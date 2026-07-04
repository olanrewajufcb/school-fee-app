package com.fee.app.schoolfeeapp.fee.service.impl;


import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.domain.*;
import com.fee.app.schoolfeeapp.fee.dto.request.CreateFeeStructureRequest;
import com.fee.app.schoolfeeapp.fee.dto.response.*;
import com.fee.app.schoolfeeapp.fee.repository.*;
import com.fee.app.schoolfeeapp.fee.service.FeeService;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.fee.app.schoolfeeapp.student.repository.SchoolStudentGuardianLinkRepository;

@Service
@RequiredArgsConstructor
@Slf4j
class FeeServiceImpl implements FeeService {

    private final FeeStructureRepository structureRepository;
    private final FeeStructureItemRepository itemRepository;
    private final FeeStructureClassRepository structureClassRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final FeeCategoryRepository feeCategoryRepository;
    private final FeeReportingRepository feeReportingRepository;
    private final ClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final SchoolStudentGuardianLinkRepository guardianLinkRepository;
    private final AcademicSessionRepository sessionRepository;
    private final TermRepository termRepository;
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;
    private final UserRepository userRepository;

    // ========================================================================
    // CREATE FEE STRUCTURE
    // ========================================================================

    private Mono<UUID> resolveLocalUserId(UUID keycloakUserId) {
        return userRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakUserId)
                .map(User::getId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "USER_NOT_FOUND",
                        "Logged-in user not found in the database")));
    }

    @Override
    public Mono<CreateFeeStructureResponse> createFeeStructure(CreateFeeStructureRequest request) {
        return Mono.fromCallable(() -> validateAndNormalizeCreateFeeStructureRequest(request))
                .flatMap(normalizedRequest -> jwtUtils.getCurrentUser()
                        .flatMap(user -> {
                    UUID schoolId = user.getSchoolId();
                    UUID keycloakUserId = user.getUserId();

                    return resolveLocalUserId(keycloakUserId)
                            .flatMap(localUserId -> transactionalOperator.transactional(
                                    validateCreateDependencies(schoolId, normalizedRequest)
                                            .then(Mono.defer(() ->
                                                    structureRepository
                                                            .existsActiveBySchoolIdAndTermIdAndNameIgnoreCase(
                                                                    schoolId,
                                                                    normalizedRequest.termId(),
                                                                    normalizedRequest.name())))
                                            .flatMap(exists -> {
                                                if (Boolean.TRUE.equals(exists)) {
                                                    return Mono.error(new SchoolFeeException(
                                                            "DUPLICATE_FEE_STRUCTURE",
                                                            "An active fee structure with this name already exists for the term",
                                                            "name"));
                                                }
                                                return Mono.empty();
                                            })
                                            .then(Mono.defer(() -> {
                                        BigDecimal mandatoryAmount = calculateMandatoryAmount(normalizedRequest.items());
                                        BigDecimal totalAmount = calculateTotalAmount(normalizedRequest.items());

                                        FeeStructure structure = FeeStructure.builder()
                                                .id(UUID.randomUUID())
                                                .schoolId(schoolId)
                                                .name(normalizedRequest.name())
                                                .academicSessionId(normalizedRequest.sessionId())
                                                .termId(normalizedRequest.termId())
                                                .totalAmount(totalAmount)
                                                .dueDate(normalizedRequest.dueDate())
                                                .lateFeePercentage(normalizedRequest.lateFeeConfig() != null
                                                        ? BigDecimal.valueOf(normalizedRequest.lateFeeConfig().percentageAmount())
                                                        : BigDecimal.ZERO)
                                                .lateFeeFlatAmount(normalizedRequest.lateFeeConfig() != null
                                                        ? normalizedRequest.lateFeeConfig().flatAmount() : BigDecimal.ZERO)
                                                .lateFeeAppliesAfterDays(normalizedRequest.lateFeeConfig() != null
                                                        ? normalizedRequest.lateFeeConfig().applyAfterDays() : 14)
                                                .status("ACTIVE")
                                                .createdBy(localUserId)
                                                .build();

                                        return structureRepository.save(structure)
                                                        .flatMap(saved -> saveFeeItems(saved.getId(), normalizedRequest.items())
                                                                .then(saveStructureClasses(saved.getId(), normalizedRequest.applicableToClassIds()))
                                                                .then(countStudents(schoolId, normalizedRequest.applicableToClassIds()))
                                                                .map(studentCount -> new CreateFeeStructureResponse(
                                                                        saved.getId(),
                                                                        saved.getName(),
                                                                        totalAmount,
                                                                        mandatoryAmount,
                                                                        normalizedRequest.applicableToClassIds().size(),
                                                                        studentCount,
                                                                        saved.getDueDate(),
                                                                        saved.getStatus(),
                                                                        saved.getCreatedAt())));
                                    }))
                            ));
                }));
    }

    // ========================================================================
    // GET FEE STRUCTURES
    // ========================================================================

    @Override
    public Mono<List<FeeStructureResponse>> getFeeStructures(String status, String termId) {
        return Mono.fromCallable(() -> normalizeFeeStructureStatus(status))
                .flatMapMany(normalizedStatus -> jwtUtils.getCurrentUser()
                        .flatMapMany(user -> resolveOptionalTermFilter(user.getSchoolId(), termId)
                                .flatMapMany(termFilter -> findFeeStructures(
                                        user.getSchoolId(), normalizedStatus, termFilter)
                                        .flatMap(structure -> toFeeStructureResponse(
                                                user.getSchoolId(), structure)))))
                .collectList();
    }

    // ========================================================================
    // ASSIGN FEES TO STUDENTS
    // ========================================================================

    @Override
    public Mono<FeeAssignmentResponse> assignFeesToStudents(UUID structureId) {
        return Mono.fromCallable(() -> requireFeeStructureId(structureId))
                .flatMap(id -> jwtUtils.getCurrentUser()
                        .flatMap(user -> {
                            UUID schoolId = user.getSchoolId();
                            UUID keycloakUserId = user.getUserId();

                            return resolveLocalUserId(keycloakUserId)
                                    .flatMap(localUserId -> transactionalOperator.transactional(
                                            structureRepository.findByIdAndSchoolIdForUpdate(id, schoolId)
                                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                                            "STRUCTURE_NOT_FOUND",
                                                            "Fee structure not found or does not belong to your school")))
                                                    .flatMap(structure -> validateStructureCanAssign(structure)
                                                            .then(Mono.defer(() -> structureClassRepository
                                                                    .findByFeeStructureId(id)
                                                                    .map(FeeStructureClass::getClassId)
                                                                    .collectList()))
                                                            .flatMap(classIds -> {
                                                                if (classIds.isEmpty()) {
                                                                    return Mono.error(new SchoolFeeException(
                                                                            "NO_CLASSES_FOR_STRUCTURE",
                                                                            "Fee structure has no applicable classes"));
                                                                }
                                                                return studentRepository
                                                                        .findActiveBySchoolIdAndCurrentClassIdIn(
                                                                                schoolId, classIds)
                                                                        .collectList()
                                                                        .flatMap(students -> assignFeesToStudentBatch(
                                                                                structure, students, localUserId));
                                                            }))
                                    ));
                        }));
    }

    private Mono<FeeAssignmentResponse> assignFeesToStudentBatch(
            FeeStructure structure, List<Student> students, UUID assignedBy) {

        return Flux.fromIterable(students)
                .concatMap(student -> {
                    StudentFee studentFee = StudentFee.builder()
                            .id(UUID.randomUUID())
                            .studentId(student.getId())
                            .schoolId(structure.getSchoolId())
                            .feeStructureId(structure.getId())
                            .totalAmount(structure.getTotalAmount())
                            .discountAmount(BigDecimal.ZERO)
                            .dueDate(structure.getDueDate())
                            .build();

                    return studentFeeRepository.findByStudentIdAndFeeStructureId(student.getId(), structure.getId())
                            .map(existing -> FeeAssignmentResult.skippedResult())
                            .switchIfEmpty(Mono.defer(() -> studentFeeRepository.save(studentFee)
                                    .flatMap(savedFee -> {
                                        LedgerEntry entry = LedgerEntry.builder()
                                                .id(UUID.randomUUID())
                                                .studentFeeId(savedFee.getId())
                                                .studentId(student.getId())
                                                .schoolId(structure.getSchoolId())
                                                .entryType("FEE_ASSIGNED")
                                                .amount(structure.getTotalAmount())
                                                .balanceAfter(structure.getTotalAmount())
                                                .sourceEntityType("fee_structure")
                                                .sourceEntityId(structure.getId())
                                                .description("Fee assigned: " + structure.getName())
                                                .transactionDate(Instant.now())
                                                .idempotencyKey(assignmentIdempotencyKey(
                                                        structure.getId(), student.getId()))
                                                .recordedBy(assignedBy)
                                                .build();

                                        return ledgerEntryRepository.save(entry)
                                                .thenReturn(FeeAssignmentResult.assignedResult());
                                    })
                                    .onErrorResume(DuplicateKeyException.class, e -> {
                                        log.info("Skipping existing fee assignment: structureId={}, studentId={}",
                                                structure.getId(), student.getId());
                                        return Mono.just(FeeAssignmentResult.skippedResult());
                                    })));
                })
                .filter(FeeAssignmentResult::assigned)
                .count()
                .map(Long::intValue)
                .map(assignedCount -> {
                    LocalDate nextReminder = structure.getDueDate().minusDays(3);
                    return new FeeAssignmentResponse(
                            structure.getId(),
                            assignedCount,
                            structure.getTotalAmount()
                                    .multiply(BigDecimal.valueOf(assignedCount)),
                            "ASSIGNED",
                            nextReminder.isAfter(LocalDate.now()) ? nextReminder : LocalDate.now(),
                            "Fees assigned to " + assignedCount + " students. " +
                                    "Reminders scheduled for " + nextReminder + "."
                    );
                });
    }

    // ========================================================================
    // GET STUDENT FEES (Parent View)
    // ========================================================================

    @Override
    public Mono<List<StudentFeeResponse>> getStudentFees(UUID studentId) {
        return Mono.fromCallable(() -> requireStudentId(studentId))
                .flatMap(id -> jwtUtils.getCurrentUser()
                        .flatMap(user -> verifyStudentFeeAccess(user, id)
                                .then(Mono.defer(() -> {
                                    var flux = termRepository.findCurrentTermsBySchoolId(user.getSchoolId());
                                    return (flux != null ? flux.next() : Mono.<Term>empty())
                                            .map(Optional::of)
                                            .defaultIfEmpty(Optional.empty());
                                }))
                                .flatMap(currentTermOpt -> studentFeeRepository.findByStudentIdAndSchoolId(
                                                id, user.getSchoolId())
                                        .flatMap(fee -> toStudentFeeResponse(fee, currentTermOpt.orElse(null)))
                                        .collectList())));
    }

    private Mono<Void> verifyStudentFeeAccess(SchoolFeeUser user, UUID studentId) {
        if (user.isParent()) {
            return verifyParentAccess(user.getUserId(), studentId, user.getSchoolId());
        }
        return studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, user.getSchoolId())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "STUDENT_NOT_FOUND",
                        "Student not found or does not belong to your school",
                        "studentId")))
                .then();
    }

    private Mono<Void> verifyParentAccess(UUID userId, UUID studentId, UUID schoolId) {
        return guardianLinkRepository
                .findFeeAccessByGuardianUserIdAndStudentIdAndSchoolId(userId, studentId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "ACCESS_DENIED",
                        "You do not have access to this student's fees",
                        "studentId")))
                .then();
    }

    // ========================================================================
    // FEE DASHBOARD
    // ========================================================================

    @Override
    public Mono<FeeDashboardResponse> getFeeDashboard(String termId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = user.getSchoolId();
                    return resolveDashboardTerm(schoolId, termId)
                            .flatMap(term -> buildDashboard(schoolId, term));
                });
    }

    @Override
    public Mono<List<UUID>> getOutstandingFeeIds(String termId, String filter) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = user.getSchoolId();
                    LocalDate today = LocalDate.now();
                    return resolveDashboardTerm(schoolId, termId)
                            .flatMapMany(term -> feeReportingRepository
                                    .getOutstandingFeeIds(schoolId, term.getId(), filter, today))
                            .collectList();
                });
    }

    private Mono<FeeDashboardResponse> buildDashboard(UUID schoolId, Term term) {
        LocalDate today = LocalDate.now();

        Mono<FeeDashboardResponse.DashboardSummary> summaryMono = feeReportingRepository
                .getDashboardSummary(schoolId, term.getId())
                .map(stats -> new FeeDashboardResponse.DashboardSummary(
                        stats.totalExpected(),
                        stats.totalCollected(),
                        stats.totalOutstanding(),
                        calculateCollectionRate(stats.totalCollected(), stats.totalExpected()),
                        stats.fullyPaidStudents(),
                        stats.partiallyPaidStudents(),
                        stats.unpaidStudents()));

        Mono<List<FeeDashboardResponse.ClassCollection>> byClassMono = feeReportingRepository
                .getClassCollections(schoolId, term.getId())
                .map(stats -> new FeeDashboardResponse.ClassCollection(
                        stats.classId(),
                        stats.className(),
                        stats.studentCount(),
                        stats.expectedAmount(),
                        stats.collectedAmount(),
                        calculateCollectionRate(stats.collectedAmount(), stats.expectedAmount())))
                .collectList();

        Mono<FeeDashboardResponse.UpcomingDeadlines> deadlinesMono = feeReportingRepository
                .getDeadlineStats(schoolId, term.getId(), today)
                .map(stats -> new FeeDashboardResponse.UpcomingDeadlines(
                        new FeeDashboardResponse.DeadlineInfo(
                                stats.dueInThreeDaysCount(), stats.dueInThreeDaysAmount()),
                        new FeeDashboardResponse.DeadlineInfo(
                                stats.dueTodayCount(), stats.dueTodayAmount()),
                        new FeeDashboardResponse.DeadlineInfo(
                                stats.overdueCount(), stats.overdueAmount())));

        Mono<List<FeeDashboardResponse.DailyTrend>> trendsMono = feeReportingRepository
                .getDailyCollectionTrend(schoolId, term.getId(), today.minusDays(6), today)
                .map(stats -> new FeeDashboardResponse.DailyTrend(
                        stats.date(),
                        stats.amount(),
                        stats.transactions()))
                .collectList();

        return Mono.zip(summaryMono, byClassMono, deadlinesMono, trendsMono)
                .map(tuple -> new FeeDashboardResponse(
                        term.getName(),
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4()));
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private CreateFeeStructureRequest validateAndNormalizeCreateFeeStructureRequest(
            CreateFeeStructureRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_FEE_STRUCTURE",
                    "Fee structure request is required");
        }

        String name = requireText(request.name(), "name", "Fee structure name is required");
        if (request.sessionId() == null) {
            throw new SchoolFeeException(
                    "INVALID_FEE_STRUCTURE",
                    "Academic session ID is required",
                    "sessionId");
        }
        if (request.termId() == null) {
            throw new SchoolFeeException(
                    "INVALID_FEE_STRUCTURE",
                    "Term ID is required",
                    "termId");
        }
        if (request.dueDate() == null) {
            throw new SchoolFeeException(
                    "INVALID_FEE_STRUCTURE",
                    "Due date is required",
                    "dueDate");
        }
        if (request.dueDate().isBefore(LocalDate.now())) {
            throw new SchoolFeeException(
                    "INVALID_DUE_DATE",
                    "Due date cannot be in the past",
                    "dueDate");
        }

        List<UUID> classIds = normalizeClassIds(request.applicableToClassIds());
        List<CreateFeeStructureRequest.FeeItemRequest> items = normalizeFeeItems(request.items());
        CreateFeeStructureRequest.LateFeeConfig lateFeeConfig =
                normalizeLateFeeConfig(request.lateFeeConfig());

        return new CreateFeeStructureRequest(
                name,
                request.sessionId(),
                request.termId(),
                classIds,
                request.dueDate(),
                items,
                lateFeeConfig);
    }

    private List<UUID> normalizeClassIds(List<UUID> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            throw new SchoolFeeException(
                    "INVALID_FEE_STRUCTURE",
                    "At least one class must be specified",
                    "applicableToClassIds");
        }
        LinkedHashSet<UUID> uniqueClassIds = new LinkedHashSet<>();
        for (int index = 0; index < classIds.size(); index++) {
            UUID classId = classIds.get(index);
            if (classId == null) {
                throw new SchoolFeeException(
                        "INVALID_FEE_STRUCTURE",
                        "Class ID is required",
                        "applicableToClassIds[" + index + "]");
            }
            uniqueClassIds.add(classId);
        }
        return List.copyOf(uniqueClassIds);
    }

    private List<CreateFeeStructureRequest.FeeItemRequest> normalizeFeeItems(
            List<CreateFeeStructureRequest.FeeItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new SchoolFeeException(
                    "INVALID_FEE_STRUCTURE",
                    "At least one fee item is required",
                    "items");
        }

        List<CreateFeeStructureRequest.FeeItemRequest> normalizedItems = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            CreateFeeStructureRequest.FeeItemRequest item = items.get(index);
            if (item == null) {
                throw new SchoolFeeException(
                        "INVALID_FEE_ITEM",
                        "Fee item is required",
                        "items[" + index + "]");
            }
            if (item.amount() == null || item.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new SchoolFeeException(
                        "INVALID_FEE_ITEM",
                        "Fee item amount must be greater than 0",
                        "items[" + index + "].amount");
            }
            normalizedItems.add(new CreateFeeStructureRequest.FeeItemRequest(
                    item.categoryId(),
                    requireText(item.description(), "items[" + index + "].description",
                            "Fee item description is required"),
                    item.amount(),
                    item.isMandatory(),
                    Math.max(item.sortOrder(), 0)));
        }
        return List.copyOf(normalizedItems);
    }

    private CreateFeeStructureRequest.LateFeeConfig normalizeLateFeeConfig(
            CreateFeeStructureRequest.LateFeeConfig config) {
        if (config == null) {
            return null;
        }
        if (config.applyAfterDays() < 0) {
            throw new SchoolFeeException(
                    "INVALID_LATE_FEE_CONFIG",
                    "Late fee apply-after days cannot be negative",
                    "lateFeeConfig.applyAfterDays");
        }
        if (config.percentageAmount() < 0) {
            throw new SchoolFeeException(
                    "INVALID_LATE_FEE_CONFIG",
                    "Late fee percentage cannot be negative",
                    "lateFeeConfig.percentageAmount");
        }
        BigDecimal flatAmount = Optional.ofNullable(config.flatAmount()).orElse(BigDecimal.ZERO);
        if (flatAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new SchoolFeeException(
                    "INVALID_LATE_FEE_CONFIG",
                    "Late fee flat amount cannot be negative",
                    "lateFeeConfig.flatAmount");
        }
        return new CreateFeeStructureRequest.LateFeeConfig(
                config.applyAfterDays(),
                config.percentageAmount(),
                flatAmount);
    }

    private String requireText(String value, String field, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new SchoolFeeException("INVALID_FEE_STRUCTURE", message, field);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UUID requireFeeStructureId(UUID structureId) {
        if (structureId == null) {
            throw new SchoolFeeException(
                    "INVALID_FEE_STRUCTURE",
                    "Fee structure ID is required",
                    "structureId");
        }
        return structureId;
    }

    private UUID requireStudentId(UUID studentId) {
        if (studentId == null) {
            throw new SchoolFeeException(
                    "INVALID_STUDENT",
                    "Student ID is required",
                    "studentId");
        }
        return studentId;
    }

    private String normalizeFeeStructureStatus(String status) {
        String normalized = Optional.ofNullable(trimToNull(status))
                .orElse("ACTIVE")
                .toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(normalized)) {
            throw new SchoolFeeException(
                    "INVALID_STATUS",
                    "Fee structure status must be ACTIVE or INACTIVE",
                    "status");
        }
        return normalized;
    }

    private Mono<Optional<UUID>> resolveOptionalTermFilter(UUID schoolId, String termId) {
        String normalized = trimToNull(termId);
        if (normalized == null) {
            return Mono.just(Optional.empty());
        }
        return resolveDashboardTerm(schoolId, normalized)
                .map(term -> Optional.of(term.getId()));
    }

    private Flux<FeeStructure> findFeeStructures(
            UUID schoolId, String status, Optional<UUID> termFilter) {
        if (termFilter.isPresent()) {
            return structureRepository.findBySchoolIdAndTermIdAndStatus(
                    schoolId, termFilter.get(), status);
        }
        return structureRepository.findBySchoolIdAndStatus(schoolId, status);
    }

    private Mono<Term> resolveDashboardTerm(UUID schoolId, String termId) {
        String normalized = trimToNull(termId);
        if (normalized == null || "current".equalsIgnoreCase(normalized)) {
            return termRepository.findCurrentTermsBySchoolId(schoolId)
                    .next()
                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                            "TERM_NOT_FOUND",
                            "Current term not found",
                            "termId")));
        }

        UUID parsedTermId = parseUuid(normalized, "termId", "INVALID_TERM_ID");
        return termRepository.findById(parsedTermId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "TERM_NOT_FOUND",
                        "Term not found",
                        "termId")))
                .flatMap(term -> validateTermBelongsToSchool(term, schoolId)
                        .thenReturn(term));
    }

    private UUID parseUuid(String value, String field, String errorCode) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new SchoolFeeException(errorCode, "Invalid UUID: " + value, field, e);
        }
    }

    private Mono<Void> validateTermBelongsToSchool(Term term, UUID schoolId) {
        return sessionRepository.findById(term.getSessionId())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "TERM_NOT_FOUND",
                        "Term not found",
                        "termId")))
                .flatMap(session -> {
                    if (schoolId.equals(session.getSchoolId())) {
                        return Mono.empty();
                    }
                    return Mono.error(new SchoolFeeException(
                            "TERM_NOT_FOUND",
                            "Term not found",
                            "termId"));
                });
    }

    private Mono<Void> validateStructureCanAssign(FeeStructure structure) {
        if (!"ACTIVE".equalsIgnoreCase(Objects.toString(structure.getStatus(), ""))) {
            return Mono.error(new SchoolFeeException(
                    "STRUCTURE_INACTIVE",
                    "Only active fee structures can be assigned"));
        }
        if (structure.getDueDate() == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_FEE_STRUCTURE",
                    "Fee structure due date is required",
                    "dueDate"));
        }
        return Mono.empty();
    }

    private UUID assignmentIdempotencyKey(UUID structureId, UUID studentId) {
        return UUID.nameUUIDFromBytes(
                ("fee-assignment:" + structureId + ":" + studentId)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private Mono<Void> validateCreateDependencies(UUID schoolId, CreateFeeStructureRequest request) {
        return sessionRepository.findByIdForUpdate(request.sessionId())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_FOUND", "Session not found", "sessionId")))
                .flatMap(session -> validateSessionCanReceiveFeeStructure(session, schoolId)
                        .then(termRepository.findByIdAndDeletedAtIsNullForUpdate(request.termId())
                                .switchIfEmpty(Mono.error(new SchoolFeeException(
                                        "TERM_NOT_FOUND", "Term not found", "termId")))
                                .flatMap(term -> validateTermCanReceiveFeeStructure(term, session, request)
                                        .then(validateClasses(schoolId, request.applicableToClassIds()))
                                        .then(validateFeeCategories(schoolId, request.items())))));
    }

    private Mono<Void> validateSessionCanReceiveFeeStructure(
            com.fee.app.schoolfeeapp.school.domain.AcademicSession session, UUID schoolId) {
        if (!schoolId.equals(session.getSchoolId())) {
            return Mono.error(new SchoolFeeException(
                    "SESSION_NOT_IN_SCHOOL",
                    "Session does not belong to your school",
                    "sessionId"));
        }
        if (session.getDeletedAt() != null) {
            return Mono.error(new SchoolFeeException(
                    "SESSION_NOT_FOUND", "Session not found", "sessionId"));
        }
        if ("COMPLETED".equalsIgnoreCase(Objects.toString(session.getStatus(), ""))
                || session.getClosedAt() != null) {
            return Mono.error(new SchoolFeeException(
                    "SESSION_ALREADY_CLOSED",
                    "Closed sessions cannot receive new fee structures",
                    "sessionId"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateTermCanReceiveFeeStructure(
            Term term,
            com.fee.app.schoolfeeapp.school.domain.AcademicSession session,
            CreateFeeStructureRequest request) {
        if (!request.sessionId().equals(term.getSessionId())) {
            return Mono.error(new SchoolFeeException(
                    "TERM_NOT_IN_SESSION",
                    "Term does not belong to this session",
                    "termId"));
        }
        if ("COMPLETED".equalsIgnoreCase(Objects.toString(term.getStatus(), ""))
                || term.getCompletedAt() != null) {
            return Mono.error(new SchoolFeeException(
                    "TERM_ALREADY_COMPLETED",
                    "Completed terms cannot receive new fee structures",
                    "termId"));
        }
        if (term.getStartDate() != null && request.dueDate().isBefore(term.getStartDate())) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_DUE_DATE",
                    "Due date cannot be before the term start date",
                    "dueDate"));
        }
        if (session.getEndDate() != null && request.dueDate().isAfter(session.getEndDate())) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_DUE_DATE",
                    "Due date cannot be after the session end date",
                    "dueDate"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateClasses(UUID schoolId, List<UUID> classIds) {
        return Flux.fromIterable(classIds)
                .concatMap(classId -> classRepository.findByIdAndSchoolId(classId, schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "CLASS_NOT_FOUND",
                                "Class not found: " + classId,
                                "applicableToClassIds")))
                        .flatMap(cls -> {
                            if (!Boolean.TRUE.equals(cls.getIsActive())) {
                                return Mono.error(new SchoolFeeException(
                                        "CLASS_INACTIVE",
                                        "Inactive classes cannot receive fee structures",
                                        "applicableToClassIds"));
                            }
                            return Mono.empty();
                        }))
                .then();
    }

    private Mono<Void> validateFeeCategories(
            UUID schoolId, List<CreateFeeStructureRequest.FeeItemRequest> items) {
        return Flux.fromIterable(items)
                .flatMap(item -> Mono.justOrEmpty(item.categoryId()))
                .distinct()
                .concatMap(categoryId -> feeCategoryRepository.existsByIdAndSchoolId(categoryId, schoolId)
                        .flatMap(exists -> {
                            if (Boolean.TRUE.equals(exists)) {
                                return Mono.empty();
                            }
                            return Mono.error(new SchoolFeeException(
                                    "FEE_CATEGORY_NOT_FOUND",
                                    "Fee category not found: " + categoryId,
                                    "items.categoryId"));
                        }))
                .then();
    }

    private Mono<Void> saveFeeItems(UUID structureId, List<CreateFeeStructureRequest.FeeItemRequest> items) {
        return Flux.fromIterable(items)
                .concatMap(item -> {
                    FeeStructureItem entity = FeeStructureItem.builder()
                            .feeStructureId(structureId)
                            .feeCategoryId(item.categoryId())
                            .description(item.description())
                            .amount(item.amount())
                            .isMandatory(item.isMandatory())
                            .sortOrder(item.sortOrder())
                            .build();
                    return itemRepository.save(entity);
                })
                .then();
    }

    private Mono<Void> saveStructureClasses(UUID structureId, List<UUID> classIds) {
        return Flux.fromIterable(classIds)
                .concatMap(classId -> structureClassRepository.insertLink(structureId, classId))
                .then();
    }

    private Mono<Integer> countStudents(UUID schoolId, List<UUID> classIds) {
        if (classIds.isEmpty()) {
            return Mono.just(0);
        }
        return studentRepository.countActiveBySchoolIdAndCurrentClassIdIn(schoolId, classIds)
                .map(Long::intValue);
    }

    private BigDecimal calculateMandatoryAmount(List<CreateFeeStructureRequest.FeeItemRequest> items) {
        return items.stream()
                .filter(CreateFeeStructureRequest.FeeItemRequest::isMandatory)
                .map(CreateFeeStructureRequest.FeeItemRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalAmount(List<CreateFeeStructureRequest.FeeItemRequest> items) {
        return items.stream()
                .map(CreateFeeStructureRequest.FeeItemRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Mono<FeeStructureResponse> toFeeStructureResponse(UUID schoolId, FeeStructure structure) {
        Mono<List<UUID>> classIdsMono = structureClassRepository.findByFeeStructureId(structure.getId())
                .map(FeeStructureClass::getClassId)
                .collectList()
                .cache();

        Mono<List<FeeStructureItem>> itemsMono = itemRepository
                .findByFeeStructureIdOrderBySortOrderAsc(structure.getId())
                .collectList()
                .cache();

        return Mono.zip(
                termRepository.findById(structure.getTermId())
                        .map(Term::getName)
                        .defaultIfEmpty("Unknown"),
                sessionRepository.findById(structure.getAcademicSessionId())
                        .map(session -> session.getName().split(" ")[0])
                        .defaultIfEmpty("Unknown"),
                classIdsMono.flatMap(classIds -> loadClassNames(schoolId, classIds)),
                classIdsMono.flatMap(classIds -> countStudents(schoolId, classIds)),
                itemsMono.map(this::calculateMandatoryAmountFromEntities),
                feeReportingRepository.getStructureCollectionStats(schoolId, structure.getId())
        ).map(tuple -> new FeeStructureResponse(
                structure.getId(),
                structure.getName(),
                tuple.getT1(),
                tuple.getT2(),
                structure.getTotalAmount(),
                tuple.getT5(),
                tuple.getT3(),
                tuple.getT3().size(),
                tuple.getT4(),
                calculateCollectionRate(tuple.getT6().collectedAmount(), tuple.getT6().expectedAmount()),
                structure.getDueDate(),
                structure.getStatus(),
                null,
                structure.getCreatedAt()
        ));
    }

    private Mono<List<String>> loadClassNames(UUID schoolId, List<UUID> classIds) {
        if (classIds.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        return Flux.fromIterable(classIds)
                .concatMap(classId -> classRepository.findByIdAndSchoolId(classId, schoolId))
                .map(ClassEntity::getName)
                .collectList();
    }

    private BigDecimal calculateMandatoryAmountFromEntities(List<FeeStructureItem> items) {
        return items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsMandatory()))
                .map(FeeStructureItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Mono<StudentFeeResponse> toStudentFeeResponse(StudentFee fee) {
        var flux = termRepository.findCurrentTermsBySchoolId(fee.getSchoolId());
        return (flux != null ? flux.next() : Mono.<Term>empty())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(currentTermOpt -> toStudentFeeResponse(fee, currentTermOpt.orElse(null)));
    }

    private Mono<StudentFeeResponse> toStudentFeeResponse(StudentFee fee, Term currentTerm) {
        return structureRepository.findById(fee.getFeeStructureId())
                .flatMap(structure -> Mono.zip(
                        itemRepository.findByFeeStructureIdOrderBySortOrderAsc(structure.getId())
                                .map(item -> new StudentFeeResponse.FeeItemDetail(
                                        item.getDescription(),
                                        item.getAmount(),
                                        Boolean.TRUE.equals(item.getIsMandatory())))
                                .collectList(),
                        termRepository.findById(structure.getTermId())
                                .defaultIfEmpty(Term.builder().name("Unknown").build()),
                        ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(fee.getId())
                                .collectList()
                ).map(tuple -> {
                    StudentFeeFinancialSummary summary = calculateStudentFeeFinancialSummary(fee, tuple.getT3());
                    long daysUntilDue = fee.getDueDate() != null ? ChronoUnit.DAYS.between(LocalDate.now(), fee.getDueDate()) : 0;

                    Term term = tuple.getT2();
                    boolean isCurrent = false;
                    boolean isUpcoming = false;
                    if (term.getId() != null) {
                        if (Boolean.TRUE.equals(term.getIsCurrent())) {
                            isCurrent = true;
                        } else if (currentTerm != null) {
                            if (term.getStartDate() != null && currentTerm.getStartDate() != null
                                    && term.getStartDate().isAfter(currentTerm.getStartDate())) {
                                isUpcoming = true;
                            }
                        } else {
                            if (term.getStartDate() != null && term.getStartDate().isAfter(LocalDate.now())) {
                                isUpcoming = true;
                            }
                        }
                    }

                    return new StudentFeeResponse(
                            fee.getId(),
                            structure.getName(),
                            term.getName(),
                            isCurrent,
                            isUpcoming,
                            tuple.getT1(),
                            fee.getTotalAmount(),
                            Optional.ofNullable(fee.getDiscountAmount()).orElse(BigDecimal.ZERO),
                            summary.amountPaid(),
                            summary.balance(),
                            fee.getDueDate(),
                            daysUntilDue,
                            summary.status(),
                            Boolean.TRUE.equals(fee.getIsLateFeeApplied()),
                            summary.lateFeeAmount(),
                            fee.getLastReminderSentAt() != null
                                    ? fee.getLastReminderSentAt().atZone(ZoneOffset.UTC)
                                    : null
                    );
                }));
    }

    private StudentFeeFinancialSummary calculateStudentFeeFinancialSummary(
            StudentFee fee, List<LedgerEntry> entries) {
        BigDecimal amountPaid = entries.stream()
                .filter(entry -> "PAYMENT".equalsIgnoreCase(Objects.toString(entry.getEntryType(), "")))
                .map(entry -> absolute(Optional.ofNullable(entry.getAmount()).orElse(BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ledgerBalance = entries.stream()
                .map(entry -> Optional.ofNullable(entry.getAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal defaultBalance = expectedStudentFeeAmount(fee);
        BigDecimal balance = entries.isEmpty() ? defaultBalance : ledgerBalance.max(BigDecimal.ZERO);

        BigDecimal ledgerLateFee = entries.stream()
                .filter(entry -> "LATE_FEE_APPLIED".equalsIgnoreCase(
                        Objects.toString(entry.getEntryType(), "")))
                .map(entry -> Optional.ofNullable(entry.getAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal storedLateFee = Optional.ofNullable(fee.getLateFeeAmount()).orElse(BigDecimal.ZERO);

        return new StudentFeeFinancialSummary(
                amountPaid,
                balance,
                deriveStudentFeeStatus(fee.getDueDate(), amountPaid, balance),
                storedLateFee.max(ledgerLateFee));
    }

    private BigDecimal expectedStudentFeeAmount(StudentFee fee) {
        return Optional.ofNullable(fee.getTotalAmount()).orElse(BigDecimal.ZERO)
                .subtract(Optional.ofNullable(fee.getDiscountAmount()).orElse(BigDecimal.ZERO))
                .add(Optional.ofNullable(fee.getLateFeeAmount()).orElse(BigDecimal.ZERO))
                .max(BigDecimal.ZERO);
    }

    private String deriveStudentFeeStatus(LocalDate dueDate, BigDecimal amountPaid, BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            return "PAID";
        }
        if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            return "PARTIAL";
        }
        if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
            return "OVERDUE";
        }
        return "PENDING";
    }

    private BigDecimal absolute(BigDecimal amount) {
        return amount.signum() < 0 ? amount.negate() : amount;
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

    private record FeeAssignmentResult(boolean assigned) {
        static FeeAssignmentResult assignedResult() {
            return new FeeAssignmentResult(true);
        }

        static FeeAssignmentResult skippedResult() {
            return new FeeAssignmentResult(false);
        }
    }

    private record StudentFeeFinancialSummary(
            BigDecimal amountPaid,
            BigDecimal balance,
            String status,
            BigDecimal lateFeeAmount) {
    }
}
