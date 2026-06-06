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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
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
                .flatMapMany(user -> scheduleRepository.findBySchoolId(user.getSchoolId()))
                .map(this::toScheduleResponse)
                .collectList();
    }

    // ========================================================================
    // SEND BULK NOTIFICATIONS
    // ========================================================================

    @Override
    public Mono<SendBulkNotificationResponse> sendBulkNotifications(SendBulkNotificationRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = user.getSchoolId();

                    return templateRepository
                            .findBySchoolIdAndTemplateCode(schoolId, request.templateCode())
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "TEMPLATE_NOT_FOUND",
                                    "Template not found: " + request.templateCode())))
                            .flatMap(template ->
                                    Flux.fromIterable(request.studentFeeIds())
                                            .flatMap(feeId ->
                                                    studentFeeRepository.findById(feeId)
                                                            .filter(fee -> fee.getSchoolId().equals(schoolId)))
                                            .flatMap(fee -> sendFeeReminder(fee, template, request.channel()))
                                            .collectList()
                                            .map(results -> {
                                                long successCount = results.stream()
                                                        .filter(ChannelResult::success).count();
                                                BigDecimal cost = channelSelector
                                                        .select(request.channel())
                                                        .getCostPerMessage()
                                                        .multiply(BigDecimal.valueOf(successCount));

                                                return new SendBulkNotificationResponse(
                                                        UUID.randomUUID(),
                                                        results.size(),
                                                        cost,
                                                        "QUEUED",
                                                        successCount + " messages queued for delivery"
                                                );
                                            }));
                });
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

    private Mono<ChannelResult> sendFeeReminder(
            StudentFee fee, NotificationTemplate template, String channel) {

        return studentRepository.findById(fee.getStudentId())
                .flatMap(student ->
                        findPrimaryGuardian(student.getId())
                                .flatMap(guardian -> {
                                    String message = renderTemplate(template.getBodyTemplate(),
                                            buildTemplateVars(fee, student, guardian));

                                    return sendToChannels(
                                            channel,
                                            guardian.getPhone(),
                                            message,
                                            fee.getId(),
                                            fee.getSchoolId());
                                }));
    }

    private Mono<ChannelResult> sendToChannels(
            String channel, String phone, String message, UUID feeId, UUID schoolId) {

        if ("BOTH".equalsIgnoreCase(channel)) {
            return Flux.fromIterable(channelSelector.getAvailableChannels())
                    .flatMap(ch -> channelSelector.select(ch).send(phone, message, feeId.toString())
                            .flatMap(result -> logAndSaveNotification(
                                    result, phone, message, ch, feeId, schoolId)
                                    .thenReturn(result)))
                    .last();
        }

        NotificationChannel selectedChannel = channelSelector.select(channel);
        return selectedChannel.send(phone, message, feeId.toString())
                .flatMap(result -> logAndSaveNotification(
                        result, phone, message, channel, feeId, schoolId)
                        .thenReturn(result));
    }

    private Mono<Void> logAndSaveNotification(
            ChannelResult result, String phone, String message,
            String channel, UUID feeId, UUID schoolId) {

        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .recipientPhone(phone)
                .channel(channel)
                .body(message)
                .renderedBody(message)
                .status(result.success() ? "SENT" : "FAILED")
                .providerMessageId(result.messageId())
                .errorMessage(result.errorMessage())
                .contextType("FEE_REMINDER")
                .contextId(feeId)
                .createdAt(Instant.now())
                .sentAt(result.success() ? Instant.now() : null)
                .build();

        return notificationRepository.save(notification).then();
    }

    private Mono<StudentGuardian> findPrimaryGuardian(UUID studentId) {
        return guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId)
                .next()
                .flatMap(link -> guardianRepository.findById(link.getGuardianId()));
    }

    private Map<String, String> buildTemplateVars(
            StudentFee fee, Student student, StudentGuardian guardian) {
        Map<String, String> vars = new HashMap<>();
        vars.put("parent_name", guardian.getFirstName());
        vars.put("amount", fee.getTotalAmount().toString());
        vars.put("student_name", student.getFirstName() + " " + student.getLastName());
        vars.put("due_date", fee.getDueDate().toString());
        vars.put("days", String.valueOf(
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
}
