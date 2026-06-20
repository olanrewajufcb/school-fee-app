package com.fee.app.schoolfeeapp.school.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.KeycloakUserResult;
import com.fee.app.schoolfeeapp.auth.service.IdentityProviderService;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.common.domain.OutboxEvent;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.repository.OutboxEventRepository;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.dto.request.*;
import com.fee.app.schoolfeeapp.school.dto.response.*;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import com.fee.app.schoolfeeapp.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.fee.repository.FeeReportingRepository;
import com.fee.app.schoolfeeapp.fee.repository.FeeReportingRepository.DashboardSummaryStats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
class SchoolServiceImpl implements SchoolService {

    /**
     * Nigerian Basic Education System Configuration
     * 
     * Standard Academic Calendar:
     * - First Term: September - December (resumes early September)
     * - Second Term: January - March/April
     * - Third Term: April/May - July
     * - Long Vacation: July/August - September
     * 
     * Most Nigerian schools resume between September 1st and September 15th.
     * Default set to second Monday of September (Sept 8) as a realistic average.
     */
    private static final String DEFAULT_COUNTRY = "Nigeria";
    private static final String DEFAULT_ACADEMIC_YEAR_START = "09-08"; // Second week of September
    private static final int DEFAULT_TERMS_PER_YEAR = 3;
    private static final List<String> DEFAULT_TERM_NAMES = List.of("First Term", "Second Term", "Third Term");
    private static final Set<String> DEFAULT_SCHOOL_ADMIN_ROLES = Set.of("SCHOOL_ADMIN", "ACCOUNTANT");
    private static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    private static final String SESSION_STATUS_COMPLETED = "COMPLETED";
    private static final String TERM_STATUS_ACTIVE = "ACTIVE";
    private static final String TERM_STATUS_COMPLETED = "COMPLETED";
    
    /**
     * Valid academic year start range for Nigerian schools (MM-dd format).
     * Schools typically resume between late August and late September.
     */
    private static final String EARLIEST_ACADEMIC_START = "08-25";
    private static final String LATEST_ACADEMIC_START = "09-30";

