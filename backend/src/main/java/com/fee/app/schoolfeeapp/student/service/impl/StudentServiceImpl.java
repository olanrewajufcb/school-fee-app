package com.fee.app.schoolfeeapp.student.service.impl;


import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.utils.PhoneNumberNormalizer;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.response.*;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.dto.request.EnrollStudentRequest;
import com.fee.app.schoolfeeapp.student.repository.SchoolStudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final AcademicSessionRepository sessionRepository;
    private final SchoolRepository schoolRepository;
    private final StudentGuardianRepository guardianRepository;
    private final SchoolStudentGuardianLinkRepository guardianLinkRepository;
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;

    // ========================================================================
    // ENROLL STUDENT
    // ========================================================================

    @Override
    public Mono<EnrollStudentResponse> enrollStudent(EnrollStudentRequest request) {
        return Mono.fromCallable(() -> validateAndNormalizeEnrollRequest(request))
                .flatMap(normalizedRequest -> jwtUtils.getCurrentUser()
                        .flatMap(adminUser -> {
                            UUID schoolId = adminUser.getSchoolId();
                            UUID userId = adminUser.getUserId();

                            return schoolRepository.findByIdAndIsActiveTrue(schoolId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "SCHOOL_NOT_FOUND", "School not found")))
                                    .then(Mono.defer(() -> transactionalOperator.transactional(
                                            classRepository.findByIdAndSchoolIdForUpdate(
                                                            normalizedRequest.classId(), schoolId)
                                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                                            "CLASS_NOT_FOUND",
                                                            "Class not found or does not belong to your school",
                                                            "classId")))
                                                    .flatMap(cls -> validateClassCanReceiveStudent(cls, schoolId)
                                                            .then(Mono.defer(() -> validateClassCapacity(cls)))
                                                            .then(Mono.defer(() -> saveEnrollment(
                                                                    normalizedRequest, schoolId, userId, cls))))
                                    )))
                                    .flatMap(enrollment -> createGuardiansIfPresent(
                                            enrollment.student(),
                                            normalizedRequest.guardians(),
                                            schoolId,
                                            userId)
                                            .thenReturn(buildEnrollResponse(
                                                    enrollment.student(),
                                                    enrollment.cls(),
                                                    normalizedRequest.guardians())));
                        }));
    }

    private Mono<Enrollment> saveEnrollment(
            EnrollStudentRequest request, UUID schoolId, UUID createdBy, ClassEntity cls) {

        return generateAdmissionNumber(schoolId)
                .flatMap(admissionNumber -> {
                    Instant now = Instant.now();
                    Student student = Student.builder()
                            .id(UUID.randomUUID())
                            .schoolId(schoolId)
                            .admissionNumber(admissionNumber)
                            .firstName(request.firstName())
                            .lastName(request.lastName())
                            .middleName(request.middleName())
                            .gender(request.gender().toUpperCase())
                            .dateOfBirth(request.dateOfBirth())
                            .currentClassId(request.classId())
                            .enrollmentDate(LocalDate.now())
                            .enrollmentStatus("ACTIVE")
                            .medicalNotes(request.medicalNotes())
                            .createdAt(now)
                            .updatedAt(now)
                            .updatedBy(createdBy)
                            .build();

                    return studentRepository.save(student)
                            .onErrorMap(DuplicateKeyException.class,
                                    this::duplicateAdmissionNumberException)
                            .map(savedStudent -> new Enrollment(savedStudent, cls));
                });
    }

    private EnrollStudentRequest validateAndNormalizeEnrollRequest(EnrollStudentRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_STUDENT_ENROLLMENT",
                    "Student enrollment request is required");
        }

        String firstName = requireText(request.firstName(), "firstName", "First name is required");
        String lastName = requireText(request.lastName(), "lastName", "Last name is required");
        String gender = requireText(request.gender(), "gender", "Gender is required")
                .toUpperCase(Locale.ROOT);
        if (!Set.of("MALE", "FEMALE").contains(gender)) {
            throw new SchoolFeeException(
                    "INVALID_STUDENT_ENROLLMENT",
                    "Gender must be MALE or FEMALE",
                    "gender");
        }
        if (request.classId() == null) {
            throw new SchoolFeeException(
                    "INVALID_STUDENT_ENROLLMENT",
                    "Class ID is required",
                    "classId");
        }
        if (request.dateOfBirth() != null && request.dateOfBirth().isAfter(LocalDate.now())) {
            throw new SchoolFeeException(
                    "INVALID_STUDENT_ENROLLMENT",
                    "Date of birth cannot be in the future",
                    "dateOfBirth");
        }

        List<EnrollStudentRequest.GuardianInfo> guardians = normalizeGuardians(request.guardians());
        return new EnrollStudentRequest(
                firstName,
                lastName,
                trimToNull(request.middleName()),
                gender,
                request.dateOfBirth(),
                request.classId(),
                guardians,
                trimToNull(request.medicalNotes()));
    }

    private String requireText(String value, String field, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new SchoolFeeException("INVALID_STUDENT_ENROLLMENT", message, field);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<EnrollStudentRequest.GuardianInfo> normalizeGuardians(
            List<EnrollStudentRequest.GuardianInfo> guardians) {
        if (guardians == null || guardians.isEmpty()) {
            return List.of();
        }

        long primaryContacts = guardians.stream()
                .filter(Objects::nonNull)
                .filter(EnrollStudentRequest.GuardianInfo::isPrimaryContact)
                .count();
        if (primaryContacts > 1) {
            throw new SchoolFeeException(
                    "DUPLICATE_PRIMARY_GUARDIAN",
                    "Only one guardian can be the primary contact",
                    "guardians");
        }

        List<EnrollStudentRequest.GuardianInfo> normalizedGuardians = new ArrayList<>();
        for (int index = 0; index < guardians.size(); index++) {
            EnrollStudentRequest.GuardianInfo guardian = guardians.get(index);
            if (guardian == null) {
                throw new SchoolFeeException(
                        "INVALID_GUARDIAN",
                        "Guardian entry is required",
                        "guardians[" + index + "]");
            }

            String fieldPrefix = "guardians[" + index + "].";
            String normalizedPhone;
            try {
                normalizedPhone = PhoneNumberNormalizer.normalize(guardian.phone());
            } catch (IllegalArgumentException error) {
                throw new SchoolFeeException(
                        "INVALID_GUARDIAN_PHONE",
                        error.getMessage(),
                        fieldPrefix + "phone");
            }
            if (normalizedPhone == null) {
                throw new SchoolFeeException(
                        "INVALID_GUARDIAN_PHONE",
                        "Guardian phone is required",
                        fieldPrefix + "phone");
            }

            normalizedGuardians.add(new EnrollStudentRequest.GuardianInfo(
                    requireText(guardian.firstName(), fieldPrefix + "firstName", "Guardian first name is required"),
                    requireText(guardian.lastName(), fieldPrefix + "lastName", "Guardian last name is required"),
                    normalizedPhone,
                    trimToNull(guardian.email()),
                    requireText(guardian.relationship(), fieldPrefix + "relationship", "Relationship is required"),
                    guardian.isPrimaryContact(),
                    guardian.canPickUpChild(),
                    guardian.canViewFees(),
                    guardian.canViewResults(),
                    guardian.canViewAttendance(),
                    guardian.canReceiveSms(),
                    guardian.contactPriority()));
        }
        return List.copyOf(normalizedGuardians);
    }

    private Mono<Void> validateClassCanReceiveStudent(ClassEntity cls, UUID schoolId) {
        if (!Boolean.TRUE.equals(cls.getIsActive())) {
            return Mono.error(new SchoolFeeException(
                    "CLASS_INACTIVE",
                    "Inactive classes cannot receive new students",
                    "classId"));
        }
        if (cls.getAcademicSessionId() == null) {
            return Mono.error(new SchoolFeeException(
                    "SESSION_NOT_FOUND",
                    "Class has no academic session",
                    "classId"));
        }

        return sessionRepository.findByIdAndDeletedAtIsNull(cls.getAcademicSessionId())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "SESSION_NOT_FOUND",
                        "Academic session not found",
                        "academicSessionId")))
                .flatMap(session -> validateSessionCanReceiveStudent(session, schoolId));
    }

    private Mono<Void> validateSessionCanReceiveStudent(AcademicSession session, UUID schoolId) {
        if (!schoolId.equals(session.getSchoolId())) {
            return Mono.error(new SchoolFeeException(
                    "SESSION_NOT_IN_SCHOOL",
                    "Academic session does not belong to your school",
                    "academicSessionId"));
        }
        if (session.getDeletedAt() != null) {
            return Mono.error(new SchoolFeeException(
                    "SESSION_NOT_FOUND",
                    "Academic session not found",
                    "academicSessionId"));
        }
        if ("COMPLETED".equalsIgnoreCase(Objects.toString(session.getStatus(), ""))
                || session.getClosedAt() != null) {
            return Mono.error(new SchoolFeeException(
                    "SESSION_ALREADY_CLOSED",
                    "Session is already closed and cannot receive new students: " + session.getId(),
                    "academicSessionId"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateClassCapacity(ClassEntity cls) {
        int capacity = Optional.ofNullable(cls.getCapacity()).orElse(0);
        return studentRepository.countActiveByCurrentClassId(cls.getId())
                .flatMap(currentCount -> {
                    if (currentCount >= capacity) {
                        return Mono.error(new SchoolFeeException(
                                "CLASS_FULL",
                                "Class " + cls.getName() + " is full (" + currentCount + "/" + capacity + ")",
                                "classId"));
                    }
                    return Mono.empty();
                });
    }

    private SchoolFeeException duplicateAdmissionNumberException(Throwable cause) {
        return new SchoolFeeException(
                "DUPLICATE_ADMISSION_NUMBER",
                "Admission number already exists. Please retry enrollment.",
                "admissionNumber",
                cause);
    }

    // ========================================================================
    // LIST STUDENTS
    // ========================================================================

    @Override
    public Mono<PageResponse<StudentListResponse>> listStudents(
            UUID classId, String status, String search, Pageable pageable) {

        return Mono.fromCallable(() -> validateAndNormalizeListRequest(status, search, pageable))
                .flatMap(criteria -> jwtUtils.getCurrentUser()
                        .flatMap(user -> {
                            UUID schoolId = user.getSchoolId();

                            Flux<Student> studentsFlux = studentRepository.findBySchoolIdWithFilters(
                                    schoolId, classId, criteria.isActive(), criteria.search(),
                                    criteria.size(), criteria.offset());

                            Mono<Long> countMono = studentRepository.countBySchoolIdWithFilters(
                                    schoolId, classId, criteria.isActive(), criteria.search());

                            return studentsFlux
                                    .concatMap(this::toStudentListResponse)
                                    .collectList()
                                    .zipWith(countMono)
                                    .map(tuple -> {
                                        List<StudentListResponse> content = tuple.getT1();
                                        long totalElements = tuple.getT2();
                                        return new PageResponse<>(
                                                content,
                                                criteria.page(),
                                                criteria.size(),
                                                totalElements,
                                                calculateTotalPages(totalElements, criteria.size()));
                                    });
                        }));
    }

    // ========================================================================
    // GET STUDENT DETAILS
    // ========================================================================

    @Override
    public Mono<StudentDetailResponse> getStudentDetails(UUID studentId) {
        return Mono.fromCallable(() -> requireStudentId(studentId))
                .flatMap(id -> jwtUtils.getCurrentUser()
                        .flatMap(user -> {
                            UUID schoolId = user.getSchoolId();

                            if (user.isParent()) {
                                return studentRepository.findByIdAndDeletedAtIsNull(id)
                                        .switchIfEmpty(studentNotFound())
                                        .flatMap(student -> validateParentCanViewStudent(user.getUserId(), id)
                                                .thenReturn(student));
                            }

                            return studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(id, schoolId)
                                    .switchIfEmpty(studentNotFound());
                        }))
                .flatMap(this::toStudentDetailResponse);
    }

    // ========================================================================
    // GET MY CHILDREN
    // ========================================================================

    @Override
    public Mono<List<MyChildrenResponse>> getMyChildren() {
        return jwtUtils.getCurrentUser()
                .flatMapMany(parentUser -> {
                    if (!parentUser.isParent() || parentUser.getUserId() == null) {
                        return Flux.error(new SchoolFeeException(
                                "ACCESS_DENIED", "Only parents can view their children"));
                    }

                    UUID userId = parentUser.getUserId();

                    return guardianRepository.findByUserIdAndDeletedAtIsNull(userId)
                            .flatMapMany(guardian ->
                                    guardianLinkRepository
                                            .findByGuardianIdAndDeletedAtIsNull(guardian.getId()))
                            .concatMap(link ->
                                    studentRepository.findByIdAndDeletedAtIsNull(link.getStudentId())
                                            .flatMap(student ->
                                                    findClass(student.getCurrentClassId(), student.getSchoolId())
                                                            .map(optionalClass -> toMyChildrenResponse(
                                                                    student,
                                                                    optionalClass.orElse(null),
                                                                    link))));
                })
                .collectSortedList(Comparator
                        .comparing(MyChildrenResponse::lastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(MyChildrenResponse::firstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(MyChildrenResponse::admissionNumber, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
    }

    @Override
    public Mono<UpdateStudentResponse> updateStudent(UUID studentId, UpdateStudentRequest request) {
        return Mono.fromCallable(() -> validateAndNormalizeUpdateStudentRequest(studentId, request))
                .flatMap(normalized -> jwtUtils.getCurrentUser()
                        .flatMap(adminUser -> {
                    UUID schoolId = adminUser.getSchoolId();
                    UUID updatedBy = adminUser.getUserId();

                    return transactionalOperator.transactional(
                                    studentRepository
                                            .findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(
                                                    normalized.studentId(), schoolId)
                                            .switchIfEmpty(studentNotFound())
                                            .flatMap(student -> resolveTargetClassForUpdate(
                                                    student, normalized.request(), schoolId)
                                                    .flatMap(targetClass -> applyUpdates(
                                                            student,
                                                            normalized.request(),
                                                            targetClass.orElse(null),
                                                            updatedBy)))
                                            .flatMap(studentRepository::save)
                                            .onErrorMap(OptimisticLockingFailureException.class,
                                                    this::staleStudentUpdateException))
                            .flatMap(savedStudent -> toUpdateStudentResponse(savedStudent, schoolId));
                }));
    }

    private Mono<Optional<ClassEntity>> resolveTargetClassForUpdate(
            Student student, UpdateStudentRequest request, UUID schoolId) {
        UUID targetClassId = request.currentClassId();
        if (targetClassId == null || targetClassId.equals(student.getCurrentClassId())) {
            return Mono.just(Optional.empty());
        }

        return classRepository.findByIdAndSchoolIdForUpdate(targetClassId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "CLASS_NOT_FOUND",
                        "Target class not found: " + targetClassId,
                        "currentClassId")))
                .flatMap(cls -> validateClassCanReceiveStudent(cls, schoolId)
                        .then(Mono.defer(() -> validateClassCapacity(cls)))
                        .thenReturn(Optional.of(cls)));
    }

    private Mono<Student> applyUpdates(
            Student student, UpdateStudentRequest request, ClassEntity newClass, UUID updatedBy) {
        if (request.firstName() != null) {
            student.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            student.setLastName(request.lastName());
        }
        if (request.middleName() != null) {
            student.setMiddleName(request.middleName().isBlank() ? null : request.middleName());
        }
        if (request.gender() != null) {
            student.setGender(request.gender());
        }
        if (request.dateOfBirth() != null) {
            student.setDateOfBirth(request.dateOfBirth());
        }
        if (request.medicalNotes() != null) {
            student.setMedicalNotes(request.medicalNotes().isBlank() ? null : request.medicalNotes());
        }
        if (request.enrollmentStatus() != null) {
            student.setEnrollmentStatus(request.enrollmentStatus());
        }
        if (newClass != null) {
            student.setCurrentClassId(newClass.getId());
        }
        student.setUpdatedAt(Instant.now());
        student.setUpdatedBy(updatedBy);
        return Mono.just(student);
    }

    private Mono<UpdateStudentResponse> toUpdateStudentResponse(Student savedStudent, UUID schoolId) {
        return findClass(savedStudent.getCurrentClassId(), schoolId)
                .map(currentClass -> new UpdateStudentResponse(
                        savedStudent.getId(),
                        savedStudent.getAdmissionNumber(),
                        savedStudent.getFirstName(),
                        savedStudent.getLastName(),
                        savedStudent.getCurrentClassId(),
                        currentClass.map(ClassEntity::getName).orElse(null),
                        savedStudent.getEnrollmentStatus(),
                        savedStudent.getUpdatedAt()));
    }

    private ValidatedUpdateStudentRequest validateAndNormalizeUpdateStudentRequest(
            UUID studentId, UpdateStudentRequest request) {
        UUID id = requireStudentId(studentId);
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_STUDENT_UPDATE",
                    "Student update request is required");
        }

        if (request.dateOfBirth() != null && request.dateOfBirth().isAfter(LocalDate.now())) {
            throw new SchoolFeeException(
                    "INVALID_DATE_OF_BIRTH",
                    "Date of birth cannot be in the future",
                    "dateOfBirth");
        }

        UpdateStudentRequest normalizedRequest = new UpdateStudentRequest(
                normalizeRequiredUpdateText(request.firstName(), "firstName", "First name cannot be blank"),
                normalizeClearableText(request.middleName()),
                normalizeRequiredUpdateText(request.lastName(), "lastName", "Last name cannot be blank"),
                normalizeUpdateGender(request.gender()),
                request.dateOfBirth(),
                request.currentClassId(),
                normalizeEnrollmentStatus(request.enrollmentStatus()),
                normalizeClearableText(request.medicalNotes()));
        return new ValidatedUpdateStudentRequest(id, normalizedRequest);
    }

    private String normalizeRequiredUpdateText(String value, String field, String message) {
        if (value == null) {
            return null;
        }
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new SchoolFeeException("INVALID_STUDENT_UPDATE", message, field);
        }
        return trimmed;
    }

    private String normalizeClearableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private String normalizeUpdateGender(String gender) {
        if (gender == null) {
            return null;
        }
        String normalized = trimToNull(gender);
        if (normalized == null) {
            throw new SchoolFeeException(
                    "INVALID_GENDER",
                    "Gender must be MALE or FEMALE",
                    "gender");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!Set.of("MALE", "FEMALE").contains(normalized)) {
            throw new SchoolFeeException(
                    "INVALID_GENDER",
                    "Gender must be MALE or FEMALE",
                    "gender");
        }
        return normalized;
    }

    private String normalizeEnrollmentStatus(String status) {
        if (status == null) {
            return null;
        }
        String normalized = trimToNull(status);
        if (normalized == null) {
            throw new SchoolFeeException(
                    "INVALID_STATUS",
                    "Enrollment status must be one of ACTIVE, GRADUATED, TRANSFERRED, SUSPENDED, or WITHDRAWN",
                    "enrollmentStatus");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "GRADUATED", "TRANSFERRED", "SUSPENDED", "WITHDRAWN").contains(normalized)) {
            throw new SchoolFeeException(
                    "INVALID_STATUS",
                    "Enrollment status must be one of ACTIVE, GRADUATED, TRANSFERRED, SUSPENDED, or WITHDRAWN",
                    "enrollmentStatus");
        }
        return normalized;
    }

    private SchoolFeeException staleStudentUpdateException(Throwable cause) {
        return new SchoolFeeException(
                "STALE_RESOURCE",
                "Student was updated by another request. Please reload and try again.",
                null,
                cause);
    }

    // ========================================================================
    // PRIVATE HELPERS — Enrollment
    // ========================================================================

    private Mono<String> generateAdmissionNumber(UUID schoolId) {
        return studentRepository.countBySchoolIdAndDeletedAtIsNull(schoolId)
                .map(count -> {
                    String year = String.valueOf(LocalDate.now().getYear()).substring(2);
                    String random = UUID.randomUUID().toString()
                            .substring(0, 4)
                            .toUpperCase(Locale.ROOT);
                    return String.format("STU%s%04d%s", year, count + 1, random);
                });
    }

    private Mono<Void> createGuardiansIfPresent(
            Student student,
            List<EnrollStudentRequest.GuardianInfo> guardians,
            UUID schoolId,
            UUID createdBy) {
        if (guardians == null || guardians.isEmpty()) {
            return Mono.empty();
        }
        return createGuardians(student, guardians, schoolId, createdBy);
    }

    private Mono<Void> createGuardians(
            Student student,
            List<EnrollStudentRequest.GuardianInfo> guardians,
            UUID schoolId,
            UUID createdBy) {

        return Flux.fromIterable(guardians)
                .concatMap(guardianInfo -> {
                    String normalizedPhone = PhoneNumberNormalizer.normalize(guardianInfo.phone());

                    return guardianRepository
                            .findByPhoneAndSchoolIdAndDeletedAtIsNull(normalizedPhone, schoolId)
                            .switchIfEmpty(Mono.defer(() -> {
                                StudentGuardian newGuardian = StudentGuardian.builder()
                                        .schoolId(schoolId)
                                        .firstName(guardianInfo.firstName())
                                        .lastName(guardianInfo.lastName())
                                        .phone(normalizedPhone)
                                        .email(guardianInfo.email())
                                        .preferredContactMethod(
                                                guardianInfo.canReceiveSms() ? "SMS" : null)
                                        .isActive(true)
                                        .createdBy(createdBy)
                                        .updatedBy(createdBy)
                                        .build();
                                return guardianRepository.save(newGuardian);
                            }))
                            .flatMap(guardian ->
                                    createGuardianLink(guardian, student, guardianInfo, schoolId, createdBy));
                })
                .then()
                .onErrorResume(DuplicateKeyException.class, e -> {
                    log.warn("Duplicate guardian link for student {}", student.getId());
                    return Mono.empty();
                });
    }

    private Mono<StudentGuardianLink> createGuardianLink(
            StudentGuardian guardian,
            Student student,
            EnrollStudentRequest.GuardianInfo info,
            UUID schoolId,
            UUID createdBy) {

        return guardianLinkRepository
                .findByGuardianIdAndStudentId(guardian.getId(), student.getId())
                .switchIfEmpty(Mono.defer(() -> {
                    Mono<Void> demoteExistingPrimary = Mono.empty();
                    if (info.isPrimaryContact()) {
                        demoteExistingPrimary = guardianLinkRepository
                                .findByStudentIdAndIsPrimaryContactTrue(student.getId())
                                .flatMap(existingPrimary -> {
                                    existingPrimary.setIsPrimaryContact(false);
                                    return guardianLinkRepository.save(existingPrimary);
                                })
                                .then();
                    }

                    StudentGuardianLink link = StudentGuardianLink.builder()
                            .id(UUID.randomUUID())
                            .guardianId(guardian.getId())
                            .studentId(student.getId())
                            .schoolId(schoolId)
                            .relationship(info.relationship().toUpperCase())
                            .isPrimaryContact(info.isPrimaryContact())
                            .canPickUpChild(info.canPickUpChild())
                            .canViewFees(info.canViewFees())
                            .canViewResults(info.canViewResults())
                            .canViewAttendance(info.canViewAttendance())
                            .canReceiveSms(info.canReceiveSms())
                            .contactPriority(info.contactPriority() > 0
                                    ? info.contactPriority() : 1)
                            .createdBy(createdBy)
                            .updatedBy(createdBy)
                            .build();

                    return demoteExistingPrimary.then(guardianLinkRepository.save(link));
                }));
    }

    // ========================================================================
    // PRIVATE HELPERS — Response Building
    // ========================================================================

    private EnrollStudentResponse buildEnrollResponse(
            Student student, ClassEntity cls,
            List<EnrollStudentRequest.GuardianInfo> guardians) {

        return new EnrollStudentResponse(
                student.getId(),
                student.getAdmissionNumber(),
                student.getFirstName(),
                student.getLastName(),
                cls.getId(),
                cls.getName(),
                guardians != null && !guardians.isEmpty(),
                null, // parentUserIds populated in Phase 2 when Keycloak accounts exist
                "Student enrolled successfully" +
                        (guardians != null && !guardians.isEmpty()
                                ? " with " + guardians.size() + " guardian(s)"
                                : "")
        );
    }

    private Mono<StudentListResponse> toStudentListResponse(Student student) {
        Mono<Optional<StudentGuardian>> guardianMono = findPrimaryGuardian(student.getId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        Mono<Optional<StudentListResponse.CurrentClass>> classMono =
                findClass(student.getCurrentClassId(), student.getSchoolId())
                .map(optionalClass -> optionalClass.map(cls -> new StudentListResponse.CurrentClass(
                        cls.getId(),
                        cls.getName(),
                        cls.getGradeLevel())));

        return Mono.zip(guardianMono, classMono)
                .map(tuple -> {
                    Optional<StudentGuardian> guardian = tuple.getT1();
                    Optional<StudentListResponse.CurrentClass> currentClass = tuple.getT2();

                    return new StudentListResponse(
                            student.getId(),
                            student.getAdmissionNumber(),
                            student.getFirstName(),
                            student.getLastName(),
                            student.getMiddleName(),
                            student.getGender(),
                            student.getDateOfBirth(),
                            currentClass.orElse(null),
                            student.getEnrollmentDate(),
                            student.getEnrollmentStatus(),
                            guardian.map(StudentGuardian::getPhone).orElse(null),
                            guardian.map(this::guardianFullName).orElse(null),
                            student.getProfilePhotoUrl()
                    );
                });
    }

    private Mono<StudentDetailResponse> toStudentDetailResponse(Student student) {
        Mono<List<StudentDetailResponse.ParentInfo>> parentsMono =
                guardianLinkRepository.findActiveByStudentId(student.getId())
                        .flatMap(link ->
                                guardianRepository.findByIdAndDeletedAtIsNull(link.getGuardianId())
                                        .map(guardian -> new StudentDetailResponse.ParentInfo(
                                                guardian.getUserId(),
                                                guardianFullName(guardian),
                                                guardian.getPhone(),
                                                link.getRelationship(),
                                                Boolean.TRUE.equals(link.getIsPrimaryContact())))
                        )
                        .collectList();

        Mono<Optional<StudentDetailResponse.CurrentClass>> classMono =
                findClass(student.getCurrentClassId(), student.getSchoolId())
                        .map(optionalClass -> optionalClass.map(cls -> new StudentDetailResponse.CurrentClass(
                                cls.getId(),
                                cls.getName(),
                                cls.getGradeLevel(),
                                null // Phase 2: class teacher name
                        )));

        // Phase 2: Fetch actual fee data
        StudentDetailResponse.FeeSummary feeSummary =
                new StudentDetailResponse.FeeSummary(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        "PENDING", null);

        return Mono.zip(parentsMono, classMono)
                .map(tuple -> new StudentDetailResponse(
                        student.getId(),
                        student.getAdmissionNumber(),
                        student.getFirstName(),
                        student.getLastName(),
                        student.getMiddleName(),
                        student.getGender(),
                        student.getDateOfBirth(),
                        tuple.getT2().orElse(null),
                        student.getEnrollmentDate(),
                        student.getEnrollmentStatus(),
                        tuple.getT1(),
                        feeSummary,
                        student.getMedicalNotes(),
                        student.getProfilePhotoUrl()
                ));
    }

    private MyChildrenResponse toMyChildrenResponse(
            Student student, ClassEntity cls, StudentGuardianLink link) {

        // Phase 2: Fetch actual fee data
        MyChildrenResponse.FeeStatus feeStatus = new MyChildrenResponse.FeeStatus(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "PENDING", null);

        return new MyChildrenResponse(
                student.getId(),
                student.getAdmissionNumber(),
                student.getFirstName(),
                student.getLastName(),
                cls != null ? cls.getName() : null,
                student.getProfilePhotoUrl(),
                feeStatus
        );
    }

    private Mono<StudentGuardian> findPrimaryGuardian(UUID studentId) {
        return guardianLinkRepository.findActivePrimaryByStudentId(studentId)
                .next()
                .flatMap(link -> guardianRepository.findByIdAndDeletedAtIsNull(link.getGuardianId()));
    }

    private Mono<Optional<ClassEntity>> findClass(UUID classId, UUID schoolId) {
        return Mono.justOrEmpty(classId)
                .flatMap(id -> classRepository.findByIdAndSchoolId(id, schoolId))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private ListStudentsCriteria validateAndNormalizeListRequest(
            String status, String search, Pageable pageable) {
        if (pageable == null) {
            throw new SchoolFeeException(
                    "INVALID_PAGE_REQUEST",
                    "Page request is required",
                    "pageable");
        }
        if (pageable.getPageNumber() < 0) {
            throw new SchoolFeeException(
                    "INVALID_PAGE_REQUEST",
                    "Page must be greater than or equal to 0",
                    "page");
        }
        if (pageable.getPageSize() <= 0) {
            throw new SchoolFeeException(
                    "INVALID_PAGE_REQUEST",
                    "Size must be greater than 0",
                    "size");
        }
        if (pageable.getPageSize() > 100) {
            throw new SchoolFeeException(
                    "INVALID_PAGE_REQUEST",
                    "Size must not exceed 100",
                    "size");
        }

        int size = pageable.getPageSize();
        int page = pageable.getPageNumber();
        return new ListStudentsCriteria(
                resolveStudentStatusFilter(status),
                trimToNull(search),
                page,
                size,
                pageable.getOffset());
    }

    private Boolean resolveStudentStatusFilter(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return true;
        }

        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> true;
            case "INACTIVE" -> false;
            case "ALL" -> null;
            default -> throw new SchoolFeeException(
                    "INVALID_STATUS",
                    "Student status must be one of ACTIVE, INACTIVE, or ALL",
                    "status");
        };
    }

    private int calculateTotalPages(long totalElements, int pageSize) {
        if (totalElements == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }

    private UUID requireStudentId(UUID studentId) {
        if (studentId == null) {
            throw new SchoolFeeException(
                    "INVALID_STUDENT",
                    "Student id is required",
                    "studentId");
        }
        return studentId;
    }

    private Mono<Void> validateParentCanViewStudent(UUID parentUserId, UUID studentId) {
        if (parentUserId == null) {
            return Mono.error(new SchoolFeeException(
                    "ACCESS_DENIED",
                    "You can only view your own children's details"));
        }

        return guardianLinkRepository
                .findByGuardianUserIdAndStudentId(parentUserId, studentId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "ACCESS_DENIED",
                        "You can only view your own children's details")))
                .then();
    }

    private <T> Mono<T> studentNotFound() {
        return Mono.error(new SchoolFeeException(
                "STUDENT_NOT_FOUND",
                "Student not found or does not belong to your school"));
    }

    private String guardianFullName(StudentGuardian guardian) {
        return trimToNull(String.join(" ", Stream.of(guardian.getFirstName(), guardian.getLastName())
                        .map(this::trimToNull)
                        .filter(Objects::nonNull)
                        .toList()));
    }

    private record Enrollment(Student student, ClassEntity cls) {
    }

    private record ValidatedUpdateStudentRequest(UUID studentId, UpdateStudentRequest request) {
    }

    private record ListStudentsCriteria(
            Boolean isActive,
            String search,
            int page,
            int size,
            long offset) {
    }
}
