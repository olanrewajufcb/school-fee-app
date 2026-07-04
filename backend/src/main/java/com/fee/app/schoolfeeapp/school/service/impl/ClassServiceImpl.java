package com.fee.app.schoolfeeapp.school.service.impl;


import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.school.dto.request.CreateClassRequest;
import com.fee.app.schoolfeeapp.school.dto.request.PromoteStudentsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.*;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import com.fee.app.schoolfeeapp.student.repository.SchoolStudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.school.service.ClassService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
class ClassServiceImpl implements ClassService {

    private static final String SESSION_STATUS_COMPLETED = "COMPLETED";
    private static final int DEFAULT_CLASS_CAPACITY = 40;
    private static final Comparator<ClassEntity> CLASS_LIST_ORDER = Comparator
            .comparing((ClassEntity cls) -> Objects.toString(cls.getGradeLevel(), ""), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(cls -> Objects.toString(cls.getName(), ""), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(cls -> Objects.toString(cls.getSection(), ""), String.CASE_INSENSITIVE_ORDER);

    private final ClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final AcademicSessionRepository sessionRepository;
    private final SchoolRepository schoolRepository;
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;
    private final SchoolStudentGuardianLinkRepository guardianLinkRepository;
    private final StudentGuardianRepository guardianRepository;

    // ========================================================================
    // CREATE CLASS
    // ========================================================================

    @Override
    public Mono<ClassResponse> createClass(CreateClassRequest request) {
        return Mono.defer(() -> {
            validateCreateClassRequest(request);

            return jwtUtils.getCurrentUser()
                    .flatMap(user -> transactionalOperator.transactional(
                            schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "SCHOOL_NOT_FOUND", "School not found")))
                                    .flatMap(school -> findSessionInSchoolForClassCreation(
                                            request.academicSessionId(),
                                            school.getId()))
                                    .flatMap(session -> persistClass(user.getSchoolId(), session, request))));
        });
    }

    private void validateCreateClassRequest(CreateClassRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_CONFIG",
                    "Class request is required");
        }
        if (isBlank(request.name())) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_CONFIG",
                    "Class name is required",
                    "name");
        }
        if (isBlank(request.gradeLevel())) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_CONFIG",
                    "Grade level is required",
                    "gradeLevel");
        }
        if (request.academicSessionId() == null) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_CONFIG",
                    "Academic session ID is required",
                    "academicSessionId");
        }
        if (request.capacity() < 1) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_CONFIG",
                    "Capacity must be greater than 0",
                    "capacity");
        }
    }

    private Mono<AcademicSession> findSessionInSchoolForClassCreation(UUID sessionId, UUID schoolId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_FOUND",
                        "Academic session not found: " + sessionId)))
                .filter(session -> schoolId.equals(session.getSchoolId()))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_IN_SCHOOL",
                        "Session does not belong to your school")))
                .flatMap(session -> {
                    if (SESSION_STATUS_COMPLETED.equalsIgnoreCase(Objects.toString(session.getStatus(), ""))) {
                        return Mono.error(new SchoolFeeException(
                                "SESSION_ALREADY_CLOSED",
                                "Session is already closed and cannot be modified: " + session.getId(),
                                "academicSessionId"));
                    }
                    return Mono.just(session);
                });
    }

    private Mono<ClassResponse> persistClass(UUID schoolId, AcademicSession session, CreateClassRequest request) {
        ClassEntity newClass = ClassEntity.builder()
                .schoolId(schoolId)
                .name(request.name().trim())
                .gradeLevel(request.gradeLevel().trim())
                .section(isBlank(request.section()) ? null : request.section().trim())
                .academicSessionId(session.getId())
                .classTeacherId(request.classTeacherId())
                .capacity(request.capacity() > 0 ? request.capacity() : DEFAULT_CLASS_CAPACITY)
                .isActive(true)
                .build();

        return classRepository.save(newClass)
                .map(saved -> buildClassResponse(saved, session.getName()))
                .onErrorMap(DuplicateKeyException.class, error -> duplicateClassException(
                        schoolId,
                        newClass.getName(),
                        session.getId(),
                        error));
    }

    private SchoolFeeException duplicateClassException(
            UUID schoolId,
            String className,
            UUID sessionId,
            Throwable cause) {
        log.warn("Duplicate class: school={}, name={}, session={}", schoolId, className, sessionId);
        return new SchoolFeeException(
                "DUPLICATE_CLASS",
                "A class named '" + className + "' already exists in this session",
                "name",
                cause);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ========================================================================
    // LIST CLASSES
    // ========================================================================

    @Override
    public Mono<List<ClassResponse>> listClasses(String sessionId, String gradeLevel, String status) {
        return Mono.defer(() -> {
            ClassListFilters filters = parseClassListFilters(sessionId, gradeLevel, status);

            return jwtUtils.getCurrentUser()
                    .flatMap(user -> transactionalOperator.transactional(
                            schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "SCHOOL_NOT_FOUND", "School not found")))
                                    .flatMap(school -> resolveListSessionFilter(filters, school.getId())
                                            .flatMapMany(resolvedSession -> resolvedSession.noMatches()
                                                    ? Flux.empty()
                                                    : fetchClasses(
                                                            school.getId(),
                                                            resolvedSession.sessionId(),
                                                            filters.gradeLevel(),
                                                            filters.status()))
                                            .sort(CLASS_LIST_ORDER)
                                            .flatMap(this::enrichClassResponse)
                                            .collectList())));
        });
    }

    private ClassListFilters parseClassListFilters(String sessionId, String gradeLevel, String status) {
        String normalizedSessionId = normalizeNullable(sessionId);
        String normalizedGradeLevel = normalizeNullable(gradeLevel);
        ClassListStatus normalizedStatus = parseClassListStatus(status);

        if (normalizedSessionId == null) {
            return new ClassListFilters(SessionFilterType.NONE, null, normalizedGradeLevel, normalizedStatus);
        }
        if ("current".equalsIgnoreCase(normalizedSessionId)) {
            return new ClassListFilters(SessionFilterType.CURRENT, null, normalizedGradeLevel, normalizedStatus);
        }

        try {
            return new ClassListFilters(
                    SessionFilterType.EXPLICIT,
                    UUID.fromString(normalizedSessionId),
                    normalizedGradeLevel,
                    normalizedStatus);
        } catch (IllegalArgumentException error) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_FILTER",
                    "Session ID must be a valid UUID or 'current'",
                    "sessionId",
                    error);
        }
    }

    private ClassListStatus parseClassListStatus(String status) {
        String normalizedStatus = normalizeNullable(status);
        if (normalizedStatus == null) {
            return ClassListStatus.ACTIVE;
        }

        try {
            return ClassListStatus.valueOf(normalizedStatus.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_FILTER",
                    "Status must be ACTIVE or INACTIVE",
                    "status",
                    error);
        }
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Mono<ResolvedListSession> resolveListSessionFilter(ClassListFilters filters, UUID schoolId) {
        return switch (filters.sessionFilterType()) {
            case NONE -> Mono.just(new ResolvedListSession(null, false));
            case CURRENT -> sessionRepository.findBySchoolIdAndIsCurrentTrue(schoolId)
                    .map(session -> new ResolvedListSession(session.getId(), false))
                    .defaultIfEmpty(new ResolvedListSession(null, true));
            case EXPLICIT -> sessionRepository.findByIdForUpdate(filters.sessionId())
                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                            "SESSION_NOT_FOUND",
                            "Academic session not found: " + filters.sessionId(),
                            "sessionId")))
                    .flatMap(session -> {
                        if (!schoolId.equals(session.getSchoolId())) {
                            return Mono.error(new SchoolFeeException(
                                    "SESSION_NOT_IN_SCHOOL",
                                    "Session does not belong to your school",
                                    "sessionId"));
                        }
                        return Mono.just(new ResolvedListSession(session.getId(), false));
                    });
        };
    }

    private Flux<ClassEntity> fetchClasses(
            UUID schoolId,
            UUID sessionId,
            String gradeLevel,
            ClassListStatus status) {
        boolean isActive = ClassListStatus.ACTIVE.equals(status);

        if (sessionId != null && gradeLevel != null) {
            return classRepository.findBySchoolIdAndAcademicSessionIdAndGradeLevelAndIsActive(
                    schoolId, sessionId, gradeLevel, isActive);
        } else if (sessionId != null) {
            return classRepository.findBySchoolIdAndAcademicSessionIdAndIsActive(
                    schoolId, sessionId, isActive);
        } else if (gradeLevel != null) {
            return classRepository.findBySchoolIdAndGradeLevelAndIsActive(
                    schoolId, gradeLevel, isActive);
        } else {
            return classRepository.findBySchoolIdAndIsActive(schoolId, isActive);
        }
    }

    private enum SessionFilterType {
        NONE,
        CURRENT,
        EXPLICIT
    }

    private enum ClassListStatus {
        ACTIVE,
        INACTIVE
    }

    private record ClassListFilters(
            SessionFilterType sessionFilterType,
            UUID sessionId,
            String gradeLevel,
            ClassListStatus status) {}

    private record ResolvedListSession(UUID sessionId, boolean noMatches) {}

    // ========================================================================
    // GET CLASS DETAILS
    // ========================================================================

    @Override
    public Mono<ClassDetailResponse> getClassDetails(UUID classId) {
        if (classId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_CLASS",
                    "Class id is required",
                    "classId"));
        }

        return jwtUtils.getCurrentUser()
                .flatMap(user -> transactionalOperator.transactional(
                        schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                .switchIfEmpty(Mono.error(new SchoolFeeException(
                                        "SCHOOL_NOT_FOUND", "School not found")))
                                .flatMap(school -> findClassInSchoolForUpdate(classId, school.getId()))
                                .flatMap(cls -> findClassSessionForDetails(cls, user.getSchoolId())
                                        .flatMap(session -> buildClassDetailResponse(
                                                cls,
                                                session,
                                                user.getSchoolId())))));
    }

    private Mono<AcademicSession> findClassSessionForDetails(ClassEntity cls, UUID schoolId) {
        return sessionRepository.findByIdForUpdate(cls.getAcademicSessionId())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_FOUND",
                        "Academic session not found: " + cls.getAcademicSessionId())))
                .flatMap(session -> {
                    if (!schoolId.equals(session.getSchoolId())) {
                        return Mono.error(new SchoolFeeException(
                                "SESSION_NOT_IN_SCHOOL",
                                "Session does not belong to your school"));
                    }
                    return Mono.just(session);
                });
    }

    // ========================================================================
    // UPDATE CLASS
    // ========================================================================

    @Override
    public Mono<UpdateClassResponse> updateClass(UUID classId, UpdateClassRequest request) {
        if (classId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_CLASS",
                    "Class id is required",
                    "classId"));
        }

        return Mono.defer(() -> {
            validateUpdateClassRequest(request);

            return jwtUtils.getCurrentUser()
                    .flatMap(user -> transactionalOperator.transactional(
                            schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "SCHOOL_NOT_FOUND", "School not found")))
                                    .flatMap(school -> findClassInSchoolForUpdate(classId, school.getId()))
                                    .flatMap(this::requireActiveClass)
                                    .flatMap(cls -> requireOpenClassSession(cls)
                                            .then(Mono.defer(() -> validateCapacityAndApplyClassUpdate(
                                                    cls,
                                                    request,
                                                    user.getUserId()))))
                                    .flatMap(this::saveUpdatedClass)));
        });
    }

    private void validateUpdateClassRequest(UpdateClassRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_UPDATE",
                    "Class update request is required");
        }
        if (request.name() == null
                && request.gradeLevel() == null
                && request.classTeacherId() == null
                && request.capacity() == null) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_UPDATE",
                    "At least one class field must be provided");
        }
        if (request.name() != null && request.name().isBlank()) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_UPDATE",
                    "Class name cannot be blank",
                    "name");
        }
        if (request.gradeLevel() != null && request.gradeLevel().isBlank()) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_UPDATE",
                    "Grade level cannot be blank",
                    "gradeLevel");
        }
        if (request.capacity() != null && request.capacity() < 1) {
            throw new SchoolFeeException(
                    "INVALID_CLASS_UPDATE",
                    "Capacity must be greater than 0",
                    "capacity");
        }
    }

    private Mono<ClassEntity> findClassInSchoolForUpdate(UUID classId, UUID schoolId) {
        return classRepository.findByIdAndSchoolIdForUpdate(classId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "CLASS_NOT_FOUND",
                        "Class not found or does not belong to your school")));
    }

    private Mono<ClassEntity> requireActiveClass(ClassEntity cls) {
        if (!Boolean.TRUE.equals(cls.getIsActive())) {
            return Mono.error(new SchoolFeeException(
                    "CLASS_NOT_ACTIVE",
                    "Inactive classes cannot be updated",
                    "classId"));
        }
        return Mono.just(cls);
    }

    private Mono<Void> requireOpenClassSession(ClassEntity cls) {
        return sessionRepository.findByIdForUpdate(cls.getAcademicSessionId())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_FOUND",
                        "Academic session not found: " + cls.getAcademicSessionId())))
                .filter(session -> cls.getSchoolId().equals(session.getSchoolId()))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_IN_SCHOOL",
                        "Session does not belong to your school")))
                .flatMap(session -> {
                    if (SESSION_STATUS_COMPLETED.equalsIgnoreCase(Objects.toString(session.getStatus(), ""))) {
                        return Mono.error(new SchoolFeeException(
                                "SESSION_ALREADY_CLOSED",
                                "Session is already closed and cannot be modified: " + session.getId(),
                                "academicSessionId"));
                    }
                    return Mono.empty();
                });
    }

    private Mono<ClassEntity> validateCapacityAndApplyClassUpdate(
            ClassEntity cls,
            UpdateClassRequest request,
            UUID updatedBy) {
        Mono<Long> currentEnrollment = request.capacity() == null
                ? Mono.just(0L)
                : studentRepository.countByCurrentClassId(cls.getId());

        return currentEnrollment.map(count -> {
            if (request.capacity() != null && request.capacity() < count) {
                throw new SchoolFeeException(
                        "CAPACITY_TOO_LOW",
                        "Cannot reduce capacity to " + request.capacity()
                                + ". Class has " + count + " students enrolled.",
                        "capacity");
            }

            if (request.name() != null) {
                cls.setName(request.name().trim());
            }
            if (request.gradeLevel() != null) {
                cls.setGradeLevel(request.gradeLevel().trim());
            }
            if (request.classTeacherId() != null) {
                cls.setClassTeacherId(request.classTeacherId());
            }
            if (request.capacity() != null) {
                cls.setCapacity(request.capacity());
            }
            cls.setUpdatedAt(Instant.now());
            cls.setUpdatedBy(updatedBy);
            return cls;
        });
    }

    private Mono<UpdateClassResponse> saveUpdatedClass(ClassEntity cls) {
        Instant updatedAt = cls.getUpdatedAt() != null ? cls.getUpdatedAt() : Instant.now();
        return classRepository.save(cls)
                .map(savedClass -> new UpdateClassResponse(
                        savedClass.getId(),
                        savedClass.getName(),
                        savedClass.getClassTeacherId() != null
                                ? savedClass.getClassTeacherId().toString()
                                : null,
                        savedClass.getCapacity(),
                        updatedAt))
                .onErrorMap(DuplicateKeyException.class, error -> duplicateClassException(
                        cls.getSchoolId(),
                        cls.getName(),
                        cls.getAcademicSessionId(),
                        error))
                .onErrorMap(OptimisticLockingFailureException.class, this::staleClassUpdateException);
    }

    private SchoolFeeException staleClassUpdateException(Throwable cause) {
        return new SchoolFeeException(
                "STALE_RESOURCE",
                "Class was modified by another request. Please reload and try again.",
                "version",
                cause);
    }

    // ========================================================================
    // DEACTIVATE CLASS
    // ========================================================================

    @Override
    public Mono<Void> deactivateClass(UUID classId) {
        if (classId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_CLASS",
                    "Class id is required",
                    "classId"));
        }

        return jwtUtils.getCurrentUser()
                .flatMap(user -> transactionalOperator.transactional(
                        schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                .switchIfEmpty(Mono.error(new SchoolFeeException(
                                        "SCHOOL_NOT_FOUND", "School not found")))
                                .flatMap(school -> findClassInSchoolForUpdate(classId, school.getId()))
                                .flatMap(this::requireActiveClass)
                                .flatMap(cls -> requireOpenClassSession(cls)
                                        .then(Mono.defer(() -> deactivateClassIfEmpty(
                                                cls,
                                                user.getUserId()))))))
                .then();
    }

    private Mono<ClassEntity> deactivateClassIfEmpty(ClassEntity cls, UUID updatedBy) {
        return studentRepository.countActiveByCurrentClassId(cls.getId())
                .flatMap(count -> {
                    if (count > 0) {
                        return Mono.error(new SchoolFeeException(
                                "CLASS_HAS_STUDENTS",
                                "Cannot deactivate class with " + count
                                        + " students enrolled. Transfer or promote students first.",
                                "classId"));
                    }

                    cls.setIsActive(false);
                    cls.setUpdatedAt(Instant.now());
                    cls.setUpdatedBy(updatedBy);
                    return classRepository.save(cls)
                            .onErrorMap(OptimisticLockingFailureException.class, this::staleClassUpdateException);
                });
    }

    // ========================================================================
    // PROMOTE STUDENTS
    // ========================================================================

    @Override
    public Mono<PromoteStudentsResponse> promoteStudents(PromoteStudentsRequest request) {
        return Mono.defer(() -> {
            validatePromoteStudentsRequest(request);
            List<UUID> studentIds = List.copyOf(request.studentIds());

            return jwtUtils.getCurrentUser()
                    .flatMap(user -> transactionalOperator.transactional(
                            schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "SCHOOL_NOT_FOUND", "School not found")))
                                    .flatMap(school -> findOpenPromotionSession(
                                            request.newSessionId(),
                                            school.getId()))
                                    .flatMap(targetSession -> lockPromotionClasses(
                                            request.fromClassId(),
                                            request.toClassId(),
                                            user.getSchoolId())
                                            .flatMap(classes -> validatePromotionClasses(
                                                    classes,
                                                    targetSession.getId())))
                                    .flatMap(classes -> lockPromotionCandidates(
                                            studentIds,
                                            classes.fromClass(),
                                            user.getSchoolId())
                                            .flatMap(candidates -> validatePromotionCapacity(
                                                    classes.toClass(),
                                                    candidates.studentsToPromote().size())
                                                    .then(savePromotedStudents(
                                                            candidates.studentsToPromote(),
                                                            classes.toClass().getId(),
                                                            user.getUserId()))
                                                    .thenReturn(buildPromoteStudentsResponse(
                                                            classes.fromClass(),
                                                            classes.toClass(),
                                                            candidates))))));
        });
    }

    private void validatePromoteStudentsRequest(PromoteStudentsRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_PROMOTION",
                    "Promotion request is required");
        }
        if (request.fromClassId() == null) {
            throw new SchoolFeeException(
                    "INVALID_PROMOTION",
                    "Source class ID is required",
                    "fromClassId");
        }
        if (request.toClassId() == null) {
            throw new SchoolFeeException(
                    "INVALID_PROMOTION",
                    "Target class ID is required",
                    "toClassId");
        }
        if (request.fromClassId().equals(request.toClassId())) {
            throw new SchoolFeeException(
                    "INVALID_PROMOTION",
                    "Source and target classes must be different",
                    "toClassId");
        }
        if (request.newSessionId() == null) {
            throw new SchoolFeeException(
                    "INVALID_PROMOTION",
                    "New session ID is required",
                    "newSessionId");
        }
        if (request.studentIds() == null || request.studentIds().isEmpty()) {
            throw new SchoolFeeException(
                    "INVALID_PROMOTION",
                    "At least one student must be selected",
                    "studentIds");
        }
        if (request.studentIds().stream().anyMatch(Objects::isNull)) {
            throw new SchoolFeeException(
                    "INVALID_PROMOTION",
                    "Student IDs cannot contain null values",
                    "studentIds");
        }
        if (new LinkedHashSet<>(request.studentIds()).size() != request.studentIds().size()) {
            throw new SchoolFeeException(
                    "DUPLICATE_STUDENTS",
                    "Student IDs must be unique",
                    "studentIds");
        }
    }

    private Mono<AcademicSession> findOpenPromotionSession(UUID sessionId, UUID schoolId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_FOUND",
                        "Academic session not found: " + sessionId)))
                .flatMap(session -> {
                    if (!schoolId.equals(session.getSchoolId())) {
                        return Mono.error(new SchoolFeeException(
                                "SESSION_NOT_IN_SCHOOL",
                                "Session does not belong to your school"));
                    }
                    if (SESSION_STATUS_COMPLETED.equalsIgnoreCase(Objects.toString(session.getStatus(), ""))) {
                        return Mono.error(new SchoolFeeException(
                                "SESSION_ALREADY_CLOSED",
                                "Session is already closed and cannot receive promoted students: " + session.getId(),
                                "newSessionId"));
                    }
                    return Mono.just(session);
                });
    }

    private Mono<PromotionClasses> lockPromotionClasses(UUID fromClassId, UUID toClassId, UUID schoolId) {
        List<UUID> lockOrder = List.of(fromClassId, toClassId).stream()
                .sorted()
                .toList();

        return lockPromotionClass(lockOrder.get(0), fromClassId, toClassId, schoolId)
                .flatMap(firstClass -> lockPromotionClass(lockOrder.get(1), fromClassId, toClassId, schoolId)
                        .map(secondClass -> {
                            ClassEntity fromClass = fromClassId.equals(firstClass.getId())
                                    ? firstClass
                                    : secondClass;
                            ClassEntity toClass = toClassId.equals(firstClass.getId())
                                    ? firstClass
                                    : secondClass;
                            return new PromotionClasses(fromClass, toClass);
                        }));
    }

    private Mono<ClassEntity> lockPromotionClass(
            UUID classId,
            UUID fromClassId,
            UUID toClassId,
            UUID schoolId) {
        return classRepository.findByIdAndSchoolIdForUpdate(classId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "CLASS_NOT_FOUND",
                        (classId.equals(fromClassId) ? "Source" : "Target")
                                + " class not found or does not belong to your school: "
                                + (classId.equals(fromClassId) ? fromClassId : toClassId),
                        classId.equals(fromClassId) ? "fromClassId" : "toClassId")));
    }

    private Mono<PromotionClasses> validatePromotionClasses(PromotionClasses classes, UUID newSessionId) {
        if (!Boolean.TRUE.equals(classes.fromClass().getIsActive())) {
            return Mono.error(new SchoolFeeException(
                    "SOURCE_CLASS_NOT_ACTIVE",
                    "Inactive source classes cannot be used for promotion",
                    "fromClassId"));
        }
        if (!Boolean.TRUE.equals(classes.toClass().getIsActive())) {
            return Mono.error(new SchoolFeeException(
                    "TARGET_CLASS_NOT_ACTIVE",
                    "Inactive target classes cannot receive promoted students",
                    "toClassId"));
        }
        if (!newSessionId.equals(classes.toClass().getAcademicSessionId())) {
            return Mono.error(new SchoolFeeException(
                    "TARGET_CLASS_SESSION_MISMATCH",
                    "Target class does not belong to the new session",
                    "newSessionId"));
        }
        return Mono.just(classes);
    }

    private Mono<PromotionCandidates> lockPromotionCandidates(
            List<UUID> studentIds,
            ClassEntity fromClass,
            UUID schoolId) {
        List<UUID> lockOrder = studentIds.stream()
                .sorted()
                .toList();

        return Flux.fromIterable(lockOrder)
                .concatMap(studentId -> lockPromotionCandidate(studentId, fromClass, schoolId))
                .collectMap(PromotionCandidate::studentId)
                .map(candidatesById -> orderPromotionCandidates(studentIds, candidatesById));
    }

    private Mono<PromotionCandidate> lockPromotionCandidate(UUID studentId, ClassEntity fromClass, UUID schoolId) {
        return studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(studentId, schoolId)
                .map(student -> {
                    if (!fromClass.getId().equals(student.getCurrentClassId())) {
                        return PromotionCandidate.failed(
                                studentId,
                                "Student is not in the source class");
                    }
                    return PromotionCandidate.success(studentId, student);
                })
                .switchIfEmpty(Mono.just(PromotionCandidate.failed(
                        studentId,
                        "Student not found or does not belong to your school")));
    }

    private PromotionCandidates orderPromotionCandidates(
            List<UUID> studentIds,
            Map<UUID, PromotionCandidate> candidatesById) {
        List<Student> studentsToPromote = new ArrayList<>();
        List<PromoteStudentsResponse.FailedPromotion> failures = new ArrayList<>();

        for (UUID studentId : studentIds) {
            PromotionCandidate candidate = candidatesById.get(studentId);
            if (candidate.student() != null) {
                studentsToPromote.add(candidate.student());
            } else {
                failures.add(new PromoteStudentsResponse.FailedPromotion(
                        studentId,
                        candidate.failureReason()));
            }
        }

        return new PromotionCandidates(studentsToPromote, failures);
    }

    private Mono<Void> validatePromotionCapacity(ClassEntity toClass, int studentsToPromote) {
        if (studentsToPromote == 0) {
            return Mono.empty();
        }

        return studentRepository.countActiveByCurrentClassId(toClass.getId())
                .flatMap(targetCount -> {
                    long capacity = toClass.getCapacity() == null
                            ? DEFAULT_CLASS_CAPACITY
                            : toClass.getCapacity();
                    long available = capacity - targetCount;
                    if (studentsToPromote > available) {
                        return Mono.error(new SchoolFeeException(
                                "INSUFFICIENT_CAPACITY",
                                "Target class only has " + Math.max(0, available)
                                        + " spots available. Trying to promote "
                                        + studentsToPromote + " students.",
                                "studentIds"));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> savePromotedStudents(List<Student> students, UUID toClassId, UUID updatedBy) {
        Instant updatedAt = Instant.now();
        return Flux.fromIterable(students)
                .concatMap(student -> {
                    student.setCurrentClassId(toClassId);
                    student.setUpdatedAt(updatedAt);
                    student.setUpdatedBy(updatedBy);
                    return studentRepository.save(student)
                            .onErrorMap(OptimisticLockingFailureException.class,
                                    error -> staleStudentPromotionException(student.getId(), error));
                })
                .then();
    }

    private SchoolFeeException staleStudentPromotionException(UUID studentId, Throwable cause) {
        return new SchoolFeeException(
                "STALE_RESOURCE",
                "Student was modified by another request during promotion: " + studentId,
                "studentIds",
                cause);
    }

    private PromoteStudentsResponse buildPromoteStudentsResponse(
            ClassEntity fromClass,
            ClassEntity toClass,
            PromotionCandidates candidates) {
        int successCount = candidates.studentsToPromote().size();
        List<PromoteStudentsResponse.FailedPromotion> failures = candidates.failedPromotions();
        return new PromoteStudentsResponse(
                UUID.randomUUID(),
                fromClass.getName(),
                toClass.getName(),
                successCount,
                failures,
                successCount + " students promoted to " + toClass.getName()
                        + (failures.isEmpty() ? "" : ". " + failures.size() + " failed."));
    }

    private record PromotionClasses(ClassEntity fromClass, ClassEntity toClass) {}

    private record PromotionCandidates(
            List<Student> studentsToPromote,
            List<PromoteStudentsResponse.FailedPromotion> failedPromotions) {}

    private record PromotionCandidate(
            UUID studentId,
            Student student,
            String failureReason) {

        private static PromotionCandidate success(UUID studentId, Student student) {
            return new PromotionCandidate(studentId, student, null);
        }

        private static PromotionCandidate failed(UUID studentId, String failureReason) {
            return new PromotionCandidate(studentId, null, failureReason);
        }
    }

    // ========================================================================
    // RESPONSE BUILDERS
    // ========================================================================

    private ClassResponse buildClassResponse(ClassEntity cls, String sessionName) {
    return new ClassResponse(
        cls.getId(),
        cls.getName(),
        cls.getGradeLevel(),
        cls.getSection(),
        sessionName,
        cls.getClassTeacherId() != null
            ? new ClassResponse.ClassTeacher(cls.getClassTeacherId(), null)
            : null,
        cls.getCapacity(),
        0,
        cls.getCapacity(),
        Collections.emptyList(),
        Boolean.TRUE.equals(cls.getIsActive()) ? "ACTIVE" : "INACTIVE",
        cls.getCreatedAt());
    }

    /**
     * Enrich class response with enrollment data.
     */
    private Mono<ClassResponse> enrichClassResponse(ClassEntity basic) {
        UUID classId = basic.getId();
        int capacity = basic.getCapacity() == null ? DEFAULT_CLASS_CAPACITY : basic.getCapacity();

        Mono<AcademicSession> session = sessionRepository.findById(basic.getAcademicSessionId())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_FOUND",
                        "Academic session not found: " + basic.getAcademicSessionId())));

        return Mono.zip(session, studentRepository.countActiveByCurrentClassId(classId))
                .map(tuple -> {
                    int enrollment = tuple.getT2().intValue();
                    return new ClassResponse(
                            basic.getId(),
                            basic.getName(),
                            basic.getGradeLevel(),
                            basic.getSection(),
                            tuple.getT1().getName(),
                            basic.getClassTeacherId() != null
                                    ? new ClassResponse.ClassTeacher(basic.getClassTeacherId(), null)
                                    : null,
                            capacity,
                            enrollment,
                            Math.max(0, capacity - enrollment),
                            Collections.emptyList(),
                            Boolean.TRUE.equals(basic.getIsActive()) ? "ACTIVE" : "INACTIVE",
                            basic.getCreatedAt());
                });
    }

    private Mono<Optional<String>> findPrimaryGuardianPhone(UUID studentId) {
        return guardianLinkRepository.findActivePrimaryByStudentId(studentId)
                .next()
                .flatMap(link -> guardianRepository.findByIdAndDeletedAtIsNull(link.getGuardianId()))
                .map(guardian -> Optional.ofNullable(guardian.getPhone()))
                .defaultIfEmpty(Optional.empty());
    }

    /**
     * Build full class detail response with students and statistics.
     */
    private Mono<ClassDetailResponse> buildClassDetailResponse(
            ClassEntity cls, AcademicSession session, UUID schoolId) {
        int capacity = cls.getCapacity() == null ? DEFAULT_CLASS_CAPACITY : cls.getCapacity();

        return studentRepository.findActiveBySchoolIdAndCurrentClassId(schoolId, cls.getId())
                .flatMap(s -> findPrimaryGuardianPhone(s.getId())
                        .map(optPhone -> new ClassDetailResponse.StudentSummary(
                                s.getId(),
                                s.getAdmissionNumber(),
                                s.getFirstName(),
                                s.getLastName(),
                                s.getGender(),
                                optPhone.orElse(null),
                                null // feeStatus
                        ))
                )
                .collectList()
                .map(studentSummaries -> {
                    int maleCount = (int) studentSummaries.stream()
                            .filter(s -> "MALE".equalsIgnoreCase(s.gender())).count();
                    int femaleCount = studentSummaries.size() - maleCount;

                    ClassDetailResponse.ClassStatistics stats = new
                            ClassDetailResponse.ClassStatistics(
                                    maleCount,
                                    femaleCount,
                                    0, // TODO: Phase 2
                                    studentSummaries.size() // TODO: Phase 2
                    );

                    return new ClassDetailResponse(
                            cls.getId(),
                            cls.getName(),
                            cls.getGradeLevel(),
                            cls.getSection(),
                            session.getName(),
                            cls.getClassTeacherId() != null
                                    ? new ClassDetailResponse.ClassTeacher(
                                            cls.getClassTeacherId(), null, null, null)
                                    : null,
                            capacity,
                            studentSummaries.size(),
                            studentSummaries,
                            stats,
                            cls.getCreatedAt()
                            );
                });
    }
}