    private final SchoolRepository schoolRepository;
    private final AcademicSessionRepository sessionRepository;
    private final TermRepository termRepository;
    private final IdentityProviderService keycloakAdminService;
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;
    private final OutboxEventRepository outboxEventRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final FeeReportingRepository feeReportingRepository;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)  // Only for dev
            .build();

    @Override
    public Mono<CreateSchoolResponse> createSchool(CreateSchoolRequest request) {
        return validateSchoolCodeAvailable(request.code())
                .then(Mono.defer(() -> doCreateSchool(request)));
    }

    private Mono<CreateSchoolResponse> doCreateSchool(CreateSchoolRequest request) {
        School school = buildSchool(request);

        Mono<SchoolCreationResult> creationWork = persistSchoolAggregate(school, request)
                .flatMap(result -> createSchoolAdminOrContinue(request, result))
                .flatMap(result -> createSchoolCreatedOutboxEvent(result).thenReturn(result));

        return transactionalOperator.transactional(creationWork)
                .map(result -> buildCreateResponse(result, request));
    }

    private Mono<Void> validateSchoolCodeAvailable(String schoolCode) {
        return schoolRepository.findByCode(schoolCode)
                .flatMap(existingSchool -> Mono.<Void>error(duplicateSchoolCodeException(schoolCode)))
                .then();
    }

    private SchoolFeeException duplicateSchoolCodeException(String schoolCode) {
        return new SchoolFeeException(
                "DUPLICATE_RESOURCE",
                "School code '" + schoolCode + "' is already in use",
                "code");
    }

    private SchoolFeeException duplicateSchoolCodeException(String schoolCode, Throwable cause) {
        return new SchoolFeeException(
                "DUPLICATE_RESOURCE",
                "School code '" + schoolCode + "' is already in use",
                "code",
                cause);
    }

    private Mono<SchoolCreationResult> persistSchoolAggregate(
            School school,
            CreateSchoolRequest request) {
        return schoolRepository.save(school)
                .onErrorMap(DuplicateKeyException.class,
                        error -> duplicateSchoolCodeException(school.getCode(), error))
                .flatMap(savedSchool -> {
                    AcademicSession session = createDefaultSession(savedSchool, request);
                    return sessionRepository.save(session)
                            .flatMap(savedSession -> {
                                List<Term> terms = createDefaultTerms(savedSession, request);
                                return termRepository.saveAll(terms)
                                        .collectList()
                                        .map(savedTerms -> new SchoolCreationResult(
                                                savedSchool,
                                                savedSession,
                                                savedTerms));
                            });
                });
    }

    private Mono<SchoolCreationResult> createSchoolAdminOrContinue(
            CreateSchoolRequest request,
            SchoolCreationResult result) {
        return createSchoolAdminInKeycloak(request.adminUser(), result.school())
                .map(keycloakResult -> result.withKeycloakResult(keycloakResult, request.adminUser().email()))
                .onErrorResume(error -> {
                    log.error("Failed to create Keycloak admin. " +
                                    "School created but admin needs manual setup. " +
                                    "schoolId={}, email={}",
                            result.school().getId(),
                            request.adminUser().email(), error);
                    return Mono.just(result);
                });
    }

    @Override
    public Mono<SchoolResponse> getCurrentSchool() {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> schoolRepository.findByIdAndIsActiveTrue(user.getSchoolId())
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "SCHOOL_NOT_FOUND", "School not found")))
                        .flatMap(this::toSchoolResponse));
    }

    @Override
    public Mono<SchoolResponse> getSchoolById(UUID schoolId) {
        return schoolRepository.findByIdAndIsActiveTrue(schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SCHOOL_NOT_FOUND", "School not found: " + schoolId)))
                .flatMap(this::toSchoolResponse);
    }

    @Override
    public Mono<SchoolResponse> updateSchool(UpdateSchoolRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> schoolRepository.findByIdAndIsActiveTrue(user.getSchoolId())
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "SCHOOL_NOT_FOUND", "School not found")))
                        .flatMap(school -> {
                            applyUpdates(school, request);
                            school.setUpdatedAt(Instant.now());
                            return schoolRepository.save(school)
                                    .onErrorMap(OptimisticLockingFailureException.class,
                                            this::staleSchoolUpdateException);
                        })
                        .flatMap(this::toSchoolResponse));
    }

    @Override
    public Mono<Void> deactivateSchool(UUID schoolId) {
        return schoolRepository.findById(schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SCHOOL_NOT_FOUND", "School not found: " + schoolId)))
                .flatMap(school -> {
                    school.setIsActive(false);
                    school.setUpdatedAt(Instant.now());
                    return schoolRepository.save(school)
                            .onErrorMap(OptimisticLockingFailureException.class,
                                    this::staleSchoolUpdateException);
                })
                .then();
    }

    private SchoolFeeException staleSchoolUpdateException(Throwable cause) {
        return new SchoolFeeException(
                "STALE_RESOURCE",
                "School was modified by another request. Please reload and try again.",
                "version",
                cause);
    }

    // ========================================================================
    // OUTBOX EVENT (Same pattern as UserManagementService)
    // ========================================================================

    /**
     * Create outbox event for school creation.
     * This will be picked up by OutboxEventProcessor for:
     * - Sending welcome email to admin
     * - Setting up default notification templates
     * - Any other async setup tasks
     *
     * Follows the SAME pattern as:
     * - createOutboxEventForParentInvitation()
     * - createOutboxEventForStaffCreation()
     */
    private Mono<Void> createSchoolCreatedOutboxEvent(SchoolCreationResult result) {
        log.info("Creating outbox event for school creation: schoolId={}", result.school().getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("schoolId", result.school().getId().toString());
        payload.put("schoolName", result.school().getName());
        payload.put("schoolCode", result.school().getCode());
        payload.put("adminKeycloakId", result.adminKeycloakId() != null
                ? result.adminKeycloakId().toString() : null);
        payload.put("adminTemporaryPassword", result.adminTemporaryPassword());
        payload.put("adminEmail", result.adminEmail());
        payload.put("sessionId", result.session().getId().toString());
        payload.put("termIds", result.terms().stream()
                .map(t -> t.getId().toString())
                .toList());

        OutboxEvent event = OutboxEvent.builder()
                .eventType("SCHOOL_CREATED")
                .aggregateId(result.school().getId())
                .aggregateType("SCHOOL")
                .payload(objectMapper.valueToTree(payload))
                .status("PENDING")
                .retryCount(0)
                .maxRetries(3)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        return outboxEventRepository.save(event)
                .doOnSuccess(e -> log.info("School created outbox event saved: eventId={}", e.getId()))
                .then();
    }

    // ========================================================================
    // PRIVATE HELPERS (same as before)
    // ========================================================================

    private School buildSchool(CreateSchoolRequest request) {
        return School.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .code(request.code())
                .email(request.email())
                .phone(request.phone())
                .address(request.address())
                .city(request.city())
                .state(request.state())
                .country(Objects.requireNonNullElse(request.country(), DEFAULT_COUNTRY))
                .logoUrl(request.logoUrl())
                .paymentConfig(buildPaymentConfig(request))
                .smsConfig(buildSmsConfig(request))
                .termConfig(buildTermConfig(request))
                .isActive(true)
                .build();
    }

    /**
     * Build payment config as JsonNode.
     */
    private JsonNode buildPaymentConfig(CreateSchoolRequest request) {
        return request.paymentConfig() != null
                ? objectMapper.valueToTree(request.paymentConfig())
                : objectMapper.createObjectNode();
    }

    private JsonNode buildSmsConfig(CreateSchoolRequest request) {
        return request.smsConfig() != null
                ? objectMapper.valueToTree(request.smsConfig())
                : objectMapper.createObjectNode();
    }

    private JsonNode buildTermConfig(CreateSchoolRequest request) {
        return request.termConfig() != null
                ? objectMapper.valueToTree(request.termConfig())
                : objectMapper.createObjectNode();
    }


    private AcademicSession createDefaultSession(School school, CreateSchoolRequest request) {
        String yearStart = resolveAcademicYearStart(request);

        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        LocalDate sessionStart = parseAcademicYearStart(yearStart, currentYear);
        if (today.isBefore(sessionStart)) {
            sessionStart = parseAcademicYearStart(yearStart, currentYear - 1);
        }
        LocalDate sessionEnd = sessionStart.plusYears(1).minusDays(1);

        return AcademicSession.builder()
                .id(UUID.randomUUID())
                .schoolId(school.getId())
                .name(sessionStart.getYear() + "/" + (sessionStart.getYear() + 1) + " Academic Year")
                .startDate(sessionStart)
                .endDate(sessionEnd)
                .isCurrent(true)
                .status(SESSION_STATUS_ACTIVE)
                .build();
    }

    private List<Term> createDefaultTerms(AcademicSession session, CreateSchoolRequest request) {
        int termsPerYear = resolveTermsPerYear(request);
        List<String> termNames = resolveTermNames(request, termsPerYear);
        List<Term> terms = new ArrayList<>();
        long sessionDays = ChronoUnit.DAYS.between(session.getStartDate(), session.getEndDate().plusDays(1));

        for (int i = 0; i < termsPerYear; i++) {
            LocalDate termStart = session.getStartDate().plusDays((i * sessionDays) / termsPerYear);
            LocalDate termEnd = (i == termsPerYear - 1)
                    ? session.getEndDate()
                    : session.getStartDate().plusDays(((i + 1) * sessionDays) / termsPerYear).minusDays(1);

            Term term = Term.builder()
                    .id(UUID.randomUUID())
                    .sessionId(session.getId())
                    .name(termNames.get(i))
                    .termNumber((short) (i + 1))
                    .startDate(termStart)
                    .endDate(termEnd)
                    .isCurrent(i == 0)
                    .status(TERM_STATUS_ACTIVE)
                    .build();
            terms.add(term);
        }
        return terms;
    }

    private String resolveAcademicYearStart(CreateSchoolRequest request) {
        if (request.termConfig() == null || isBlank(request.termConfig().academicYearStart())) {
            return DEFAULT_ACADEMIC_YEAR_START;
        }
        return request.termConfig().academicYearStart();
    }

    private LocalDate parseAcademicYearStart(String academicYearStart, int year) {
        try {
            LocalDate parsedDate = LocalDate.parse(year + "-" + academicYearStart);
            validateAcademicYearStart(academicYearStart);
            return parsedDate;
        } catch (DateTimeParseException e) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Academic year start must use MM-dd format (e.g., 09-08 for September 8th)",
                    "termConfig.academicYearStart",
                    e);
        }
    }
    
    /**
     * Validates that the academic year start date falls within acceptable ranges
     * for the Nigerian education system.
     * 
     * @param academicYearStart Date in MM-dd format
     * @throws SchoolFeeException if date is outside valid range
     */
    private void validateAcademicYearStart(String academicYearStart) {
        try {
            // Parse without year to compare month-day only
            LocalDate providedDate = LocalDate.parse("2000-" + academicYearStart);
            LocalDate earliestDate = LocalDate.parse("2000-" + EARLIEST_ACADEMIC_START);
            LocalDate latestDate = LocalDate.parse("2000-" + LATEST_ACADEMIC_START);
            
            if (providedDate.isBefore(earliestDate) || providedDate.isAfter(latestDate)) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        String.format(
                            "Academic year start (%s) is outside the valid range for Nigerian schools. " +
                            "Expected between %s and %s. Most Nigerian basic education schools resume " +
                            "between late August and late September.",
                            academicYearStart, EARLIEST_ACADEMIC_START, LATEST_ACADEMIC_START),
                        "termConfig.academicYearStart");
            }
        } catch (DateTimeParseException e) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Academic year start must use MM-dd format (e.g., 09-08)",
                    "termConfig.academicYearStart",
                    e);
        }
    }

    private int resolveTermsPerYear(CreateSchoolRequest request) {
        if (request.termConfig() == null || request.termConfig().termsPerYear() <= 0) {
            return DEFAULT_TERMS_PER_YEAR;
        }
        return request.termConfig().termsPerYear();
    }

    private List<String> resolveTermNames(CreateSchoolRequest request, int termsPerYear) {
        List<String> configuredNames = request.termConfig() != null
                ? request.termConfig().termNames()
                : null;

        List<String> names = configuredNames == null || configuredNames.isEmpty()
                ? new ArrayList<>(DEFAULT_TERM_NAMES)
                : configuredNames.stream()
                        .filter(name -> !isBlank(name))
                        .map(String::trim)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        if (names.isEmpty()) {
            names.addAll(DEFAULT_TERM_NAMES);
        }

        for (int i = names.size(); i < termsPerYear; i++) {
            names.add("Term " + (i + 1));
        }

        return names.subList(0, termsPerYear);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Mono<KeycloakUserResult> createSchoolAdminInKeycloak(
            CreateSchoolRequest.AdminUser adminUser, School school) {

        return keycloakAdminService.createStaffUser(
                new CreateStaffRequest(
                        adminUser.email(),
                        adminUser.firstName(),
                        adminUser.lastName(),
                        adminUser.phoneNumber(),
                        "SCHOOL_ADMIN",
                        DEFAULT_SCHOOL_ADMIN_ROLES
                ),
                school.getId(),
                school.getName()
        )
                .doOnSuccess(keycloakResult ->
                        log.info("School admin created in Keycloak: keycloakId={}, email={}",
                                keycloakResult.userId(), adminUser.email()))
                .doOnError(error ->
                        log.error("Failed to create school admin in Keycloak: email={}",
                                adminUser.email(), error));
    }


    private Mono<SchoolResponse> toSchoolResponse(School school) {
        return findCurrentTerm(school.getId())
                .map(currentTerm -> SchoolResponse.builder()
                        .schoolId(school.getId())
                        .name(school.getName())
                        .code(school.getCode())
                        .email(school.getEmail())
                        .phone(school.getPhone())
                        .address(school.getAddress())
                        .city(school.getCity())
                        .state(school.getState())
                        .country(school.getCountry())
                        .logoUrl(school.getLogoUrl())
                        .status(school.getIsActive() ? "ACTIVE" : "INACTIVE")
                        .currentTerm(currentTerm.orElse(null))
                        .paymentConfig(jsonNodeToMap(school.getPaymentConfig()))
                        .createdAt(school.getCreatedAt())
                        .build());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) return Collections.emptyMap();
        try {
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            log.warn("Failed to convert JsonNode to Map: {}", node);
            return Collections.emptyMap();
        }
    }


    private Mono<Optional<SchoolResponse.CurrentTerm>> findCurrentTerm(UUID schoolId) {
        return termRepository.findCurrentTermsBySchoolId(schoolId)
                .next()  // Take the first current term
                .flatMap(term -> sessionRepository.findById(term.getSessionId())
                        .map(session -> SchoolResponse.CurrentTerm.builder()
                                .termId(term.getId())
                                .name(term.getName())
                                .sessionName(session.getName())
                                .startDate(term.getStartDate().toString())
                                .endDate(term.getEndDate().toString())
                                .build())
                )
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }


    // ========================================================================
    // UPDATE HELPERS — Use JsonNode
    // ========================================================================

    private void applyUpdates(School school, UpdateSchoolRequest request) {
        if (request.email() != null) school.setEmail(request.email());
        if (request.phone() != null) school.setPhone(request.phone());
        if (request.address() != null) school.setAddress(request.address());
        if (request.city() != null) school.setCity(request.city());
        if (request.state() != null) school.setState(request.state());
        if (request.logoUrl() != null) school.setLogoUrl(request.logoUrl());

        if (request.paymentConfig() != null) {
            school.setPaymentConfig(objectMapper.valueToTree(request.paymentConfig()));
        }
        if (request.smsConfig() != null) {
            school.setSmsConfig(objectMapper.valueToTree(request.smsConfig()));
        }
    }

    private CreateSchoolResponse buildCreateResponse(
            SchoolCreationResult result, CreateSchoolRequest request) {
        return CreateSchoolResponse.builder()
                .schoolId(result.school().getId())
                .name(result.school().getName())
                .code(result.school().getCode())
                .status("ACTIVE")
                .adminUserCreated(result.adminKeycloakId() != null)
                .adminTemporaryPassword(result.adminKeycloakId() != null
                        ? "Sent via email" : "Manual setup required")
                .currentSessionId(result.session().getId())
                .currentSessionName(result.session().getName())
                .createdAt(Instant.now())
                .message(result.adminKeycloakId() != null
                        ? "School created successfully. Admin credentials sent to " + request.adminUser().email()
                        : "School created but admin account setup failed. Please contact support.")
                .build();
    }

    /**
     * Internal result holder for school creation.
     * Same pattern as UserGuardianResult in UserManagementService.
     */
    private record SchoolCreationResult(
            School school,
            AcademicSession session,
            List<Term> terms,
            UUID adminKeycloakId,
            String adminTemporaryPassword,
            String adminEmail
    ) {
        SchoolCreationResult(School school, AcademicSession session, List<Term> terms) {
            this(school, session, terms, null, null, null);
        }

        SchoolCreationResult withKeycloakResult(KeycloakUserResult result, String email) {
            return new SchoolCreationResult(school, session, terms, result.userId(), result.temporaryPassword(), email);
        }
    }



    // ========================================================================
    // ACADEMIC SESSIONS
    // ========================================================================

    @Override
    public Mono<List<AcademicSessionResponse>> getCurrentSchoolSessions() {
        return jwtUtils.getCurrentUser()
                .flatMapMany(user ->
                        sessionRepository.findBySchoolIdOrderByStartDateDesc(user.getSchoolId()))
                .concatMap(this::toSessionResponse)
                .collectList();
    }

    @Override
    public Mono<AcademicSessionResponse> createSession(CreateAcademicSessionRequest request) {
        return Mono.defer(() -> {
            validateCreateSessionRequest(request);

            return jwtUtils.getCurrentUser()
                    .flatMap(user -> schoolRepository.findByIdAndIsActiveTrue(user.getSchoolId())
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "SCHOOL_NOT_FOUND", "School not found")))
                            .flatMap(school -> transactionalOperator
                                    .transactional(persistAcademicSession(school.getId(), request)))
                            .flatMap(this::toSessionResponse));
        });
    }

    private Mono<AcademicSession> persistAcademicSession(
            UUID schoolId,
            CreateAcademicSessionRequest request) {
        Mono<Void> unsetCurrent = request.setAsCurrent()
                ? unsetCurrentSessionAndTerms(schoolId)
                : Mono.empty();

        return unsetCurrent
                .then(Mono.defer(() -> sessionRepository.save(buildAcademicSession(schoolId, request))))
                .flatMap(savedSession -> saveSessionTerms(savedSession, request)
                        .thenReturn(savedSession))
                .onErrorMap(DuplicateKeyException.class, this::currentSessionConflictException);
    }

    private AcademicSession buildAcademicSession(UUID schoolId, CreateAcademicSessionRequest request) {
        return AcademicSession.builder()
                .id(UUID.randomUUID())
                .schoolId(schoolId)
                .name(request.name().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .isCurrent(request.setAsCurrent())
                .status(SESSION_STATUS_ACTIVE)
                .build();
    }

    private Mono<Void> unsetCurrentSessionAndTerms(UUID schoolId) {
        return sessionRepository
                .findBySchoolIdAndIsCurrentTrue(schoolId)
                .flatMap(currentSession -> unsetCurrentTerms(currentSession.getId())
                        .then(Mono.defer(() -> {
                            currentSession.setIsCurrent(false);
                            currentSession.setUpdatedAt(Instant.now());
                            return sessionRepository.save(currentSession);
                        })))
                .then();
    }

    private Mono<Void> unsetCurrentTerms(UUID sessionId) {
        return termRepository.findBySessionId(sessionId)
                .filter(term -> Boolean.TRUE.equals(term.getIsCurrent()))
                .flatMap(term -> {
                    term.setIsCurrent(false);
                    return termRepository.save(term);
                })
                .then();
    }

    private Mono<Void> saveSessionTerms(
            AcademicSession savedSession,
            CreateAcademicSessionRequest request) {
        List<Term> terms = buildSessionTerms(savedSession, request);
        return termRepository.saveAll(terms).then();
    }

    private List<Term> buildSessionTerms(
            AcademicSession savedSession,
            CreateAcademicSessionRequest request) {
        List<CreateAcademicSessionRequest.TermRequest> sortedTerms = request.terms().stream()
                .sorted(Comparator.comparingInt(CreateAcademicSessionRequest.TermRequest::termNumber))
                .toList();

        return sortedTerms.stream()
                .map(termRequest -> Term.builder()
                        .id(UUID.randomUUID())
                        .sessionId(savedSession.getId())
                        .name(termRequest.name().trim())
                        .termNumber((short) termRequest.termNumber())
                        .startDate(termRequest.startDate())
                        .endDate(termRequest.endDate())
                        .isCurrent(request.setAsCurrent()
                                && termRequest.termNumber() == sortedTerms.getFirst().termNumber())
                        .status(TERM_STATUS_ACTIVE)
                        .build())
                .toList();
    }

    private void validateCreateSessionRequest(CreateAcademicSessionRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_SESSION_CONFIG",
                    "Session request is required");
        }

        if (isBlank(request.name())) {
            throw new SchoolFeeException(
                    "INVALID_SESSION_CONFIG",
                    "Session name is required",
                    "name");
        }

        validateDatePresent(request.startDate(), "Start date is required", "startDate");
        validateDatePresent(request.endDate(), "End date is required", "endDate");
        if (request.endDate().isBefore(request.startDate())) {
            throw new SchoolFeeException(
                    "INVALID_SESSION_CONFIG",
                    "Session end date must be on or after start date",
                    "endDate");
        }

        if (request.terms() == null || request.terms().isEmpty()) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "At least one term is required",
                    "terms");
        }

        validateSessionTerms(request);
    }

    private void validateDatePresent(LocalDate date, String message, String field) {
        if (date == null) {
            throw new SchoolFeeException("INVALID_SESSION_CONFIG", message, field);
        }
    }

    private void validateSessionTerms(CreateAcademicSessionRequest request) {
        Set<Integer> termNumbers = new HashSet<>();
        List<CreateAcademicSessionRequest.TermRequest> sortedTerms = new ArrayList<>();

        for (int index = 0; index < request.terms().size(); index++) {
            CreateAcademicSessionRequest.TermRequest term = request.terms().get(index);
            String fieldPrefix = "terms[" + index + "]";

            if (term == null) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        "Term details are required",
                        fieldPrefix);
            }
            if (isBlank(term.name())) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        "Term name is required",
                        fieldPrefix + ".name");
            }
            if (term.termNumber() <= 0) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        "Term number must be greater than zero",
                        fieldPrefix + ".termNumber");
            }
            if (!termNumbers.add(term.termNumber())) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        "Term numbers must be unique within a session",
                        fieldPrefix + ".termNumber");
            }

            validateTermDates(request, term, fieldPrefix);
            sortedTerms.add(term);
        }

        validateTermDateOverlap(sortedTerms);
    }

    private void validateTermDates(
            CreateAcademicSessionRequest request,
            CreateAcademicSessionRequest.TermRequest term,
            String fieldPrefix) {
        if (term.startDate() == null) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Term start date is required",
                    fieldPrefix + ".startDate");
        }
        if (term.endDate() == null) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Term end date is required",
                    fieldPrefix + ".endDate");
        }
        if (term.endDate().isBefore(term.startDate())) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Term end date must be on or after start date",
                    fieldPrefix + ".endDate");
        }
        if (term.startDate().isBefore(request.startDate()) || term.endDate().isAfter(request.endDate())) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Term dates must fall within the academic session date range",
                    fieldPrefix + ".startDate");
        }
    }

    private void validateTermDateOverlap(List<CreateAcademicSessionRequest.TermRequest> terms) {
        List<CreateAcademicSessionRequest.TermRequest> sortedTerms = terms.stream()
                .sorted(Comparator.comparing(CreateAcademicSessionRequest.TermRequest::startDate)
                        .thenComparing(CreateAcademicSessionRequest.TermRequest::endDate))
                .toList();

        for (int index = 1; index < sortedTerms.size(); index++) {
            CreateAcademicSessionRequest.TermRequest previous = sortedTerms.get(index - 1);
            CreateAcademicSessionRequest.TermRequest current = sortedTerms.get(index);
            if (!current.startDate().isAfter(previous.endDate())) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        "Terms must not overlap",
                        "terms");
            }
        }
    }

    private SchoolFeeException currentSessionConflictException(Throwable cause) {
        return currentSessionConflictException("setAsCurrent", cause);
    }

    private SchoolFeeException currentSessionConflictException(String field, Throwable cause) {
        return new SchoolFeeException(
                "CURRENT_SESSION_CONFLICT",
                "Another current session was created for this school. Please reload and try again.",
                field,
                cause);
    }

    @Override
    public Mono<AcademicSessionResponse> setCurrentSession(UUID sessionId) {
        if (sessionId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_SESSION",
                    "Session id is required",
                    "sessionId"));
        }

        return jwtUtils.getCurrentUser()
                .flatMap(user -> transactionalOperator.transactional(
                        schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                .switchIfEmpty(Mono.error(new SchoolFeeException(
                                        "SCHOOL_NOT_FOUND", "School not found")))
                                .flatMap(school -> findSessionInCurrentSchoolForUpdate(sessionId, school.getId())
                                        .flatMap(this::requireSessionOpen)
                                        .flatMap(session -> applyCurrentSession(school.getId(), session)))))
                .flatMap(this::toSessionResponse);
    }

    private Mono<AcademicSession> findSessionInCurrentSchool(UUID sessionId, UUID schoolId) {
        return sessionRepository.findById(sessionId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_FOUND",
                        "Session not found: " + sessionId)))
                .filter(session -> schoolId.equals(session.getSchoolId()))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_IN_SCHOOL",
                        "Session does not belong to your school")));
    }

    private Mono<AcademicSession> findSessionInCurrentSchoolForUpdate(UUID sessionId, UUID schoolId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_FOUND",
                        "Session not found: " + sessionId)))
                .filter(session -> schoolId.equals(session.getSchoolId()))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_IN_SCHOOL",
                        "Session does not belong to your school")));
    }

    private Mono<AcademicSession> requireSessionOpen(AcademicSession session) {
        if (isCompletedSession(session)) {
            return Mono.error(sessionAlreadyClosedException(session.getId()));
        }
        return Mono.just(session);
    }

    private boolean isCompletedSession(AcademicSession session) {
        return SESSION_STATUS_COMPLETED.equalsIgnoreCase(Objects.toString(session.getStatus(), ""));
    }

    private SchoolFeeException sessionAlreadyClosedException(UUID sessionId) {
        return new SchoolFeeException(
                "SESSION_ALREADY_CLOSED",
                "Session is already closed and cannot be modified: " + sessionId,
                "sessionId");
    }

    private Mono<AcademicSession> applyCurrentSession(UUID schoolId, AcademicSession session) {
        if (Boolean.TRUE.equals(session.getIsCurrent())) {
            return setFirstTermCurrent(session.getId())
                    .thenReturn(session);
        }

        return unsetCurrentSessionAndTerms(schoolId)
                .then(Mono.defer(() -> {
                    session.setIsCurrent(true);
                    session.setUpdatedAt(Instant.now());
                    return sessionRepository.save(session);
                }))
                .flatMap(savedSession -> setFirstTermCurrent(savedSession.getId())
                        .thenReturn(savedSession))
                .onErrorMap(DuplicateKeyException.class,
                        error -> currentSessionConflictException("sessionId", error));
    }

    private Mono<Void> setFirstTermCurrent(UUID sessionId) {
        return termRepository.findBySessionIdOrderByTermNumberAsc(sessionId)
                .collectList()
                .flatMap(terms -> {
                    if (terms.isEmpty()) {
                        return Mono.empty();
                    }

                    Term firstTerm = terms.getFirst();
                    Mono<Void> unsetOtherCurrentTerms = Flux.fromIterable(terms)
                            .filter(term -> !term.getId().equals(firstTerm.getId()))
                            .filter(term -> Boolean.TRUE.equals(term.getIsCurrent()))
                            .flatMap(term -> {
                                term.setIsCurrent(false);
                                return termRepository.save(term);
                            })
                            .then();

                    Mono<Void> setFirstCurrent = Mono.defer(() -> {
                        if (Boolean.TRUE.equals(firstTerm.getIsCurrent())) {
                            return Mono.empty();
                        }
                        firstTerm.setIsCurrent(true);
                        return termRepository.save(firstTerm).then();
                    });

                    return unsetOtherCurrentTerms.then(setFirstCurrent);
                });
    }

    // ========================================================================
    // RESPONSE BUILDERS
    // ========================================================================

    private record TermInfo(Term term, String formattedName) {}

    private Mono<Optional<TermInfo>> findCurrentTermInfo(UUID schoolId) {
        return termRepository.findCurrentTermsBySchoolId(schoolId)
                .next()
                .flatMap(term -> sessionRepository.findById(term.getSessionId())
                        .map(session -> new TermInfo(term, term.getName() + " " + session.getName())))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private double calculateCollectionRate(BigDecimal collected, BigDecimal expected) {
        if (expected == null || expected.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        return Optional.ofNullable(collected).orElse(BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(100))
                .divide(expected, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Mono<SchoolSummaryResponse> toSchoolSummary(School school) {
        UUID schoolId = school.getId();

        Mono<Long> studentCountMono = studentRepository.countBySchoolIdAndDeletedAtIsNull(schoolId)
                .defaultIfEmpty(0L);

        Mono<Long> activeUsersMono = userRepository.countBySchoolIdWithFilters(schoolId, null, true, null)
                .defaultIfEmpty(0L);

        Mono<Optional<TermInfo>> termInfoMono = findCurrentTermInfo(schoolId);

        return Mono.zip(studentCountMono, activeUsersMono, termInfoMono)
                .flatMap(tuple -> {
                    long studentCount = tuple.getT1();
                    long activeUsers = tuple.getT2();
                    Optional<TermInfo> termInfoOpt = tuple.getT3();

                    if (termInfoOpt.isPresent()) {
                        TermInfo termInfo = termInfoOpt.get();
                        return feeReportingRepository.getDashboardSummary(schoolId, termInfo.term().getId())
                                .map(stats -> {
                                    double collectionRate = calculateCollectionRate(stats.totalCollected(), stats.totalExpected());
                                    return new SchoolSummaryResponse(
                                            schoolId,
                                            school.getName(),
                                            school.getCode(),
                                            school.getCity(),
                                            school.getState(),
                                            (int) studentCount,
                                            (int) activeUsers,
                                            Boolean.TRUE.equals(school.getIsActive()) ? "ACTIVE" : "INACTIVE",
                                            termInfo.formattedName(),
                                            collectionRate,
                                            school.getCreatedAt()
                                    );
                                });
                    } else {
                        return Mono.just(new SchoolSummaryResponse(
                                schoolId,
                                school.getName(),
                                school.getCode(),
                                school.getCity(),
                                school.getState(),
                                (int) studentCount,
                                (int) activeUsers,
                                Boolean.TRUE.equals(school.getIsActive()) ? "ACTIVE" : "INACTIVE",
                                null,
                                0.0,
                                school.getCreatedAt()
                        ));
                    }
                });
    }


    private Mono<AcademicSessionResponse> toSessionResponse(AcademicSession session) {
    return termRepository
        .findBySessionIdOrderByTermNumberAsc(session.getId())
        .map(
            term ->
                new AcademicSessionResponse.TermResponse(
                        term.getId(),
                        term.getName(),
                        term.getTermNumber(),
                        term.getStartDate(),
                        term.getEndDate(),
                        term.getIsCurrent()))
        .collectList()
        .map(
            terms ->
                new AcademicSessionResponse(
                    session.getId(),
                    session.getName(),
                    session.getStartDate(),
                    session.getEndDate(),
                    session.getIsCurrent(),
                    terms));
    }
    // ========================================================================
    // LIST SCHOOLS (Super Admin)
    // ========================================================================

    @Override
    public Mono<PageResponse<SchoolSummaryResponse>> listSchools(String status, Pageable pageable) {
        return Mono.defer(() -> {
            Boolean isActive = resolveSchoolStatusFilter(status);
            int limit = pageable.getPageSize();
            long offset = pageable.getOffset();

            Mono<List<SchoolSummaryResponse>> contentMono = schoolRepository
                    .findByActiveStatus(isActive, limit, offset)
                    .flatMap(this::toSchoolSummary)
                    .collectList();
            Mono<Long> countMono = schoolRepository.countByActiveStatus(isActive);

            return contentMono
                    .zipWith(countMono)
                    .map(tuple -> {
                        List<SchoolSummaryResponse> content = tuple.getT1();
                        long totalElements = tuple.getT2();

                        return new PageResponse<>(
                                content,
                                pageable.getPageNumber(),
                                limit,
                                totalElements,
                                calculateTotalPages(totalElements, limit)
                        );
                    });
        });
    }

    private Boolean resolveSchoolStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return true;
        }

        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> true;
            case "INACTIVE" -> false;
            case "ALL" -> null;
            default -> throw new SchoolFeeException(
                    "INVALID_STATUS",
                    "School status must be one of ACTIVE, INACTIVE, or ALL",
                    "status");
        };
    }

    private int calculateTotalPages(long totalElements, int pageSize) {
        if (totalElements == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }

    // ========================================================================
// UPDATE SESSION
// ========================================================================

    @Override
    public Mono<UpdateSessionResponse> updateSession(UUID sessionId, UpdateSessionRequest request) {
        if (sessionId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_SESSION",
                    "Session id is required",
                    "sessionId"));
        }

        return Mono.defer(() -> {
            validateUpdateSessionRequestShape(request);

            return jwtUtils.getCurrentUser()
                    .flatMap(user -> transactionalOperator.transactional(
                            schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "SCHOOL_NOT_FOUND", "School not found")))
                                    .flatMap(school -> findSessionInCurrentSchool(sessionId, school.getId()))
                                    .flatMap(this::requireSessionOpen)
                                    .flatMap(session -> applySessionUpdate(session, request))));
        });
    }

    private Mono<UpdateSessionResponse> applySessionUpdate(
            AcademicSession session,
            UpdateSessionRequest request) {
        boolean sessionDateChanged = request.startDate() != null || request.endDate() != null;
        applySessionFieldUpdates(session, request);
        Instant updatedAt = Instant.now();

        return updateSessionTerms(session, request.terms(), sessionDateChanged)
                .then(Mono.defer(() -> {
                    session.setUpdatedAt(updatedAt);
                    return sessionRepository.save(session);
                }))
                .map(savedSession -> new UpdateSessionResponse(
                        savedSession.getId(),
                        savedSession.getName(),
                        updatedAt))
                .onErrorMap(OptimisticLockingFailureException.class, this::staleSessionUpdateException);
    }

    private void applySessionFieldUpdates(AcademicSession session, UpdateSessionRequest request) {
        String name = request.name() != null ? request.name().trim() : session.getName();
        LocalDate startDate = request.startDate() != null ? request.startDate() : session.getStartDate();
        LocalDate endDate = request.endDate() != null ? request.endDate() : session.getEndDate();

        validateSessionDateRange(startDate, endDate);

        session.setName(name);
        session.setStartDate(startDate);
        session.setEndDate(endDate);
    }

    private Mono<Void> updateSessionTerms(
            AcademicSession session,
            List<UpdateSessionRequest.TermUpdate> termUpdates,
            boolean validateExistingTerms) {
        if ((termUpdates == null || termUpdates.isEmpty()) && !validateExistingTerms) {
            return Mono.empty();
        }

        Map<UUID, UpdateSessionRequest.TermUpdate> updatesByTermId = termUpdates == null
                ? Map.of()
                : termUpdates.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                UpdateSessionRequest.TermUpdate::termId,
                                update -> update,
                                (first, ignored) -> first,
                                LinkedHashMap::new));

        return termRepository.findBySessionIdOrderByTermNumberAsc(session.getId())
                .collectList()
                .flatMap(existingTerms -> {
                    Map<UUID, Term> termsById = existingTerms.stream()
                            .collect(java.util.stream.Collectors.toMap(Term::getId, term -> term));

                    Optional<UUID> missingTermId = updatesByTermId.keySet().stream()
                            .filter(termId -> !termsById.containsKey(termId))
                            .findFirst();
                    if (missingTermId.isPresent()) {
                        return termNotInSessionOrMissingError(session.getId(), missingTermId.get());
                    }

                    List<TermUpdatePlan> plans = buildTermUpdatePlans(
                            existingTerms,
                            updatesByTermId,
                            session.getStartDate(),
                            session.getEndDate());
                    validateTermUpdateOverlap(plans);

                    return Flux.fromIterable(plans)
                            .filter(TermUpdatePlan::changed)
                            .flatMap(plan -> {
                                Term term = plan.term();
                                term.setName(plan.name());
                                term.setStartDate(plan.startDate());
                                term.setEndDate(plan.endDate());
                                return termRepository.save(term);
                            })
                            .then();
                });
    }

    private Mono<Void> termNotInSessionOrMissingError(UUID sessionId, UUID termId) {
        return termRepository.findById(termId)
                .flatMap(term -> Mono.<Void>error(new SchoolFeeException(
                        "TERM_NOT_IN_SESSION",
                        "Term does not belong to session: " + sessionId,
                        "terms")))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "TERM_NOT_FOUND",
                        "Term not found: " + termId,
                        "terms")));
    }

    private List<TermUpdatePlan> buildTermUpdatePlans(
            List<Term> existingTerms,
            Map<UUID, UpdateSessionRequest.TermUpdate> updatesByTermId,
            LocalDate sessionStartDate,
            LocalDate sessionEndDate) {
        List<TermUpdatePlan> plans = new ArrayList<>();

        for (Term term : existingTerms) {
            UpdateSessionRequest.TermUpdate update = updatesByTermId.get(term.getId());
            String name = update != null && update.name() != null
                    ? update.name().trim()
                    : term.getName();
            LocalDate startDate = update != null && update.startDate() != null
                    ? update.startDate()
                    : term.getStartDate();
            LocalDate endDate = update != null && update.endDate() != null
                    ? update.endDate()
                    : term.getEndDate();

            validateTermUpdateDates(term, startDate, endDate, sessionStartDate, sessionEndDate);

            boolean changed = update != null && (
                    !Objects.equals(term.getName(), name)
                            || !Objects.equals(term.getStartDate(), startDate)
                            || !Objects.equals(term.getEndDate(), endDate));
            plans.add(new TermUpdatePlan(term, name, startDate, endDate, changed));
        }

        return plans;
    }

    private void validateTermUpdateDates(
            Term term,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate sessionStartDate,
            LocalDate sessionEndDate) {
        if (startDate == null) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Term start date is required",
                    "terms[" + term.getId() + "].startDate");
        }
        if (endDate == null) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Term end date is required",
                    "terms[" + term.getId() + "].endDate");
        }
        if (endDate.isBefore(startDate)) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Term end date must be on or after start date",
                    "terms[" + term.getId() + "].endDate");
        }
        if (startDate.isBefore(sessionStartDate) || endDate.isAfter(sessionEndDate)) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Term dates must fall within the academic session date range",
                    "terms[" + term.getId() + "].startDate");
        }
    }

    private void validateTermUpdateOverlap(List<TermUpdatePlan> plans) {
        List<TermUpdatePlan> sortedPlans = plans.stream()
                .sorted(Comparator.comparing(TermUpdatePlan::startDate)
                        .thenComparing(TermUpdatePlan::endDate))
                .toList();

        for (int index = 1; index < sortedPlans.size(); index++) {
            TermUpdatePlan previous = sortedPlans.get(index - 1);
            TermUpdatePlan current = sortedPlans.get(index);
            if (!current.startDate().isAfter(previous.endDate())) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        "Terms must not overlap",
                        "terms");
            }
        }
    }

    private void validateUpdateSessionRequestShape(UpdateSessionRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_SESSION_UPDATE",
                    "Session update request is required");
        }

        if (request.name() != null && request.name().isBlank()) {
            throw new SchoolFeeException(
                    "INVALID_SESSION_CONFIG",
                    "Session name must not be blank",
                    "name");
        }

        validateTermUpdateRequestShape(request.terms());

        boolean hasSessionField = request.name() != null
                || request.startDate() != null
                || request.endDate() != null;
        boolean hasTermField = request.terms() != null
                && request.terms().stream().anyMatch(this::hasTermUpdateField);

        if (!hasSessionField && !hasTermField) {
            throw new SchoolFeeException(
                    "INVALID_SESSION_UPDATE",
                    "At least one session or term field must be provided");
        }
    }

    private void validateTermUpdateRequestShape(List<UpdateSessionRequest.TermUpdate> termUpdates) {
        if (termUpdates == null || termUpdates.isEmpty()) {
            return;
        }

        Set<UUID> termIds = new HashSet<>();
        for (int index = 0; index < termUpdates.size(); index++) {
            UpdateSessionRequest.TermUpdate update = termUpdates.get(index);
            String fieldPrefix = "terms[" + index + "]";

            if (update == null) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        "Term update details are required",
                        fieldPrefix);
            }
            if (update.termId() == null) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        "Term id is required",
                        fieldPrefix + ".termId");
            }
            if (!termIds.add(update.termId())) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        "Term updates must not contain duplicate term ids",
                        fieldPrefix + ".termId");
            }
            if (update.name() != null && update.name().isBlank()) {
                throw new SchoolFeeException(
                        "INVALID_TERM_CONFIG",
                        "Term name must not be blank",
                        fieldPrefix + ".name");
            }
        }
    }

    private boolean hasTermUpdateField(UpdateSessionRequest.TermUpdate update) {
        return update != null
                && (update.name() != null || update.startDate() != null || update.endDate() != null);
    }

    private void validateSessionDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new SchoolFeeException(
                    "INVALID_SESSION_CONFIG",
                    "Session start date is required",
                    "startDate");
        }
        if (endDate == null) {
            throw new SchoolFeeException(
                    "INVALID_SESSION_CONFIG",
                    "Session end date is required",
                    "endDate");
        }
        if (endDate.isBefore(startDate)) {
            throw new SchoolFeeException(
                    "INVALID_SESSION_CONFIG",
                    "Session end date must be on or after start date",
                    "endDate");
        }
    }

    private SchoolFeeException staleSessionUpdateException(Throwable cause) {
        return new SchoolFeeException(
                "STALE_RESOURCE",
                "Academic session was modified by another request. Please reload and try again.",
                "version",
                cause);
    }

    private record TermUpdatePlan(
            Term term,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean changed) {
    }

