package com.fee.app.schoolfeeapp.notification.service.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.domain.StudentFee;
import com.fee.app.schoolfeeapp.fee.repository.StudentFeeRepository;
import com.fee.app.schoolfeeapp.notification.channel.ChannelResult;
import com.fee.app.schoolfeeapp.notification.channel.NotificationChannel;
import com.fee.app.schoolfeeapp.notification.channel.NotificationChannelSelector;
import com.fee.app.schoolfeeapp.notification.domain.Notification;
import com.fee.app.schoolfeeapp.notification.domain.NotificationTemplate;
import com.fee.app.schoolfeeapp.notification.domain.ReminderSchedule;
import com.fee.app.schoolfeeapp.notification.dto.request.NotificationTemplateResponse;
import com.fee.app.schoolfeeapp.notification.dto.request.SendBulkNotificationRequest;
import com.fee.app.schoolfeeapp.notification.dto.request.UpdateTemplateRequest;
import com.fee.app.schoolfeeapp.notification.dto.response.NotificationBalanceResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.ReminderScheduleResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.SendBulkNotificationResponse;
import com.fee.app.schoolfeeapp.notification.dto.response.UpdateTemplateResponse;
import com.fee.app.schoolfeeapp.notification.repository.NotificationRepository;
import com.fee.app.schoolfeeapp.notification.repository.NotificationTemplateRepository;
import com.fee.app.schoolfeeapp.notification.repository.ReminderScheduleRepository;
import com.fee.app.schoolfeeapp.notification.service.NotificationService;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Slf4j
class NotificationServiceImpl implements NotificationService {

    private static final Set<String> SUPPORTED_TEMPLATE_CHANNELS =
            Set.of("SMS", "WHATSAPP", "EMAIL");
    private static final Set<String> SUPPORTED_BULK_CHANNELS =
            Set.of("SMS", "WHATSAPP", "BOTH");
    private static final List<String> DIRECT_BULK_CHANNELS = List.of("SMS", "WHATSAPP");

    private final NotificationTemplateRepository templateRepository;
    private final NotificationRepository notificationRepository;
    private final ReminderScheduleRepository scheduleRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final StudentRepository studentRepository;
    private final StudentGuardianRepository guardianRepository;
    private final StudentGuardianLinkRepository guardianLinkRepository;
    private final NotificationChannelSelector channelSelector;
    private final JwtUtils jwtUtils;

    // ========================================================================
    // TEMPLATES
    // ========================================================================

    @Override
    public Mono<List<NotificationTemplateResponse>> getTemplates(String channel) {
        return Flux.defer(() -> {
                    String normalizedChannel = normalizeTemplateChannel(channel);
                    return jwtUtils.getCurrentUser()
                            .flatMapMany(user -> {
                                UUID schoolId = requireSchoolId(user);
                                if (normalizedChannel != null) {
                                    return templateRepository.findBySchoolIdAndChannel(
                                            schoolId, normalizedChannel);
                                }
                                return templateRepository.findBySchoolId(schoolId);
                            });
                })
                .map(this::toTemplateResponse)
                .collectList();
    }

