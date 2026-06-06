package com.fee.app.schoolfeeapp.school.service.impl;

import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.school.dto.request.CreateClassRequest;
import com.fee.app.schoolfeeapp.school.dto.request.PromoteStudentsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.ClassDetailResponse;
import com.fee.app.schoolfeeapp.school.dto.response.ClassResponse;
import com.fee.app.schoolfeeapp.school.dto.response.PromoteStudentsResponse;
import com.fee.app.schoolfeeapp.school.dto.response.UpdateClassRequest;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassServiceImplTest {

    @Mock
    private ClassRepository classRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AcademicSessionRepository sessionRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TransactionalOperator transactionalOperator;

    private ClassServiceImpl classService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID OTHER_SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678902");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID SESSION_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID TEACHER_ID = UUID.fromString("f6a7b890-1234-5678-f123-456789012345");
    private static final UUID OTHER_TEACHER_ID = UUID.fromString("f6a7b890-1234-5678-f123-456789012346");
    private static final UUID CLASS_ID = UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901234");
    private static final UUID TARGET_CLASS_ID = UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901235");
    private static final UUID STUDENT_ID_1 = UUID.fromString("a1111111-1111-1111-1111-111111111111");
    private static final UUID STUDENT_ID_2 = UUID.fromString("a2222222-2222-2222-2222-222222222222");
    private static final UUID STUDENT_ID_3 = UUID.fromString("a3333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        classService = new ClassServiceImpl(
                classRepository,
                studentRepository,
                sessionRepository,
                schoolRepository,
                jwtUtils,
                transactionalOperator);
    }

    @Test
    @DisplayName("Should create class in locked active school session")
    void shouldCreateClassInLockedActiveSchoolSession() {
        CreateClassRequest request = new CreateClassRequest(
                "  Primary 1  ",
                "  Grade 1  ",
                "  A  ",
                SESSION_ID,
                TEACHER_ID,
                35);
        AcademicSession session = currentSession();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(session));
        when(classRepository.save(any(ClassEntity.class)))
                .thenAnswer(invocation -> Mono.just(savedClass(invocation.getArgument(0))));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.createClass(request))
                .assertNext(response -> {
                    assertThat(response.classId()).isNotNull();
                    assertThat(response.name()).isEqualTo("Primary 1");
                    assertThat(response.gradeLevel()).isEqualTo("Grade 1");
                    assertThat(response.section()).isEqualTo("A");
                    assertThat(response.sessionName()).isEqualTo("2025/2026 Academic Year");
                    assertThat(response.classTeacher()).isNotNull();
                    assertThat(response.classTeacher().userId()).isEqualTo(TEACHER_ID);
                    assertThat(response.capacity()).isEqualTo(35);
                    assertThat(response.currentEnrollment()).isZero();
                    assertThat(response.availableSpots()).isEqualTo(35);
                    assertThat(response.status()).isEqualTo("ACTIVE");
                })
                .verifyComplete();

        ArgumentCaptor<ClassEntity> classCaptor = ArgumentCaptor.forClass(ClassEntity.class);
        verify(classRepository).save(classCaptor.capture());
        ClassEntity savedClass = classCaptor.getValue();
        assertThat(savedClass.getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(savedClass.getName()).isEqualTo("Primary 1");
        assertThat(savedClass.getGradeLevel()).isEqualTo("Grade 1");
        assertThat(savedClass.getSection()).isEqualTo("A");
        assertThat(savedClass.getAcademicSessionId()).isEqualTo(SESSION_ID);
        assertThat(savedClass.getClassTeacherId()).isEqualTo(TEACHER_ID);
        assertThat(savedClass.getCapacity()).isEqualTo(35);
        assertThat(savedClass.getIsActive()).isTrue();
        verify(schoolRepository).findActiveByIdForUpdate(SCHOOL_ID);
        verify(sessionRepository).findByIdForUpdate(SESSION_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should reject invalid class request before auth lookup")
    void shouldRejectInvalidClassRequestBeforeAuthLookup() {
        CreateClassRequest request = new CreateClassRequest(
                " ",
                "Grade 1",
                null,
                SESSION_ID,
                null,
                30);

        StepVerifier.create(classService.createClass(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_CLASS_CONFIG");
                    assertThat(exception.getField()).isEqualTo("name");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class creation for inactive school")
    void shouldRejectClassCreationForInactiveSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.createClass(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(sessionRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class creation when session is missing")
    void shouldRejectClassCreationWhenSessionIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.createClass(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_FOUND");
                })
                .verify();

        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class creation when session is in another school")
    void shouldRejectClassCreationWhenSessionIsInAnotherSchool() {
        AcademicSession otherSchoolSession = currentSession();
        otherSchoolSession.setSchoolId(OTHER_SCHOOL_ID);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(otherSchoolSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.createClass(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_IN_SCHOOL");
                })
                .verify();

        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class creation in closed session")
    void shouldRejectClassCreationInClosedSession() {
        AcademicSession closedSession = currentSession();
        closedSession.setStatus("COMPLETED");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(closedSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.createClass(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                    assertThat(exception.getField()).isEqualTo("academicSessionId");
                })
                .verify();

        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should map duplicate class save race to domain error")
    void shouldMapDuplicateClassSaveRaceToDomainError() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.save(any(ClassEntity.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("school_classes_school_id_name_academic_session_id_key")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.createClass(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_CLASS");
                    assertThat(exception.getField()).isEqualTo("name");
                    assertThat(exception.getCause()).isInstanceOf(DuplicateKeyException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("Should list active classes for current session with enrollment data")
    void shouldListActiveClassesForCurrentSessionWithEnrollmentData() {
        ClassEntity primaryTwo = targetClass();
        primaryTwo.setCreatedAt(java.time.Instant.parse("2025-09-02T10:15:30Z"));
        ClassEntity primaryOne = existingClass();
        primaryOne.setCreatedAt(java.time.Instant.parse("2025-09-01T10:15:30Z"));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findBySchoolIdAndIsCurrentTrue(SCHOOL_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.findBySchoolIdAndAcademicSessionIdAndIsActive(SCHOOL_ID, SESSION_ID, true))
                .thenReturn(Flux.just(primaryTwo, primaryOne));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(2L));
        when(studentRepository.countActiveByCurrentClassId(TARGET_CLASS_ID)).thenReturn(Mono.just(1L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.listClasses(" current ", null, "ACTIVE"))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(2);
                    ClassResponse first = responses.get(0);
                    assertThat(first.classId()).isEqualTo(CLASS_ID);
                    assertThat(first.name()).isEqualTo("Primary 1");
                    assertThat(first.sessionName()).isEqualTo("2025/2026 Academic Year");
                    assertThat(first.classTeacher()).isNotNull();
                    assertThat(first.classTeacher().userId()).isEqualTo(TEACHER_ID);
                    assertThat(first.capacity()).isEqualTo(35);
                    assertThat(first.currentEnrollment()).isEqualTo(2);
                    assertThat(first.availableSpots()).isEqualTo(33);
                    assertThat(first.studentIds()).isEmpty();
                    assertThat(first.status()).isEqualTo("ACTIVE");

                    ClassResponse second = responses.get(1);
                    assertThat(second.classId()).isEqualTo(TARGET_CLASS_ID);
                    assertThat(second.name()).isEqualTo("Primary 2");
                    assertThat(second.currentEnrollment()).isEqualTo(1);
                    assertThat(second.availableSpots()).isEqualTo(34);
                })
                .verifyComplete();

        verify(schoolRepository).findActiveByIdForUpdate(SCHOOL_ID);
        verify(sessionRepository).findBySchoolIdAndIsCurrentTrue(SCHOOL_ID);
        verify(classRepository).findBySchoolIdAndAcademicSessionIdAndIsActive(SCHOOL_ID, SESSION_ID, true);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should list inactive classes by explicit session and trimmed grade")
    void shouldListInactiveClassesByExplicitSessionAndTrimmedGrade() {
        ClassEntity inactiveClass = existingClass();
        inactiveClass.setIsActive(false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.findBySchoolIdAndAcademicSessionIdAndGradeLevelAndIsActive(
                SCHOOL_ID,
                SESSION_ID,
                "Grade 1",
                false))
                .thenReturn(Flux.just(inactiveClass));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(0L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.listClasses(SESSION_ID.toString(), "  Grade 1  ", "inactive"))
                .assertNext(responses -> {
                    assertThat(responses).hasSize(1);
                    assertThat(responses.get(0).status()).isEqualTo("INACTIVE");
                    assertThat(responses.get(0).sessionName()).isEqualTo("2025/2026 Academic Year");
                    assertThat(responses.get(0).availableSpots()).isEqualTo(35);
                })
                .verifyComplete();

        verify(classRepository).findBySchoolIdAndAcademicSessionIdAndGradeLevelAndIsActive(
                SCHOOL_ID,
                SESSION_ID,
                "Grade 1",
                false);
    }

    @Test
    @DisplayName("Should return empty list when current session is missing")
    void shouldReturnEmptyListWhenCurrentSessionIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findBySchoolIdAndIsCurrentTrue(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.listClasses("current", null, null))
                .assertNext(responses -> assertThat(responses).isEmpty())
                .verifyComplete();

        verify(classRepository, never()).findBySchoolIdAndIsActive(any(UUID.class), any(Boolean.class));
        verify(studentRepository, never()).countActiveByCurrentClassId(any(UUID.class));
    }

    @Test
    @DisplayName("Should reject invalid list session id before auth lookup")
    void shouldRejectInvalidListSessionIdBeforeAuthLookup() {
        StepVerifier.create(classService.listClasses("not-a-uuid", null, "ACTIVE"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_CLASS_FILTER");
                    assertThat(exception.getField()).isEqualTo("sessionId");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject invalid list status before auth lookup")
    void shouldRejectInvalidListStatusBeforeAuthLookup() {
        StepVerifier.create(classService.listClasses(null, null, "ARCHIVED"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_CLASS_FILTER");
                    assertThat(exception.getField()).isEqualTo("status");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject class listing for inactive school")
    void shouldRejectClassListingForInactiveSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.listClasses(null, null, "ACTIVE"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(classRepository, never()).findBySchoolIdAndIsActive(any(UUID.class), any(Boolean.class));
    }

    @Test
    @DisplayName("Should reject class listing for explicit session in another school")
    void shouldRejectClassListingForExplicitSessionInAnotherSchool() {
        AcademicSession otherSchoolSession = currentSession();
        otherSchoolSession.setSchoolId(OTHER_SCHOOL_ID);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(otherSchoolSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.listClasses(SESSION_ID.toString(), null, "ACTIVE"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_IN_SCHOOL");
                    assertThat(exception.getField()).isEqualTo("sessionId");
                })
                .verify();

        verify(classRepository, never()).findBySchoolIdAndAcademicSessionIdAndIsActive(
                any(UUID.class),
                any(UUID.class),
                any(Boolean.class));
    }

    @Test
    @DisplayName("Should get class details with locked active school class session and active students")
    void shouldGetClassDetailsWithLockedActiveSchoolClassSessionAndActiveStudents() {
        ClassEntity cls = existingClass();
        Student maleStudent = student(STUDENT_ID_1, CLASS_ID);
        maleStudent.setFirstName("Alan");
        maleStudent.setLastName("Turing");
        maleStudent.setGender("MALE");
        Student femaleStudent = student(STUDENT_ID_2, CLASS_ID);
        femaleStudent.setFirstName("Ada");
        femaleStudent.setLastName("Lovelace");
        femaleStudent.setGender("FEMALE");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(cls));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID))
                .thenReturn(Flux.just(femaleStudent, maleStudent));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.getClassDetails(CLASS_ID))
                .assertNext(response -> {
                    assertThat(response.classId()).isEqualTo(CLASS_ID);
                    assertThat(response.name()).isEqualTo("Primary 1");
                    assertThat(response.gradeLevel()).isEqualTo("Grade 1");
                    assertThat(response.section()).isEqualTo("A");
                    assertThat(response.sessionName()).isEqualTo("2025/2026 Academic Year");
                    assertThat(response.classTeacher()).isNotNull();
                    assertThat(response.classTeacher().userId()).isEqualTo(TEACHER_ID);
                    assertThat(response.capacity()).isEqualTo(35);
                    assertThat(response.currentEnrollment()).isEqualTo(2);
                    assertThat(response.students()).hasSize(2);
                    assertThat(response.students())
                            .extracting(ClassDetailResponse.StudentSummary::studentId)
                            .containsExactly(STUDENT_ID_2, STUDENT_ID_1);
                    assertThat(response.statistics().maleCount()).isEqualTo(1);
                    assertThat(response.statistics().femaleCount()).isEqualTo(1);
                    assertThat(response.statistics().pendingFees()).isEqualTo(2);
                })
                .verifyComplete();

        verify(schoolRepository).findActiveByIdForUpdate(SCHOOL_ID);
        verify(classRepository).findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID);
        verify(sessionRepository).findByIdForUpdate(SESSION_ID);
        verify(studentRepository).findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should get inactive class details for active school")
    void shouldGetInactiveClassDetailsForActiveSchool() {
        ClassEntity inactiveClass = existingClass();
        inactiveClass.setIsActive(false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(inactiveClass));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID)).thenReturn(Flux.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.getClassDetails(CLASS_ID))
                .assertNext(response -> {
                    assertThat(response.classId()).isEqualTo(CLASS_ID);
                    assertThat(response.currentEnrollment()).isZero();
                    assertThat(response.students()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject null class id details before auth lookup")
    void shouldRejectNullClassIdDetailsBeforeAuthLookup() {
        StepVerifier.create(classService.getClassDetails(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_CLASS");
                    assertThat(exception.getField()).isEqualTo("classId");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject class details for inactive school")
    void shouldRejectClassDetailsForInactiveSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.getClassDetails(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(classRepository, never()).findByIdAndSchoolIdForUpdate(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should reject class details when class is missing")
    void shouldRejectClassDetailsWhenClassIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.getClassDetails(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CLASS_NOT_FOUND");
                })
                .verify();

        verify(sessionRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(studentRepository, never()).findActiveBySchoolIdAndCurrentClassId(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should reject class details when session is missing")
    void shouldRejectClassDetailsWhenSessionIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.getClassDetails(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_FOUND");
                })
                .verify();

        verify(studentRepository, never()).findActiveBySchoolIdAndCurrentClassId(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should reject class details when session is in another school")
    void shouldRejectClassDetailsWhenSessionIsInAnotherSchool() {
        AcademicSession otherSchoolSession = currentSession();
        otherSchoolSession.setSchoolId(OTHER_SCHOOL_ID);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(otherSchoolSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.getClassDetails(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_IN_SCHOOL");
                })
                .verify();

        verify(studentRepository, never()).findActiveBySchoolIdAndCurrentClassId(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should update class fields in locked active school session")
    void shouldUpdateClassFieldsInLockedActiveSchoolSession() {
        ClassEntity cls = existingClass();
        UpdateClassRequest request = new UpdateClassRequest(
                "  Primary 1 Gold  ",
                "  Grade 1  ",
                OTHER_TEACHER_ID,
                45);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(cls));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(studentRepository.countByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(30L));
        when(classRepository.save(any(ClassEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.updateClass(CLASS_ID, request))
                .assertNext(response -> {
                    assertThat(response.classId()).isEqualTo(CLASS_ID);
                    assertThat(response.name()).isEqualTo("Primary 1 Gold");
                    assertThat(response.classTeacher()).isEqualTo(OTHER_TEACHER_ID.toString());
                    assertThat(response.capacity()).isEqualTo(45);
                    assertThat(response.updatedAt()).isNotNull();
                })
                .verifyComplete();

        assertThat(cls.getName()).isEqualTo("Primary 1 Gold");
        assertThat(cls.getGradeLevel()).isEqualTo("Grade 1");
        assertThat(cls.getClassTeacherId()).isEqualTo(OTHER_TEACHER_ID);
        assertThat(cls.getCapacity()).isEqualTo(45);
        assertThat(cls.getUpdatedAt()).isNotNull();
        assertThat(cls.getUpdatedBy()).isEqualTo(USER_ID);
        verify(schoolRepository).findActiveByIdForUpdate(SCHOOL_ID);
        verify(classRepository).findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID);
        verify(sessionRepository).findByIdForUpdate(SESSION_ID);
        verify(studentRepository).countByCurrentClassId(CLASS_ID);
        verify(classRepository).save(cls);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should update class name without capacity count")
    void shouldUpdateClassNameWithoutCapacityCount() {
        ClassEntity cls = existingClass();
        UpdateClassRequest request = new UpdateClassRequest(
                "Primary 1 Blue",
                null,
                null,
                null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(cls));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.save(any(ClassEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.updateClass(CLASS_ID, request))
                .assertNext(response -> {
                    assertThat(response.name()).isEqualTo("Primary 1 Blue");
                    assertThat(response.capacity()).isEqualTo(35);
                })
                .verifyComplete();

        assertThat(cls.getName()).isEqualTo("Primary 1 Blue");
        assertThat(cls.getCapacity()).isEqualTo(35);
        verify(studentRepository, never()).countByCurrentClassId(any(UUID.class));
        verify(classRepository).save(cls);
    }

    @Test
    @DisplayName("Should reject null class id update before auth lookup")
    void shouldRejectNullClassIdUpdateBeforeAuthLookup() {
        StepVerifier.create(classService.updateClass(null, new UpdateClassRequest("Primary 1", null, null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_CLASS");
                    assertThat(exception.getField()).isEqualTo("classId");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject empty class update before auth lookup")
    void shouldRejectEmptyClassUpdateBeforeAuthLookup() {
        StepVerifier.create(classService.updateClass(CLASS_ID, new UpdateClassRequest(null, null, null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_CLASS_UPDATE");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject class update for inactive school")
    void shouldRejectClassUpdateForInactiveSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.updateClass(CLASS_ID, validUpdateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(classRepository, never()).findByIdAndSchoolIdForUpdate(any(UUID.class), any(UUID.class));
        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class update when class is missing")
    void shouldRejectClassUpdateWhenClassIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.updateClass(CLASS_ID, validUpdateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CLASS_NOT_FOUND");
                })
                .verify();

        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class update for inactive class")
    void shouldRejectClassUpdateForInactiveClass() {
        ClassEntity inactiveClass = existingClass();
        inactiveClass.setIsActive(false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(inactiveClass));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.updateClass(CLASS_ID, validUpdateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CLASS_NOT_ACTIVE");
                    assertThat(exception.getField()).isEqualTo("classId");
                })
                .verify();

        verify(sessionRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class update in closed session")
    void shouldRejectClassUpdateInClosedSession() {
        AcademicSession closedSession = currentSession();
        closedSession.setStatus("COMPLETED");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(closedSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.updateClass(CLASS_ID, validUpdateRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                    assertThat(exception.getField()).isEqualTo("academicSessionId");
                })
                .verify();

        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class update when capacity is below enrollment")
    void shouldRejectClassUpdateWhenCapacityIsBelowEnrollment() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(studentRepository.countByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(36L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.updateClass(CLASS_ID, new UpdateClassRequest(null, null, null, 35)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CAPACITY_TOO_LOW");
                    assertThat(exception.getField()).isEqualTo("capacity");
                })
                .verify();

        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should map duplicate class update race to domain error")
    void shouldMapDuplicateClassUpdateRaceToDomainError() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.save(any(ClassEntity.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("school_classes_school_id_name_academic_session_id_key")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.updateClass(CLASS_ID, new UpdateClassRequest("Primary 2", null, null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_CLASS");
                    assertThat(exception.getField()).isEqualTo("name");
                    assertThat(exception.getCause()).isInstanceOf(DuplicateKeyException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("Should map stale class update to conflict error")
    void shouldMapStaleClassUpdateToConflictError() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.save(any(ClassEntity.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale class row")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.updateClass(CLASS_ID, new UpdateClassRequest("Primary 2", null, null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("STALE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("version");
                    assertThat(exception.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("Should deactivate empty class in locked active school session")
    void shouldDeactivateEmptyClassInLockedActiveSchoolSession() {
        ClassEntity cls = existingClass();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(cls));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(0L));
        when(classRepository.save(any(ClassEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.deactivateClass(CLASS_ID))
                .verifyComplete();

        assertThat(cls.getIsActive()).isFalse();
        assertThat(cls.getUpdatedAt()).isNotNull();
        assertThat(cls.getUpdatedBy()).isEqualTo(USER_ID);
        verify(schoolRepository).findActiveByIdForUpdate(SCHOOL_ID);
        verify(classRepository).findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID);
        verify(sessionRepository).findByIdForUpdate(SESSION_ID);
        verify(studentRepository).countActiveByCurrentClassId(CLASS_ID);
        verify(classRepository).save(cls);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should reject null class id deactivation before auth lookup")
    void shouldRejectNullClassIdDeactivationBeforeAuthLookup() {
        StepVerifier.create(classService.deactivateClass(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_CLASS");
                    assertThat(exception.getField()).isEqualTo("classId");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject class deactivation for inactive school")
    void shouldRejectClassDeactivationForInactiveSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.deactivateClass(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(classRepository, never()).findByIdAndSchoolIdForUpdate(any(UUID.class), any(UUID.class));
        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class deactivation when class is missing")
    void shouldRejectClassDeactivationWhenClassIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.deactivateClass(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CLASS_NOT_FOUND");
                })
                .verify();

        verify(sessionRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject deactivation for inactive class")
    void shouldRejectDeactivationForInactiveClass() {
        ClassEntity inactiveClass = existingClass();
        inactiveClass.setIsActive(false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(inactiveClass));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.deactivateClass(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CLASS_NOT_ACTIVE");
                    assertThat(exception.getField()).isEqualTo("classId");
                })
                .verify();

        verify(sessionRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(studentRepository, never()).countActiveByCurrentClassId(any(UUID.class));
        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class deactivation in closed session")
    void shouldRejectClassDeactivationInClosedSession() {
        AcademicSession closedSession = currentSession();
        closedSession.setStatus("COMPLETED");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(closedSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.deactivateClass(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                    assertThat(exception.getField()).isEqualTo("academicSessionId");
                })
                .verify();

        verify(studentRepository, never()).countActiveByCurrentClassId(any(UUID.class));
        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should reject class deactivation when active students are enrolled")
    void shouldRejectClassDeactivationWhenActiveStudentsAreEnrolled() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(2L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.deactivateClass(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CLASS_HAS_STUDENTS");
                    assertThat(exception.getField()).isEqualTo("classId");
                })
                .verify();

        verify(classRepository, never()).save(any(ClassEntity.class));
    }

    @Test
    @DisplayName("Should map stale class deactivation to conflict error")
    void shouldMapStaleClassDeactivationToConflictError() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(0L));
        when(classRepository.save(any(ClassEntity.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale class row")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.deactivateClass(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("STALE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("version");
                    assertThat(exception.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("Should promote students with locked school session classes and student rows")
    void shouldPromoteStudentsWithLockedRows() {
        ClassEntity fromClass = existingClass();
        ClassEntity toClass = targetClass();
        Student firstStudent = student(STUDENT_ID_1, CLASS_ID);
        Student secondStudent = student(STUDENT_ID_2, CLASS_ID);
        PromoteStudentsRequest request = validPromotionRequest(STUDENT_ID_1, STUDENT_ID_2);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(fromClass));
        when(classRepository.findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(toClass));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID_1, SCHOOL_ID))
                .thenReturn(Mono.just(firstStudent));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID_2, SCHOOL_ID))
                .thenReturn(Mono.just(secondStudent));
        when(studentRepository.countActiveByCurrentClassId(TARGET_CLASS_ID)).thenReturn(Mono.just(5L));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.promoteStudents(request))
                .assertNext(response -> {
                    assertThat(response.promotionId()).isNotNull();
                    assertThat(response.fromClass()).isEqualTo("Primary 1");
                    assertThat(response.toClass()).isEqualTo("Primary 2");
                    assertThat(response.studentsPromoted()).isEqualTo(2);
                    assertThat(response.failedPromotions()).isEmpty();
                    assertThat(response.message()).isEqualTo("2 students promoted to Primary 2");
                })
                .verifyComplete();

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository, times(2)).save(studentCaptor.capture());
        assertThat(studentCaptor.getAllValues())
                .allSatisfy(student -> {
                    assertThat(student.getCurrentClassId()).isEqualTo(TARGET_CLASS_ID);
                    assertThat(student.getUpdatedAt()).isNotNull();
                    assertThat(student.getUpdatedBy()).isEqualTo(USER_ID);
                });
        verify(schoolRepository).findActiveByIdForUpdate(SCHOOL_ID);
        verify(sessionRepository).findByIdForUpdate(SESSION_ID);
        verify(classRepository).findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID);
        verify(classRepository).findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID);
        verify(studentRepository).countActiveByCurrentClassId(TARGET_CLASS_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should promote valid students and report invalid selected students")
    void shouldPromoteValidStudentsAndReportInvalidStudents() {
        PromoteStudentsRequest request = validPromotionRequest(STUDENT_ID_1, STUDENT_ID_2, STUDENT_ID_3);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(classRepository.findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(targetClass()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID_1, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID_1, CLASS_ID)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID_2, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID_3, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID_3, TARGET_CLASS_ID)));
        when(studentRepository.countActiveByCurrentClassId(TARGET_CLASS_ID)).thenReturn(Mono.just(0L));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.promoteStudents(request))
                .assertNext(response -> {
                    assertThat(response.studentsPromoted()).isEqualTo(1);
                    assertThat(response.failedPromotions()).hasSize(2);
                    assertThat(response.failedPromotions())
                            .extracting(PromoteStudentsResponse.FailedPromotion::studentId)
                            .containsExactly(STUDENT_ID_2, STUDENT_ID_3);
                    assertThat(response.message()).isEqualTo("1 students promoted to Primary 2. 2 failed.");
                })
                .verifyComplete();

        verify(studentRepository, times(1)).save(any(Student.class));
    }

    @Test
    @DisplayName("Should reject duplicate promotion student ids before auth lookup")
    void shouldRejectDuplicatePromotionStudentIdsBeforeAuthLookup() {
        PromoteStudentsRequest request = validPromotionRequest(STUDENT_ID_1, STUDENT_ID_1);

        StepVerifier.create(classService.promoteStudents(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_STUDENTS");
                    assertThat(exception.getField()).isEqualTo("studentIds");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject promotion to same class before auth lookup")
    void shouldRejectPromotionToSameClassBeforeAuthLookup() {
        PromoteStudentsRequest request = new PromoteStudentsRequest(
                CLASS_ID,
                CLASS_ID,
                List.of(STUDENT_ID_1),
                SESSION_ID);

        StepVerifier.create(classService.promoteStudents(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_PROMOTION");
                    assertThat(exception.getField()).isEqualTo("toClassId");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject promotion for inactive school")
    void shouldRejectPromotionForInactiveSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.promoteStudents(validPromotionRequest(STUDENT_ID_1)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(sessionRepository, never()).findByIdForUpdate(any(UUID.class));
        verify(classRepository, never()).findByIdAndSchoolIdForUpdate(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should reject promotion to closed target session")
    void shouldRejectPromotionToClosedTargetSession() {
        AcademicSession closedSession = currentSession();
        closedSession.setStatus("COMPLETED");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(closedSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.promoteStudents(validPromotionRequest(STUDENT_ID_1)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                    assertThat(exception.getField()).isEqualTo("newSessionId");
                })
                .verify();

        verify(classRepository, never()).findByIdAndSchoolIdForUpdate(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should reject promotion to inactive target class")
    void shouldRejectPromotionToInactiveTargetClass() {
        ClassEntity inactiveTargetClass = targetClass();
        inactiveTargetClass.setIsActive(false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(classRepository.findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(inactiveTargetClass));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.promoteStudents(validPromotionRequest(STUDENT_ID_1)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("TARGET_CLASS_NOT_ACTIVE");
                    assertThat(exception.getField()).isEqualTo("toClassId");
                })
                .verify();

        verify(studentRepository, never()).findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(any(UUID.class), any(UUID.class));
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should reject promotion when target class is not in new session")
    void shouldRejectPromotionWhenTargetClassIsNotInNewSession() {
        ClassEntity targetClass = targetClass();
        targetClass.setAcademicSessionId(UUID.fromString("d4e5f6a7-b890-1234-def1-234567890124"));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(classRepository.findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(targetClass));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.promoteStudents(validPromotionRequest(STUDENT_ID_1)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("TARGET_CLASS_SESSION_MISMATCH");
                    assertThat(exception.getField()).isEqualTo("newSessionId");
                })
                .verify();

        verify(studentRepository, never()).findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should reject promotion when target class capacity is insufficient")
    void shouldRejectPromotionWhenTargetClassCapacityIsInsufficient() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(classRepository.findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(targetClass()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID_1, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID_1, CLASS_ID)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID_2, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID_2, CLASS_ID)));
        when(studentRepository.countActiveByCurrentClassId(TARGET_CLASS_ID)).thenReturn(Mono.just(34L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.promoteStudents(validPromotionRequest(STUDENT_ID_1, STUDENT_ID_2)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INSUFFICIENT_CAPACITY");
                    assertThat(exception.getField()).isEqualTo("studentIds");
                })
                .verify();

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should map stale student promotion save to conflict error")
    void shouldMapStaleStudentPromotionSaveToConflictError() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool()));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Mono.just(currentSession()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(existingClass()));
        when(classRepository.findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(targetClass()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID_1, SCHOOL_ID))
                .thenReturn(Mono.just(student(STUDENT_ID_1, CLASS_ID)));
        when(studentRepository.countActiveByCurrentClassId(TARGET_CLASS_ID)).thenReturn(Mono.just(0L));
        when(studentRepository.save(any(Student.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale student")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(classService.promoteStudents(validPromotionRequest(STUDENT_ID_1)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("STALE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("studentIds");
                    assertThat(exception.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                })
                .verify();
    }

    private CreateClassRequest validRequest() {
        return new CreateClassRequest(
                "Primary 1",
                "Grade 1",
                "A",
                SESSION_ID,
                TEACHER_ID,
                35);
    }

    private UpdateClassRequest validUpdateRequest() {
        return new UpdateClassRequest(
                "Primary 1 Gold",
                "Grade 1",
                OTHER_TEACHER_ID,
                45);
    }

    private PromoteStudentsRequest validPromotionRequest(UUID... studentIds) {
        return new PromoteStudentsRequest(
                CLASS_ID,
                TARGET_CLASS_ID,
                List.of(studentIds),
                SESSION_ID);
    }

    private ClassEntity savedClass(ClassEntity cls) {
        if (cls.getId() == null) {
            cls.setId(UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901234"));
        }
        return cls;
    }

    private AcademicSession currentSession() {
        return AcademicSession.builder()
                .id(SESSION_ID)
                .schoolId(SCHOOL_ID)
                .name("2025/2026 Academic Year")
                .startDate(LocalDate.of(2025, 9, 8))
                .endDate(LocalDate.of(2026, 9, 7))
                .isCurrent(true)
                .status("ACTIVE")
                .build();
    }

    private ClassEntity existingClass() {
        return ClassEntity.builder()
                .id(CLASS_ID)
                .schoolId(SCHOOL_ID)
                .name("Primary 1")
                .gradeLevel("Grade 1")
                .section("A")
                .academicSessionId(SESSION_ID)
                .classTeacherId(TEACHER_ID)
                .capacity(35)
                .isActive(true)
                .version(0)
                .build();
    }

    private ClassEntity targetClass() {
        return ClassEntity.builder()
                .id(TARGET_CLASS_ID)
                .schoolId(SCHOOL_ID)
                .name("Primary 2")
                .gradeLevel("Grade 2")
                .section("A")
                .academicSessionId(SESSION_ID)
                .capacity(35)
                .isActive(true)
                .version(0)
                .build();
    }

    private Student student(UUID studentId, UUID currentClassId) {
        return Student.builder()
                .id(studentId)
                .schoolId(SCHOOL_ID)
                .admissionNumber("ADM-" + studentId.toString().substring(0, 8))
                .firstName("Ada")
                .lastName("Lovelace")
                .currentClassId(currentClassId)
                .enrollmentDate(LocalDate.of(2025, 9, 8))
                .enrollmentStatus("ACTIVE")
                .version(0)
                .build();
    }

    private School existingSchool() {
        return School.builder()
                .id(SCHOOL_ID)
                .name("Grace International School")
                .code("GIS")
                .isActive(true)
                .build();
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .email("admin@gis.edu")
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }
}