// ========================================================================
// CLOSE SESSION
// ========================================================================

    @Override
    public Mono<CloseSessionResponse> closeSession(UUID sessionId, CloseSessionRequest request) {
        if (sessionId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_SESSION",
                    "Session id is required",
                    "sessionId"));
        }

        return Mono.defer(() -> {
            CloseSessionRequest safeRequest = request == null
                    ? new CloseSessionRequest(false, false, null)
                    : request;

            return jwtUtils.getCurrentUser()
                    .flatMap(user -> transactionalOperator.transactional(
                            schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "SCHOOL_NOT_FOUND", "School not found")))
                                    .flatMap(school -> findSessionInCurrentSchoolForUpdate(sessionId, school.getId()))
                                    .flatMap(session -> applyCloseSession(
                                            user.getUserId(),
                                            session,
                                            safeRequest))));
        });
    }

    private Mono<CloseSessionResponse> applyCloseSession(
            UUID closedBy,
            AcademicSession session,
            CloseSessionRequest request) {
        if (isCompletedSession(session)) {
            return Mono.error(sessionAlreadyClosedException(session.getId()));
        }

        if (Boolean.TRUE.equals(session.getIsCurrent())) {
            return Mono.error(new SchoolFeeException(
                    "CANNOT_CLOSE_CURRENT_SESSION",
                    "Please set another session as current before closing this one.",
                    "sessionId"));
        }

        Instant closedAt = Instant.now();
        Mono<Integer> termsCompletedMono = request.completeAllTerms()
                ? completeSessionTerms(session.getId(), closedBy, closedAt)
                : Mono.just(0);

        if (request.archiveStudentRecords()) {
            log.info("Student archiving requested for session: {}. Not implemented yet.", session.getId());
        }

        session.setStatus(SESSION_STATUS_COMPLETED);
        session.setIsCurrent(false);
        session.setClosedAt(closedAt);
        session.setClosedBy(closedBy);
        session.setClosedNotes(isBlank(request.notes()) ? null : request.notes().trim());
        session.setUpdatedAt(closedAt);

        return termsCompletedMono
                .flatMap(termsCompleted -> sessionRepository.save(session)
                        .map(savedSession -> new CloseSessionResponse(
                                savedSession.getId(),
                                savedSession.getName(),
                                SESSION_STATUS_COMPLETED,
                                closedAt,
                                termsCompleted,
                                0,
                                buildCloseSessionMessage(request))))
                .onErrorMap(OptimisticLockingFailureException.class, this::staleSessionUpdateException);
    }

    private Mono<Integer> completeSessionTerms(UUID sessionId, UUID completedBy, Instant completedAt) {
        return termRepository.findBySessionId(sessionId)
                .flatMap(term -> {
                    term.setIsCurrent(false);
                    term.setStatus(TERM_STATUS_COMPLETED);
                    term.setCompletedAt(completedAt);
                    term.setCompletedBy(completedBy);
                    return termRepository.save(term);
                })
                .count()
                .map(Long::intValue);
    }

    private String buildCloseSessionMessage(CloseSessionRequest request) {
        StringBuilder message = new StringBuilder("Session closed.");
        if (request.completeAllTerms()) {
            message.append(" Terms marked completed.");
        }
        if (request.archiveStudentRecords()) {
            message.append(" Student archiving is not yet implemented.");
        }
        return message.toString();
    }

