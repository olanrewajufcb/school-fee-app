package com.fee.app.schoolfeeapp.result.service.impl;


import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.result.domain.ClassSubject;
import com.fee.app.schoolfeeapp.result.domain.Subject;
import com.fee.app.schoolfeeapp.result.dto.request.AssignSubjectRequest;
import com.fee.app.schoolfeeapp.result.dto.request.CreateSubjectRequest;
import com.fee.app.schoolfeeapp.result.dto.response.ClassSubjectResponse;
import com.fee.app.schoolfeeapp.result.dto.response.SubjectResponse;
import com.fee.app.schoolfeeapp.result.repository.ClassSubjectRepository;
import com.fee.app.schoolfeeapp.result.repository.SubjectRepository;
import com.fee.app.schoolfeeapp.result.service.SubjectService;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<SubjectResponse> createSubject(CreateSubjectRequest request) {
        return Mono.fromCallable(() -> normalize(request))
                .flatMap(normalized -> jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user.getSchoolId());
                    Instant now = Instant.now();
                    Subject subject = Subject.builder()
                            .schoolId(schoolId)
                            .name(normalized.name())
                            .code(normalized.code())
                            .category(normalized.category())
                            .isActive(true)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    return ensureUnique(schoolId, normalized, null)
                            .then(Mono.defer(() -> subjectRepository.save(subject)))
                            .map(this::toResponse)
                            .onErrorMap(DuplicateKeyException.class, this::duplicateSubjectException);
                }));
    }

    @Override
    public Mono<List<SubjectResponse>> listSubjects() {
        return jwtUtils.getCurrentUser()
                .flatMapMany(user -> subjectRepository.findActiveBySchoolIdOrderByName(
                        requireSchoolId(user.getSchoolId())))
                .map(this::toResponse)
                .collectList();
    }

    @Override
    public Mono<SubjectResponse> updateSubject(UUID subjectId, CreateSubjectRequest request) {
        if (subjectId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_SUBJECT",
                    "Subject ID is required",
                    "subjectId"));
        }

        return Mono.fromCallable(() -> normalize(request))
                .flatMap(normalized -> jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user.getSchoolId());
                    return subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(subjectId, schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException("SUBJECT_NOT_FOUND", "Subject not found")))
                        .flatMap(subject -> {
                            subject.setName(normalized.name());
                            subject.setCode(normalized.code());
                            subject.setCategory(normalized.category());
                            subject.setUpdatedAt(Instant.now());
                            return ensureUnique(schoolId, normalized, subjectId)
                                    .then(Mono.defer(() -> subjectRepository.save(subject)));
                        })
                        .map(this::toResponse)
                        .onErrorMap(DuplicateKeyException.class, this::duplicateSubjectException)
                        .onErrorMap(OptimisticLockingFailureException.class, error -> new SchoolFeeException(
                                "STALE_RESOURCE",
                                "This subject was changed by another user. Refresh and try again.",
                                null,
                                error));
                }));
    }

    private Mono<Void> ensureUnique(
            UUID schoolId,
            NormalizedSubject normalized,
            UUID excludedId) {
        return subjectRepository.existsByNormalizedName(schoolId, normalized.name(), excludedId)
                .flatMap(nameExists -> Boolean.TRUE.equals(nameExists)
                        ? Mono.error(duplicateSubject("name", normalized.name()))
                        : Mono.empty())
                .then(Mono.defer(() -> normalized.code() == null
                        ? Mono.empty()
                        : subjectRepository.existsByNormalizedCode(schoolId, normalized.code(), excludedId)
                                .flatMap(codeExists -> Boolean.TRUE.equals(codeExists)
                                        ? Mono.error(duplicateSubject("code", normalized.code()))
                                        : Mono.empty())))
                .then();
    }

    private NormalizedSubject normalize(CreateSubjectRequest request) {
        if (request == null) {
            throw new SchoolFeeException("INVALID_SUBJECT", "Subject details are required");
        }

        String name = normalizeSpaces(request.name());
        if (name == null) {
            throw new SchoolFeeException("INVALID_SUBJECT", "Subject name is required", "name");
        }
        if (name.length() > 100) {
            throw new SchoolFeeException(
                    "INVALID_SUBJECT",
                    "Subject name must not exceed 100 characters",
                    "name");
        }

        String code = normalizeSpaces(request.code());
        if (code != null) {
            code = code.toUpperCase(Locale.ROOT);
            if (code.length() > 20) {
                throw new SchoolFeeException(
                        "INVALID_SUBJECT",
                        "Subject code must not exceed 20 characters",
                        "code");
            }
        }

        String category = normalizeSpaces(request.category());
        if (category != null) {
            category = category.toUpperCase(Locale.ROOT);
            if (category.length() > 50) {
                throw new SchoolFeeException(
                        "INVALID_SUBJECT",
                        "Subject category must not exceed 50 characters",
                        "category");
            }
        }
        return new NormalizedSubject(name, code, category);
    }

    private String normalizeSpaces(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private UUID requireSchoolId(UUID schoolId) {
        if (schoolId == null) {
            throw new SchoolFeeException(
                    "SCHOOL_CONTEXT_REQUIRED",
                    "Select a school before managing subjects");
        }
        return schoolId;
    }

    private SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getName(),
                subject.getCode(),
                subject.getCategory(),
                subject.isActive());
    }

    private SchoolFeeException duplicateSubject(String field, String value) {
        return new SchoolFeeException(
                "DUPLICATE_RESOURCE",
                "A subject with this " + field + " already exists: " + value,
                field);
    }

    private SchoolFeeException duplicateSubjectException(Throwable error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        String field = message.contains("normalized_code") ? "code" : "name";
        log.warn("Duplicate subject rejected for field {}", field);
        return new SchoolFeeException(
                "DUPLICATE_RESOURCE",
                "A subject with this " + field + " already exists",
                field,
                error);
    }

    private record NormalizedSubject(String name, String code, String category) {
    }

    @Override
    public Mono<Void> deactivateSubject(UUID subjectId) {
        if (subjectId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_SUBJECT",
                    "Subject ID is required",
                    "subjectId"));
        }

        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user.getSchoolId());
                    Mono<Void> work = subjectRepository.findActiveByIdAndSchoolIdForUpdate(subjectId, schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException("SUBJECT_NOT_FOUND", "Subject not found")))
                        .flatMap(subject -> {
                            subject.setActive(false);
                            subject.setUpdatedAt(Instant.now());
                            return subjectRepository.save(subject);
                        })
                        .then(classSubjectRepository.deactivateActiveBySubjectIdAndSchoolId(subjectId, schoolId))
                        .then();
                    return transactionalOperator.transactional(work)
                            .onErrorMap(OptimisticLockingFailureException.class, error -> staleAssignment(error));
                });
    }

    @Override
    public Mono<ClassSubjectResponse> assignSubjectToClass(UUID classId, AssignSubjectRequest request) {
        validateAssignmentRequest(classId, request);

        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user.getSchoolId());
                    Mono<ClassSubjectResponse> work = validateClass(classId, schoolId)
                            .then(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(
                                    request.subjectId(), schoolId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "SUBJECT_NOT_FOUND",
                                            "Subject not found or inactive"))))
                            .flatMap(subject -> resolveTeacher(request.teacherId(), schoolId)
                                    .flatMap(teacher -> upsertClassSubject(
                                                    classId,
                                                    subject.getId(),
                                                    teacher.map(User::getId).orElse(null),
                                                    schoolId)
                                            .map(saved -> toClassSubjectResponse(
                                                    saved,
                                                    subject,
                                                    teacher))));

                    return transactionalOperator.transactional(work)
                            .onErrorMap(DuplicateKeyException.class, error -> new SchoolFeeException(
                                    "DUPLICATE_RESOURCE",
                                    "This subject is already assigned to the class. Refresh and try again.",
                                    "subjectId",
                                    error))
                            .onErrorMap(OptimisticLockingFailureException.class, this::staleAssignment);
                });
    }

    @Override
    public Mono<List<ClassSubjectResponse>> getSubjectsForClass(UUID classId) {
        if (classId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_CLASS",
                    "Class ID is required",
                    "classId"));
        }

        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user.getSchoolId());
                    return validateClass(classId, schoolId)
                            .thenMany(classSubjectRepository.findActiveByClassIdAndSchoolId(
                                    classId, schoolId))
                            .concatMap(assignment -> subjectRepository
                                    .findByIdAndSchoolIdAndIsActiveTrue(
                                            assignment.getSubjectId(), schoolId)
                                    .flatMap(subject -> resolveTeacherForDisplay(
                                                    assignment.getTeacherId(), schoolId)
                                            .map(teacher -> toClassSubjectResponse(
                                                    assignment,
                                                    subject,
                                                    teacher))))
                            .collectList();
                });
    }

    @Override
    public Mono<Void> removeSubjectFromClass(UUID classId, UUID subjectId) {
        if (classId == null || subjectId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_ASSIGNMENT",
                    "Class ID and subject ID are required"));
        }

        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = requireSchoolId(user.getSchoolId());
                    Mono<Void> work = validateClass(classId, schoolId)
                            .then(classSubjectRepository.findByClassAndSubjectForUpdate(
                                    classId, subjectId, schoolId))
                            .flatMap(assignment -> {
                                if (!assignment.isActive()) {
                                    return Mono.empty();
                                }
                                assignment.setActive(false);
                                assignment.setUpdatedAt(Instant.now());
                                return classSubjectRepository.save(assignment).then();
                            })
                            .then();
                    return transactionalOperator.transactional(work)
                            .onErrorMap(OptimisticLockingFailureException.class, this::staleAssignment);
                });
    }

    private void validateAssignmentRequest(UUID classId, AssignSubjectRequest request) {
        if (classId == null) {
            throw new SchoolFeeException("INVALID_CLASS", "Class ID is required", "classId");
        }
        if (request == null || request.subjectId() == null) {
            throw new SchoolFeeException(
                    "INVALID_ASSIGNMENT",
                    "Subject ID is required",
                    "subjectId");
        }
    }

    private Mono<Void> validateClass(UUID classId, UUID schoolId) {
        return classRepository.findByIdAndSchoolId(classId, schoolId)
                .filter(cls -> Boolean.TRUE.equals(cls.getIsActive()))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "CLASS_NOT_FOUND",
                        "Class not found or inactive")))
                .then();
    }

    private Mono<Optional<User>> resolveTeacher(UUID teacherId, UUID schoolId) {
        if (teacherId == null) {
            return Mono.just(Optional.empty());
        }
        return userRepository.findByIdAndSchoolIdAndDeletedAtIsNull(teacherId, schoolId)
                .filter(teacher -> Boolean.TRUE.equals(teacher.getIsActive())
                        && "TEACHER".equalsIgnoreCase(teacher.getUserType()))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "INVALID_TEACHER",
                        "Teacher not found, inactive, or outside the selected school",
                        "teacherId")))
                .map(Optional::of);
    }

    private Mono<Optional<User>> resolveTeacherForDisplay(UUID teacherId, UUID schoolId) {
        if (teacherId == null) {
            return Mono.just(Optional.empty());
        }
        return userRepository.findByIdAndSchoolIdAndDeletedAtIsNull(teacherId, schoolId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<ClassSubject> upsertClassSubject(
            UUID classId,
            UUID subjectId,
            UUID teacherId,
            UUID schoolId) {
        return classSubjectRepository.findByClassAndSubjectForUpdate(classId, subjectId, schoolId)
                .flatMap(existing -> {
                    boolean changed = !existing.isActive()
                            || !Objects.equals(existing.getTeacherId(), teacherId);
                    if (!changed) {
                        return Mono.just(existing);
                    }
                    existing.setActive(true);
                    existing.setTeacherId(teacherId);
                    existing.setUpdatedAt(Instant.now());
                    return classSubjectRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    Instant now = Instant.now();
                    ClassSubject assignment = ClassSubject.builder()
                            .schoolId(schoolId)
                            .classId(classId)
                            .subjectId(subjectId)
                            .teacherId(teacherId)
                            .isActive(true)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return classSubjectRepository.save(assignment);
                }));
    }

    private ClassSubjectResponse toClassSubjectResponse(
            ClassSubject assignment,
            Subject subject,
            Optional<User> teacher) {
        return new ClassSubjectResponse(
                assignment.getId(),
                subject.getId(),
                subject.getName(),
                subject.getCode(),
                assignment.getTeacherId(),
                teacher.map(this::fullName).orElse("No Teacher Assigned"));
    }

    private String fullName(User user) {
        String name = String.join(
                " ",
                Objects.toString(user.getFirstName(), "").trim(),
                Objects.toString(user.getLastName(), "").trim()).trim();
        return name.isEmpty() ? Objects.toString(user.getEmail(), "Unassigned") : name;
    }

    private SchoolFeeException staleAssignment(Throwable error) {
        return new SchoolFeeException(
                "STALE_RESOURCE",
                "This subject assignment was changed by another user. Refresh and try again.",
                null,
                error);
    }
}