    @Override
    public Mono<UpdateTemplateResponse> updateTemplate(UUID templateId, UpdateTemplateRequest request) {
        return Mono.fromCallable(() -> validateAndNormalizeUpdate(templateId, request))
                .flatMap(update -> jwtUtils.getCurrentUser()
                .flatMap(user -> templateRepository.findByIdAndSchoolId(update.templateId(), requireSchoolId(user))
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "TEMPLATE_NOT_FOUND", "Template not found")))
                        .flatMap(template -> {
                            if (update.body() != null) {
                                template.setBodyTemplate(update.body());
                            }
                            if (update.name() != null) {
                                template.setName(update.name());
                            }
                            if (update.isActive() != null) {
                                template.setIsActive(update.isActive());
                            }
                            template.setUpdatedAt(Instant.now());
                            return templateRepository.save(template);
                        })
                        .map(saved -> new UpdateTemplateResponse(saved.getId(), saved.getUpdatedAt())))
                .onErrorMap(OptimisticLockingFailureException.class, error ->
                        new SchoolFeeException(
                                "TEMPLATE_UPDATE_CONFLICT",
                                "Template was updated by another user. Please reload and try again")));
    }

    // ========================================================================
    // REMINDER SCHEDULES
    // ========================================================================

    @Override
    public Mono<List<ReminderScheduleResponse>> getReminderSchedules() {
        return jwtUtils.getCurrentUser()
                .flatMapMany(user -> {
                    UUID schoolId = requireSchoolId(user);
                    return scheduleRepository.findBySchoolId(schoolId);
                })
                .map(this::toScheduleResponse)
                .collectList();
    }

    // ========================================================================
    // SEND BULK NOTIFICATIONS
    // ========================================================================

    @Override
    public Mono<SendBulkNotificationResponse> sendBulkNotifications(SendBulkNotificationRequest request) {
        return Mono.fromCallable(() -> validateAndNormalizeBulkRequest(request))
                .flatMap(bulkRequest -> jwtUtils.getCurrentUser()
                        .flatMap(user -> sendBulkNotificationsForUser(bulkRequest, user)));
    }

    // ========================================================================
    // BALANCE
    // ========================================================================

    @Override
    public Mono<NotificationBalanceResponse> getBalance() {
        NotificationChannel smsChannel = channelSelector.select("SMS");

        return smsChannel.getBalance()
                .map(balance -> new NotificationBalanceResponse(
                        smsChannel.getProviderName(),
                        balance,
                        "NGN",
                        smsChannel.getCostPerMessage(),
                        LocalDate.now(),
                        balance > 0 ? balance / 10 : 0));
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private Mono<SendBulkNotificationResponse> sendBulkNotificationsForUser(
            BulkNotification bulkRequest, SchoolFeeUser user) {
        UUID schoolId = requireSchoolId(user);
        UUID batchId = UUID.randomUUID();
        int concurrency = 10;

        return templateRepository
                .findActiveForBulkSend(
                        schoolId,
                        bulkRequest.templateCode(),
                        bulkRequest.channel())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "TEMPLATE_NOT_FOUND",
                        "Active template not found: " + bulkRequest.templateCode())))
                .flatMap(template -> Mono.fromCallable(() -> resolveBulkChannels(bulkRequest.channel()))
                        .flatMapMany(channels -> Flux.fromIterable(bulkRequest.studentFeeIds())
                                .flatMap(feeId -> prepareFeeReminder(
                                        feeId,
                                        schoolId,
                                        template,
                                        bulkRequest.channel())
                                        .flatMapMany(reminder -> sendPreparedReminder(
                                                reminder,
                                                channels,
                                                batchId))
                                        .onErrorResume(e -> {
                                            String channelStr = bulkRequest.channel() != null ? bulkRequest.channel() : "UNKNOWN";
                                            return Flux.just(ChannelResult.builder()
                                                    .channel(channelStr)
                                                    .success(false)
                                                    .errorMessage(e.getMessage())
                                                    .build());
                                        }), concurrency))
                        .collectList()
                        .map(results -> toBulkResponse(batchId, results)));
    }

    private SendBulkNotificationResponse toBulkResponse(UUID batchId, List<ChannelResult> results) {
        long successCount = results.stream()
                .filter(ChannelResult::success)
                .count();
        BigDecimal cost = calculateSuccessfulDeliveryCost(results);
        String status = bulkStatus(results.size(), successCount);

        return new SendBulkNotificationResponse(
                batchId,
                results.size(),
                cost,
                status,
                successCount + " of " + results.size() + " messages queued for delivery");
    }

    private Mono<PreparedFeeReminder> prepareFeeReminder(
            UUID feeId, UUID schoolId, NotificationTemplate template, String requestedChannel) {

        return studentFeeRepository.findByIdAndSchoolId(feeId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "STUDENT_FEE_NOT_FOUND",
                        "Student fee not found or does not belong to your school",
                        "studentFeeIds")))
                .flatMap(fee -> studentRepository
                        .findByIdAndSchoolIdAndDeletedAtIsNull(fee.getStudentId(), schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "STUDENT_NOT_FOUND",
                                "Student linked to fee was not found",
                                "studentFeeIds")))
                        .flatMap(student -> findPrimaryGuardian(student.getId(), schoolId)
                                .map(guardian -> {
                                    String phone = trimToNull(guardian.getPhone());
                                    if (phone == null) {
                                        throw new SchoolFeeException(
                                                "GUARDIAN_CONTACT_MISSING",
                                                "Primary guardian has no phone number",
                                                "studentFeeIds");
                                    }
                                    String message = renderTemplate(
                                            template.getBodyTemplate(),
                                            buildTemplateVars(fee, student, guardian));
                                    return new PreparedFeeReminder(
                                            fee,
                                            student,
                                            guardian,
                                            template,
                                            requestedChannel,
                                            phone,
                                            message);
                                })));
    }

    private Flux<ChannelResult> sendPreparedReminder(
            PreparedFeeReminder reminder, List<NotificationChannel> channels, UUID batchId) {
        return Flux.fromIterable(channels)
                .flatMap(channel -> sendToChannel(reminder, channel, batchId));
    }

    private Mono<ChannelResult> sendToChannel(
            PreparedFeeReminder reminder, NotificationChannel channel, UUID batchId) {

        String channelName = channel.getChannel();
        Notification notification = buildQueuedNotification(reminder, channelName, batchId);

        return notificationRepository.insertNotification(notification)
                .flatMap(saved -> Mono.defer(() -> channel.send(
                                reminder.phone(),
                                reminder.message(),
                                reminder.fee().getId().toString()))
                        .onErrorResume(error -> Mono.just(ChannelResult.builder()
                                .channel(channelName)
                                .success(false)
                                .errorMessage(error.getMessage())
                                .build()))
                        .flatMap(result -> notificationRepository.updateDeliveryResult(
                                        saved.getId(),
                                        result.success() ? "SENT" : "FAILED",
                                        result.messageId(),
                                        result.success() ? channel.getCostPerMessage() : BigDecimal.ZERO,
                                        result.errorMessage(),
                                        result.success() ? Instant.now() : null)
                                .thenReturn(result)))
                .onErrorResume(DuplicateKeyException.class, error ->
                        Mono.just(ChannelResult.builder()
                                .channel(channelName)
                                .success(false)
                                .errorMessage("Notification already queued for this fee and channel today")
                                .build()));
    }

    private Notification buildQueuedNotification(
            PreparedFeeReminder reminder, String channel, UUID batchId) {
        Instant now = Instant.now();
        return Notification.builder()
                .id(UUID.randomUUID())
                .schoolId(reminder.fee().getSchoolId())
                .recipientId(reminder.guardian().getId())
                .recipientPhone(reminder.phone())
                .channel(channel)
                .templateCode(reminder.template().getTemplateCode())
                .body(reminder.template().getBodyTemplate())
                .renderedBody(reminder.message())
                .status("QUEUED")
                .providerCost(BigDecimal.ZERO)
                .retryCount(0)
                .maxRetries(3)
                .correlationId(batchId)
                .contextType("FEE_REMINDER")
                .contextId(reminder.fee().getId())
                .idempotencyKey(buildDailyIdempotencyKey(reminder, channel))
                .createdAt(now)
                .build();
    }

    private Mono<StudentGuardian> findPrimaryGuardian(UUID studentId, UUID schoolId) {
        return guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId)
                .next()
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "GUARDIAN_NOT_FOUND",
                        "Primary guardian not found for student",
                        "studentFeeIds")))
                .flatMap(link -> {
                    if (!schoolId.equals(link.getSchoolId())) {
                        return Mono.error(new SchoolFeeException(
                                "GUARDIAN_NOT_FOUND",
                                "Primary guardian not found for student",
                                "studentFeeIds"));
                    }
                    if (Boolean.FALSE.equals(link.getCanReceiveSms())) {
                        return Mono.error(new SchoolFeeException(
                                "GUARDIAN_CONTACT_NOT_ALLOWED",
                                "Primary guardian cannot receive fee reminders",
                                "studentFeeIds"));
                    }
                    return guardianRepository.findByIdAndDeletedAtIsNull(link.getGuardianId());
                })
                .filter(guardian -> schoolId.equals(guardian.getSchoolId()))
                .filter(guardian -> !Boolean.FALSE.equals(guardian.getIsActive()))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "GUARDIAN_NOT_FOUND",
                        "Primary guardian not found for student",
                        "studentFeeIds")));
    }

    private Map<String, String> buildTemplateVars(
            StudentFee fee, Student student, StudentGuardian guardian) {
        Map<String, String> vars = new HashMap<>();
        vars.put("parent_name", guardian.getFirstName());
        vars.put("amount", Objects.toString(fee.getTotalAmount(), ""));
        vars.put("student_name", student.getFirstName() + " " + student.getLastName());
        vars.put("due_date", Objects.toString(fee.getDueDate(), ""));
        vars.put("days", fee.getDueDate() == null ? "" : String.valueOf(
                java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), fee.getDueDate())));
        vars.put("admission_number", student.getAdmissionNumber());
        vars.put("payment_link", "https://schoolfee.app/pay/" + fee.getId());
        return vars;
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue() : "");
        }
        return rendered;
    }

    private NotificationTemplateResponse toTemplateResponse(NotificationTemplate template) {
        List<String> variables = parseVariables(template.getVariables());
        return new NotificationTemplateResponse(
                template.getId(),
                template.getTemplateCode(),
                template.getName(),
                template.getChannel(),
                template.getBodyTemplate(),
                variables,
                Boolean.TRUE.equals(template.getIsDefault()),
                !Boolean.FALSE.equals(template.getIsActive()),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }

    private ReminderScheduleResponse toScheduleResponse(ReminderSchedule schedule) {
        return new ReminderScheduleResponse(
                schedule.getId(),
                schedule.getName(),
                schedule.getTriggerType(),
                schedule.getDaysOffset(),
                schedule.getSendTime(),
                schedule.getTemplateCode(),
                Boolean.TRUE.equals(schedule.getIsActive()));
    }

    private String normalizeTemplateChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        String normalized = channel.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TEMPLATE_CHANNELS.contains(normalized)) {
            throw new SchoolFeeException(
                    "UNSUPPORTED_CHANNEL",
                    "Unsupported notification template channel: " + channel,
                    "channel");
        }
        return normalized;
    }

    private BulkNotification validateAndNormalizeBulkRequest(SendBulkNotificationRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_BULK_NOTIFICATION_REQUEST",
                    "Bulk notification request is required");
        }
        if (request.studentFeeIds() == null || request.studentFeeIds().isEmpty()) {
            throw new SchoolFeeException(
                    "INVALID_BULK_NOTIFICATION_REQUEST",
                    "At least one student fee ID is required",
                    "studentFeeIds");
        }
        if (request.studentFeeIds().stream().anyMatch(Objects::isNull)) {
            throw new SchoolFeeException(
                    "INVALID_BULK_NOTIFICATION_REQUEST",
                    "Student fee IDs cannot contain null values",
                    "studentFeeIds");
        }
        Set<UUID> uniqueFeeIds = new LinkedHashSet<>(request.studentFeeIds());
        if (uniqueFeeIds.size() != request.studentFeeIds().size()) {
            throw new SchoolFeeException(
                    "DUPLICATE_NOTIFICATION_TARGET",
                    "Student fee IDs must be unique within a bulk notification request",
                    "studentFeeIds");
        }

        String templateCode = trimToNull(request.templateCode());
        if (templateCode == null) {
            throw new SchoolFeeException(
                    "INVALID_BULK_NOTIFICATION_REQUEST",
                    "Template code is required",
                    "templateCode");
        }

        String channel = trimToNull(request.channel());
        if (channel == null) {
            throw new SchoolFeeException(
                    "INVALID_BULK_NOTIFICATION_REQUEST",
                    "Channel is required",
                    "channel");
        }
        channel = channel.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_BULK_CHANNELS.contains(channel)) {
            throw new SchoolFeeException(
                    "UNSUPPORTED_CHANNEL",
                    "Unsupported bulk notification channel: " + request.channel(),
                    "channel");
        }

        return new BulkNotification(List.copyOf(uniqueFeeIds), templateCode, channel);
    }

    private List<NotificationChannel> resolveBulkChannels(String channel) {
        if (!"BOTH".equals(channel)) {
            return List.of(channelSelector.select(channel));
        }

        List<String> availableChannels = channelSelector.getAvailableChannels();
        if (!availableChannels.containsAll(DIRECT_BULK_CHANNELS)) {
            throw new SchoolFeeException(
                    "UNSUPPORTED_CHANNEL",
                    "SMS and WHATSAPP channels must both be configured for bulk channel BOTH",
                    "channel");
        }
        return DIRECT_BULK_CHANNELS.stream()
                .map(channelSelector::select)
                .toList();
    }

    private BigDecimal calculateSuccessfulDeliveryCost(List<ChannelResult> results) {
        return results.stream()
                .filter(ChannelResult::success)
                .map(result -> channelSelector.select(result.channel()).getCostPerMessage())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String bulkStatus(int attempts, long successCount) {
        if (attempts == 0 || successCount == 0) {
            return "FAILED";
        }
        if (successCount == attempts) {
            return "QUEUED";
        }
        return "PARTIAL";
    }

    private String buildDailyIdempotencyKey(PreparedFeeReminder reminder, String channel) {
        String source = String.join(":",
                "FEE_REMINDER",
                reminder.fee().getSchoolId().toString(),
                reminder.fee().getId().toString(),
                reminder.template().getTemplateCode(),
                channel,
                LocalDate.now().toString());
        UUID digest = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
        return "FEE_REMINDER:" + digest;
    }

    private TemplateUpdate validateAndNormalizeUpdate(UUID templateId, UpdateTemplateRequest request) {
        if (templateId == null) {
            throw new SchoolFeeException(
                    "INVALID_TEMPLATE_REQUEST",
                    "Template ID is required",
                    "templateId");
        }
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_TEMPLATE_REQUEST",
                    "Template update request is required");
        }

        String body = trimToNull(request.body());
        String name = trimToNull(request.name());
        if (request.body() != null && body == null) {
            throw new SchoolFeeException(
                    "INVALID_TEMPLATE_REQUEST",
                    "Template body cannot be blank",
                    "body");
        }
        if (request.name() != null && name == null) {
            throw new SchoolFeeException(
                    "INVALID_TEMPLATE_REQUEST",
                    "Template name cannot be blank",
                    "name");
        }
        if (body == null && name == null && request.isActive() == null) {
            throw new SchoolFeeException(
                    "INVALID_TEMPLATE_REQUEST",
                    "At least one template field must be updated");
        }
        return new TemplateUpdate(templateId, body, name, request.isActive());
    }

    private UUID requireSchoolId(SchoolFeeUser user) {
        if (user == null || user.getSchoolId() == null) {
            throw new SchoolFeeException(
                    "SCHOOL_CONTEXT_REQUIRED",
                    "A school context is required to manage notification templates");
        }
        return user.getSchoolId();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<String> parseVariables(JsonNode variablesJson) {
        if (variablesJson == null || variablesJson.isNull()) {
            return List.of();
        }
        if (variablesJson.isArray()) {
            return StreamSupport.stream(variablesJson.spliterator(), false)
                    .map(JsonNode::asText)
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
        }
        if (!variablesJson.isTextual()) {
            return List.of();
        }
        String variables = variablesJson.asText();
        if (variables == null || variables.isBlank()) return List.of();
        try {
            return Arrays.stream(variables.replaceAll("[\\[\\]\"]", "").split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private record TemplateUpdate(
            UUID templateId,
            String body,
            String name,
            Boolean isActive) {
    }

    private record BulkNotification(
            List<UUID> studentFeeIds,
            String templateCode,
            String channel) {
    }

    private record PreparedFeeReminder(
            StudentFee fee,
            Student student,
            StudentGuardian guardian,
            NotificationTemplate template,
            String requestedChannel,
            String phone,
            String message) {
    }
}