// ========================================================================
// SET CURRENT TERM
// ========================================================================

    @Override
    public Mono<SetCurrentTermResponse> setCurrentTerm(UUID termId) {
        if (termId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_TERM",
                    "Term id is required",
                    "termId"));
        }
        return jwtUtils.getCurrentUser()
                .flatMap(user -> transactionalOperator.transactional(
                        schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                .switchIfEmpty(Mono.error(new SchoolFeeException(
                                        "SCHOOL_NOT_FOUND", "School not found")))
                                .flatMap(school -> findTermForUpdate(termId)
                                        .flatMap(term -> findTermSessionInCurrentSchoolForUpdate(term, school.getId())
                                                .flatMap(this::requireSessionOpen)
                                                .flatMap(this::requireCurrentSession)
                                                .flatMap(session -> applyCurrentTerm(
                                                        session,
                                                        term,
                                                        user.getUserId()))))));
    }

    private Mono<Term> findTermForUpdate(UUID termId) {
        return termRepository.findByIdForUpdate(termId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "TERM_NOT_FOUND",
                        "Term not found: " + termId)));
    }

    private Mono<AcademicSession> findTermSessionInCurrentSchoolForUpdate(Term term, UUID schoolId) {
        return sessionRepository.findByIdForUpdate(term.getSessionId())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_FOUND",
                        "Parent session not found for term: " + term.getId())))
                .filter(session -> schoolId.equals(session.getSchoolId()))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "TERM_NOT_IN_SCHOOL",
                        "Term does not belong to your school")));
    }

    private Mono<AcademicSession> requireCurrentSession(AcademicSession session) {
        if (!Boolean.TRUE.equals(session.getIsCurrent())) {
            return Mono.error(new SchoolFeeException(
                    "TERM_NOT_IN_CURRENT_SESSION",
                    "Term does not belong to the current academic session",
                    "termId"));
        }
        return Mono.just(session);
    }

    private Mono<SetCurrentTermResponse> applyCurrentTerm(
            AcademicSession session,
            Term term,
            UUID updatedBy) {
        if (Boolean.TRUE.equals(term.getIsCurrent())) {
            return Mono.just(buildSetCurrentTermResponse(session, term, null));
        }

        Instant updatedAt = Instant.now();

        return termRepository.findBySessionIdOrderByTermNumberAsc(session.getId())
                .filter(existingTerm -> Boolean.TRUE.equals(existingTerm.getIsCurrent()))
                .filter(existingTerm -> !existingTerm.getId().equals(term.getId()))
                .next()
                .flatMap(previousTerm -> {
                    previousTerm.setIsCurrent(false);
                    previousTerm.setStatus(TERM_STATUS_COMPLETED);
                    previousTerm.setCompletedAt(updatedAt);
                    previousTerm.setCompletedBy(updatedBy);
                    return termRepository.save(previousTerm)
                            .map(Optional::of);
                })
                .defaultIfEmpty(Optional.empty())
                .flatMap(previousTerm -> {
                    term.setIsCurrent(true);
                    term.setStatus(TERM_STATUS_ACTIVE);
                    term.setCompletedAt(null);
                    term.setCompletedBy(null);
                    return termRepository.save(term)
                            .map(savedTerm -> buildSetCurrentTermResponse(
                                    session,
                                    savedTerm,
                                    previousTerm.orElse(null)));
                })
                .onErrorMap(DuplicateKeyException.class, this::currentTermConflictException)
                .onErrorMap(OptimisticLockingFailureException.class, this::staleTermUpdateException);
    }

    private SetCurrentTermResponse buildSetCurrentTermResponse(
            AcademicSession session,
            Term currentTerm,
            Term previousTerm) {
        SetCurrentTermResponse.PreviousTerm previous = previousTerm == null
                ? null
                : new SetCurrentTermResponse.PreviousTerm(
                        previousTerm.getId(),
                        previousTerm.getName(),
                        Objects.toString(previousTerm.getStatus(), TERM_STATUS_COMPLETED));

        return new SetCurrentTermResponse(
                currentTerm.getId(),
                currentTerm.getName(),
                session.getName(),
                true,
                currentTerm.getStartDate(),
                currentTerm.getEndDate(),
                previous);
    }

    private SchoolFeeException currentTermConflictException(Throwable cause) {
        return new SchoolFeeException(
                "CURRENT_TERM_CONFLICT",
                "Another current term was set for this session. Please reload and try again.",
                "termId",
                cause);
    }

    private SchoolFeeException staleTermUpdateException(Throwable cause) {
        return new SchoolFeeException(
                "STALE_RESOURCE",
                "Academic term was modified by another request. Please reload and try again.",
                "version",
                cause);
    }

}
