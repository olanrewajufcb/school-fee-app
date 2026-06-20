package com.fee.app.schoolfeeapp.result.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.result.domain.ClassSubject;
import com.fee.app.schoolfeeapp.result.domain.Subject;
import com.fee.app.schoolfeeapp.result.dto.request.AssignSubjectRequest;
import com.fee.app.schoolfeeapp.result.dto.request.CreateSubjectRequest;
import com.fee.app.schoolfeeapp.result.repository.ClassSubjectRepository;
import com.fee.app.schoolfeeapp.result.repository.SubjectRepository;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceImplTest {

    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private ClassSubjectRepository classSubjectRepository;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private TransactionalOperator transactionalOperator;

    private SubjectServiceImpl subjectService;

    private static final UUID SCHOOL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID SUBJECT_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID CLASS_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID TEACHER_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000006");

    @BeforeEach
    void setUp() {
        subjectService = new SubjectServiceImpl(
                subjectRepository,
                classSubjectRepository,
                jwtUtils,
                userRepository,
                classRepository,
                transactionalOperator);
        org.mockito.Mockito.lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldNormalizeAndCreateSubject() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(subjectRepository.existsByNormalizedName(SCHOOL_ID, "English Language", null))
                .thenReturn(Mono.just(false));
        when(subjectRepository.existsByNormalizedCode(SCHOOL_ID, "ENG 101", null))
                .thenReturn(Mono.just(false));
        when(subjectRepository.save(any(Subject.class)))
                .thenAnswer(invocation -> {
                    Subject subject = invocation.getArgument(0);
                    subject.setId(SUBJECT_ID);
                    subject.setVersion(0);
                    return Mono.just(subject);
                });

        StepVerifier.create(subjectService.createSubject(
                        new CreateSubjectRequest("  English   Language ", " eng 101 ", " languages ")))
                .assertNext(response -> {
                    assertThat(response.subjectId()).isEqualTo(SUBJECT_ID);
                    assertThat(response.name()).isEqualTo("English Language");
                    assertThat(response.code()).isEqualTo("ENG 101");
                    assertThat(response.category()).isEqualTo("LANGUAGES");
                    assertThat(response.isActive()).isTrue();
                })
                .verifyComplete();

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldRejectBlankNameBeforeAuthentication() {
        StepVerifier.create(subjectService.createSubject(
                        new CreateSubjectRequest("   ", "ENG", "Languages")))
                .expectErrorSatisfies(error -> assertBusinessError(error, "INVALID_SUBJECT", "name"))
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateNameBeforeSave() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(subjectRepository.existsByNormalizedName(SCHOOL_ID, "Mathematics", null))
                .thenReturn(Mono.just(true));

        StepVerifier.create(subjectService.createSubject(
                        new CreateSubjectRequest("Mathematics", "MTH", "Science")))
                .expectErrorSatisfies(error -> assertBusinessError(error, "DUPLICATE_RESOURCE", "name"))
                .verify();

        verify(subjectRepository, never()).save(any());
    }

    @Test
    void shouldTranslateDuplicateRaceFromDatabase() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(subjectRepository.existsByNormalizedName(SCHOOL_ID, "Mathematics", null))
                .thenReturn(Mono.just(false));
        when(subjectRepository.existsByNormalizedCode(SCHOOL_ID, "MTH", null))
                .thenReturn(Mono.just(false));
        when(subjectRepository.save(any(Subject.class)))
                .thenReturn(Mono.error(new DuplicateKeyException(
                        "duplicate key violates uq_subjects_school_normalized_code")));

        StepVerifier.create(subjectService.createSubject(
                        new CreateSubjectRequest("Mathematics", "MTH", "Science")))
                .expectErrorSatisfies(error -> assertBusinessError(error, "DUPLICATE_RESOURCE", "code"))
                .verify();
    }

    @Test
    void shouldListOnlyActiveSubjectsForCurrentSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(subjectRepository.findActiveBySchoolIdOrderByName(SCHOOL_ID))
                .thenReturn(Flux.just(
                        subject(SUBJECT_ID, "Agricultural Science", "AGR"),
                        subject(UUID.randomUUID(), "Mathematics", "MTH")));

        StepVerifier.create(subjectService.listSubjects())
                .assertNext(subjects -> {
                    assertThat(subjects).extracting("name")
                            .containsExactly("Agricultural Science", "Mathematics");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeAndUpdateSubjectWithoutChangingTenantOrIdentity() {
        Subject existing = subject(SUBJECT_ID, "Maths", "MTH");
        existing.setVersion(3);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existing));
        when(subjectRepository.existsByNormalizedName(SCHOOL_ID, "Mathematics", SUBJECT_ID))
                .thenReturn(Mono.just(false));
        when(subjectRepository.existsByNormalizedCode(SCHOOL_ID, "MATH", SUBJECT_ID))
                .thenReturn(Mono.just(false));
        when(subjectRepository.save(existing)).thenReturn(Mono.just(existing));

        StepVerifier.create(subjectService.updateSubject(
                        SUBJECT_ID,
                        new CreateSubjectRequest(" Mathematics ", " math ", " science ")))
                .assertNext(response -> {
                    assertThat(response.name()).isEqualTo("Mathematics");
                    assertThat(response.code()).isEqualTo("MATH");
                    assertThat(response.category()).isEqualTo("SCIENCE");
                })
                .verifyComplete();

        assertThat(existing.getId()).isEqualTo(SUBJECT_ID);
        assertThat(existing.getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(existing.getVersion()).isEqualTo(3);
        assertThat(existing.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldTranslateConcurrentUpdateToStaleResource() {
        Subject existing = subject(SUBJECT_ID, "Maths", "MTH");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existing));
        when(subjectRepository.existsByNormalizedName(SCHOOL_ID, "Mathematics", SUBJECT_ID))
                .thenReturn(Mono.just(false));
        when(subjectRepository.existsByNormalizedCode(SCHOOL_ID, "MATH", SUBJECT_ID))
                .thenReturn(Mono.just(false));
        when(subjectRepository.save(existing))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale")));

        StepVerifier.create(subjectService.updateSubject(
                        SUBJECT_ID,
                        new CreateSubjectRequest("Mathematics", "MATH", "Science")))
                .expectErrorSatisfies(error -> assertBusinessError(error, "STALE_RESOURCE", null))
                .verify();
    }

    @Test
    void shouldRequireSchoolContext() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(SchoolFeeUser.builder()
                .userId(USER_ID)
                .userType("SUPER_ADMIN")
                .build()));

        StepVerifier.create(subjectService.listSubjects())
                .expectErrorSatisfies(error -> assertBusinessError(
                        error, "SCHOOL_CONTEXT_REQUIRED", null))
                .verify();

        verify(subjectRepository, never()).findActiveBySchoolIdOrderByName(any());
    }

    @Test
    void shouldDeactivateSubjectAndItsClassAssignmentsTransactionally() {
        Subject existing = subject(SUBJECT_ID, "Mathematics", "MTH");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(subjectRepository.findActiveByIdAndSchoolIdForUpdate(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existing));
        when(subjectRepository.save(existing)).thenReturn(Mono.just(existing));
        when(classSubjectRepository.deactivateActiveBySubjectIdAndSchoolId(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(2));

        StepVerifier.create(subjectService.deactivateSubject(SUBJECT_ID))
                .verifyComplete();

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getUpdatedAt()).isNotNull();
        verify(classSubjectRepository)
                .deactivateActiveBySubjectIdAndSchoolId(SUBJECT_ID, SCHOOL_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    void shouldAssignValidatedSubjectAndTeacherToClass() {
        Subject existingSubject = subject(SUBJECT_ID, "Mathematics", "MTH");
        User teacher = teacher();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass()));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existingSubject));
        when(userRepository.findByIdAndSchoolIdAndDeletedAtIsNull(TEACHER_ID, SCHOOL_ID))
                .thenReturn(Mono.just(teacher));
        when(classSubjectRepository.findByClassAndSubjectForUpdate(
                CLASS_ID, SUBJECT_ID, SCHOOL_ID)).thenReturn(Mono.empty());
        when(classSubjectRepository.save(any(ClassSubject.class)))
                .thenAnswer(invocation -> {
                    ClassSubject assignment = invocation.getArgument(0);
                    assignment.setId(ASSIGNMENT_ID);
                    assignment.setVersion(0);
                    return Mono.just(assignment);
                });

        StepVerifier.create(subjectService.assignSubjectToClass(
                        CLASS_ID,
                        new AssignSubjectRequest(SUBJECT_ID, TEACHER_ID)))
                .assertNext(response -> {
                    assertThat(response.classSubjectId()).isEqualTo(ASSIGNMENT_ID);
                    assertThat(response.subjectId()).isEqualTo(SUBJECT_ID);
                    assertThat(response.teacherId()).isEqualTo(TEACHER_ID);
                    assertThat(response.teacherName()).isEqualTo("Ada Teacher");
                })
                .verifyComplete();

        ArgumentCaptor<ClassSubject> captor = ArgumentCaptor.forClass(ClassSubject.class);
        verify(classSubjectRepository).save(captor.capture());
        assertThat(captor.getValue().getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void shouldReactivateExistingClassAssignment() {
        Subject existingSubject = subject(SUBJECT_ID, "Mathematics", "MTH");
        ClassSubject assignment = assignment(false, null);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass()));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existingSubject));
        when(classSubjectRepository.findByClassAndSubjectForUpdate(
                CLASS_ID, SUBJECT_ID, SCHOOL_ID)).thenReturn(Mono.just(assignment));
        when(classSubjectRepository.save(assignment)).thenReturn(Mono.just(assignment));

        StepVerifier.create(subjectService.assignSubjectToClass(
                        CLASS_ID,
                        new AssignSubjectRequest(SUBJECT_ID, null)))
                .assertNext(response -> {
                    assertThat(response.classSubjectId()).isEqualTo(ASSIGNMENT_ID);
                    assertThat(response.teacherName()).isEqualTo("No Teacher Assigned");
                })
                .verifyComplete();

        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldRejectTeacherOutsideCurrentSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass()));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(subject(SUBJECT_ID, "Mathematics", "MTH")));
        when(userRepository.findByIdAndSchoolIdAndDeletedAtIsNull(TEACHER_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(subjectService.assignSubjectToClass(
                        CLASS_ID,
                        new AssignSubjectRequest(SUBJECT_ID, TEACHER_ID)))
                .expectErrorSatisfies(error -> assertBusinessError(
                        error, "INVALID_TEACHER", "teacherId"))
                .verify();

        verify(classSubjectRepository, never()).save(any());
    }

    @Test
    void shouldListClassSubjectsUsingTenantScopedQueries() {
        ClassSubject assignment = assignment(true, TEACHER_ID);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass()));
        when(classSubjectRepository.findActiveByClassIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Flux.just(assignment));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(subject(SUBJECT_ID, "Mathematics", "MTH")));
        when(userRepository.findByIdAndSchoolIdAndDeletedAtIsNull(TEACHER_ID, SCHOOL_ID))
                .thenReturn(Mono.just(teacher()));

        StepVerifier.create(subjectService.getSubjectsForClass(CLASS_ID))
                .assertNext(subjects -> {
                    assertThat(subjects).hasSize(1);
                    assertThat(subjects.getFirst().subjectName()).isEqualTo("Mathematics");
                    assertThat(subjects.getFirst().teacherName()).isEqualTo("Ada Teacher");
                })
                .verifyComplete();
    }

    @Test
    void shouldMakeRemovalIdempotentAndTenantScoped() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass()));
        when(classSubjectRepository.findByClassAndSubjectForUpdate(
                CLASS_ID, SUBJECT_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(subjectService.removeSubjectFromClass(CLASS_ID, SUBJECT_ID))
                .verifyComplete();

        verify(classSubjectRepository, never()).save(any());
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("SCHOOL_ADMIN")
                .build();
    }

    private Subject subject(UUID id, String name, String code) {
        return Subject.builder()
                .id(id)
                .schoolId(SCHOOL_ID)
                .name(name)
                .code(code)
                .category("SCIENCE")
                .isActive(true)
                .version(0)
                .build();
    }

    private ClassEntity activeClass() {
        return ClassEntity.builder()
                .id(CLASS_ID)
                .schoolId(SCHOOL_ID)
                .name("JSS 1")
                .isActive(true)
                .build();
    }

    private User teacher() {
        return User.builder()
                .id(TEACHER_ID)
                .schoolId(SCHOOL_ID)
                .firstName("Ada")
                .lastName("Teacher")
                .email("ada@example.com")
                .userType("TEACHER")
                .isActive(true)
                .build();
    }

    private ClassSubject assignment(boolean active, UUID teacherId) {
        return ClassSubject.builder()
                .id(ASSIGNMENT_ID)
                .schoolId(SCHOOL_ID)
                .classId(CLASS_ID)
                .subjectId(SUBJECT_ID)
                .teacherId(teacherId)
                .isActive(active)
                .version(0)
                .build();
    }

    private void assertBusinessError(Throwable error, String code, String field) {
        assertThat(error).isInstanceOf(SchoolFeeException.class);
        SchoolFeeException exception = (SchoolFeeException) error;
        assertThat(exception.getErrorCode()).isEqualTo(code);
        assertThat(exception.getField()).isEqualTo(field);
    }

    @Test
    void shouldThrowExceptionWhenDeactivatingNullSubjectId() {
        StepVerifier.create(subjectService.deactivateSubject(null))
                .expectErrorSatisfies(error -> assertBusinessError(error, "INVALID_SUBJECT", "subjectId"))
                .verify();
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNullSubjectId() {
        StepVerifier.create(subjectService.updateSubject(null, new CreateSubjectRequest("Name", "CODE", "Category")))
                .expectErrorSatisfies(error -> assertBusinessError(error, "INVALID_SUBJECT", "subjectId"))
                .verify();
    }

    @Test
    void shouldThrowExceptionWhenRequestIsNullInNormalize() {
        StepVerifier.create(subjectService.createSubject(null))
                .expectErrorSatisfies(error -> assertBusinessError(error, "INVALID_SUBJECT", null))
                .verify();
    }

    @Test
    void shouldThrowExceptionWhenNameIsTooLong() {
        String longName = "A".repeat(101);
        StepVerifier.create(subjectService.createSubject(new CreateSubjectRequest(longName, "CODE", "Category")))
                .expectErrorSatisfies(error -> assertBusinessError(error, "INVALID_SUBJECT", "name"))
                .verify();
    }

    @Test
    void shouldThrowExceptionWhenCodeIsTooLong() {
        String longCode = "C".repeat(21);
        StepVerifier.create(subjectService.createSubject(new CreateSubjectRequest("Name", longCode, "Category")))
                .expectErrorSatisfies(error -> assertBusinessError(error, "INVALID_SUBJECT", "code"))
                .verify();
    }

    @Test
    void shouldThrowExceptionWhenCategoryIsTooLong() {
        String longCategory = "Cat".repeat(18);
        StepVerifier.create(subjectService.createSubject(new CreateSubjectRequest("Name", "CODE", longCategory)))
                .expectErrorSatisfies(error -> assertBusinessError(error, "INVALID_SUBJECT", "category"))
                .verify();
    }

    @Test
    void shouldReturnNullWhenValueTrimsToEmptyInNormalizeSpaces() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(subjectRepository.existsByNormalizedName(SCHOOL_ID, "Name", null))
                .thenReturn(Mono.just(false));
        when(subjectRepository.save(any(Subject.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(subjectService.createSubject(new CreateSubjectRequest("Name", "   ", "Category")))
                .assertNext(response -> {
                    assertThat(response.code()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldThrowExceptionWhenClassIdIsNullInValidateAssignmentRequest() {
        assertThatThrownBy(() -> subjectService.assignSubjectToClass(null, new AssignSubjectRequest(SUBJECT_ID, TEACHER_ID)))
                .isInstanceOf(SchoolFeeException.class)
                .satisfies(error -> assertBusinessError(error, "INVALID_CLASS", "classId"));
    }

    @Test
    void shouldThrowExceptionWhenRequestOrSubjectIdIsNullInValidateAssignmentRequest() {
        assertThatThrownBy(() -> subjectService.assignSubjectToClass(CLASS_ID, null))
                .isInstanceOf(SchoolFeeException.class)
                .satisfies(error -> assertBusinessError(error, "INVALID_ASSIGNMENT", "subjectId"));

        assertThatThrownBy(() -> subjectService.assignSubjectToClass(CLASS_ID, new AssignSubjectRequest(null, TEACHER_ID)))
                .isInstanceOf(SchoolFeeException.class)
                .satisfies(error -> assertBusinessError(error, "INVALID_ASSIGNMENT", "subjectId"));
    }

    @Test
    void shouldThrowExceptionWhenClassIsNotFoundOrInactive() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.empty());
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(subjectService.assignSubjectToClass(CLASS_ID, new AssignSubjectRequest(SUBJECT_ID, TEACHER_ID)))
                .expectErrorSatisfies(error -> assertBusinessError(error, "CLASS_NOT_FOUND", null))
                .verify();

        ClassEntity inactiveCls = activeClass();
        inactiveCls.setIsActive(false);
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(inactiveCls));

        StepVerifier.create(subjectService.assignSubjectToClass(CLASS_ID, new AssignSubjectRequest(SUBJECT_ID, TEACHER_ID)))
                .expectErrorSatisfies(error -> assertBusinessError(error, "CLASS_NOT_FOUND", null))
                .verify();
    }

    @Test
    void shouldThrowExceptionWhenTeacherIsInactiveOrNotTeacher() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass()));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(subject(SUBJECT_ID, "Mathematics", "MTH")));

        User inactiveTeacher = teacher();
        inactiveTeacher.setIsActive(false);
        when(userRepository.findByIdAndSchoolIdAndDeletedAtIsNull(TEACHER_ID, SCHOOL_ID))
                .thenReturn(Mono.just(inactiveTeacher));

        StepVerifier.create(subjectService.assignSubjectToClass(CLASS_ID, new AssignSubjectRequest(SUBJECT_ID, TEACHER_ID)))
                .expectErrorSatisfies(error -> assertBusinessError(error, "INVALID_TEACHER", "teacherId"))
                .verify();

        User studentUser = teacher();
        studentUser.setUserType("STUDENT");
        when(userRepository.findByIdAndSchoolIdAndDeletedAtIsNull(TEACHER_ID, SCHOOL_ID))
                .thenReturn(Mono.just(studentUser));

        StepVerifier.create(subjectService.assignSubjectToClass(CLASS_ID, new AssignSubjectRequest(SUBJECT_ID, TEACHER_ID)))
                .expectErrorSatisfies(error -> assertBusinessError(error, "INVALID_TEACHER", "teacherId"))
                .verify();
    }

    @Test
    void shouldReturnEmptyOptionalWhenTeacherNotFoundForDisplay() {
        ClassSubject assignment = assignment(true, TEACHER_ID);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass()));
        when(classSubjectRepository.findActiveByClassIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Flux.just(assignment));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(subject(SUBJECT_ID, "Mathematics", "MTH")));
        when(userRepository.findByIdAndSchoolIdAndDeletedAtIsNull(TEACHER_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(subjectService.getSubjectsForClass(CLASS_ID))
                .assertNext(subjects -> {
                    assertThat(subjects).hasSize(1);
                    assertThat(subjects.getFirst().teacherName()).isEqualTo("No Teacher Assigned");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnExistingAssignmentWhenUpsertingNoChange() {
        Subject existingSubject = subject(SUBJECT_ID, "Mathematics", "MTH");
        ClassSubject assignment = assignment(true, TEACHER_ID);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass()));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existingSubject));
        when(userRepository.findByIdAndSchoolIdAndDeletedAtIsNull(TEACHER_ID, SCHOOL_ID))
                .thenReturn(Mono.just(teacher()));
        when(classSubjectRepository.findByClassAndSubjectForUpdate(CLASS_ID, SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(assignment));

        StepVerifier.create(subjectService.assignSubjectToClass(CLASS_ID, new AssignSubjectRequest(SUBJECT_ID, TEACHER_ID)))
                .assertNext(response -> {
                    assertThat(response.classSubjectId()).isEqualTo(ASSIGNMENT_ID);
                })
                .verifyComplete();

        verify(classSubjectRepository, never()).save(any());
    }

    @Test
    void shouldMapDuplicateKeyExceptionOnAssignSubjectToClass() {
        Subject existingSubject = subject(SUBJECT_ID, "Mathematics", "MTH");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass()));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existingSubject));
        when(classSubjectRepository.findByClassAndSubjectForUpdate(CLASS_ID, SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(classSubjectRepository.save(any(ClassSubject.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate")));

        StepVerifier.create(subjectService.assignSubjectToClass(CLASS_ID, new AssignSubjectRequest(SUBJECT_ID, null)))
                .expectErrorSatisfies(error -> assertBusinessError(error, "DUPLICATE_RESOURCE", "subjectId"))
                .verify();
    }

    @Test
    void shouldMapOptimisticLockingFailureExceptionOnAssignSubjectToClass() {
        Subject existingSubject = subject(SUBJECT_ID, "Mathematics", "MTH");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass()));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existingSubject));
        when(classSubjectRepository.findByClassAndSubjectForUpdate(CLASS_ID, SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(classSubjectRepository.save(any(ClassSubject.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale")));

        StepVerifier.create(subjectService.assignSubjectToClass(CLASS_ID, new AssignSubjectRequest(SUBJECT_ID, null)))
                .expectErrorSatisfies(error -> assertBusinessError(error, "STALE_RESOURCE", null))
                .verify();
    }

    @Test
    void shouldMapOptimisticLockingFailureExceptionOnRemoveSubjectFromClass() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass()));
        
        ClassSubject assignment = assignment(true, TEACHER_ID);
        when(classSubjectRepository.findByClassAndSubjectForUpdate(CLASS_ID, SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(assignment));
        when(classSubjectRepository.save(assignment))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale")));

        StepVerifier.create(subjectService.removeSubjectFromClass(CLASS_ID, SUBJECT_ID))
                .expectErrorSatisfies(error -> assertBusinessError(error, "STALE_RESOURCE", null))
                .verify();
    }

    @Test
    void shouldMapOptimisticLockingFailureExceptionOnDeactivateSubject() {
        Subject existing = subject(SUBJECT_ID, "Mathematics", "MTH");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(subjectRepository.findActiveByIdAndSchoolIdForUpdate(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existing));
        when(subjectRepository.save(existing))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale")));
        when(classSubjectRepository.deactivateActiveBySubjectIdAndSchoolId(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(0));

        StepVerifier.create(subjectService.deactivateSubject(SUBJECT_ID))
                .expectErrorSatisfies(error -> assertBusinessError(error, "STALE_RESOURCE", null))
                .verify();
    }

    @Test
    void shouldThrowExceptionWhenGetSubjectsForClassIdIsNull() {
        StepVerifier.create(subjectService.getSubjectsForClass(null))
                .expectErrorSatisfies(error -> assertBusinessError(error, "INVALID_CLASS", "classId"))
                .verify();
    }

    @Test
    void shouldThrowExceptionWhenRemoveSubjectFromClassIdsAreNull() {
        StepVerifier.create(subjectService.removeSubjectFromClass(null, SUBJECT_ID))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(SchoolFeeException.class))
                .verify();

        StepVerifier.create(subjectService.removeSubjectFromClass(CLASS_ID, null))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(SchoolFeeException.class))
                .verify();
    }
}
