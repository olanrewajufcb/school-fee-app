package com.fee.app.schoolfeeapp.student.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.dto.request.EnrollStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.response.MyChildrenResponse;
import com.fee.app.schoolfeeapp.student.dto.response.StudentDetailResponse;
import com.fee.app.schoolfeeapp.student.dto.response.StudentListResponse;
import com.fee.app.schoolfeeapp.student.dto.response.UpdateStudentResponse;
import com.fee.app.schoolfeeapp.student.repository.SchoolStudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import com.fee.app.schoolfeeapp.fee.service.FeeService;
import java.time.Instant;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ClassRepository classRepository;

    @Mock
    private AcademicSessionRepository sessionRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private StudentGuardianRepository guardianRepository;

    @Mock
    private SchoolStudentGuardianLinkRepository guardianLinkRepository;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TransactionalOperator transactionalOperator;

    @Mock
    private FeeService feeService;

    private StudentServiceImpl studentService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID CLASS_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID SESSION_ID = UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901234");
    private static final UUID STUDENT_ID = UUID.fromString("f6a7b890-1234-4567-f123-456789012345");
    private static final UUID GUARDIAN_ID = UUID.fromString("a7b89012-3456-7890-1234-567890123456");
    private static final UUID PARENT_USER_ID = UUID.fromString("b8901234-5678-9012-3456-789012345678");
    private static final UUID TARGET_CLASS_ID = UUID.fromString("c9012345-6789-0123-4567-890123456789");

    @BeforeEach
    void setUp() {
        studentService = new StudentServiceImpl(
                studentRepository,
                classRepository,
                sessionRepository,
                schoolRepository,
                guardianRepository,
                guardianLinkRepository,
                jwtUtils,
                transactionalOperator,
                feeService);
    }

    @Test
    @DisplayName("Should enroll student in locked active school class session")
    void shouldEnrollStudentInLockedActiveSchoolClassSession() {
        EnrollStudentRequest request = validRequest();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(activeSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass(2)));
        when(sessionRepository.findByIdAndDeletedAtIsNull(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(1L));
        when(studentRepository.countBySchoolIdAndDeletedAtIsNull(SCHOOL_ID)).thenReturn(Mono.just(1L));
        when(studentRepository.save(any(Student.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull("2348031234567", SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(guardianRepository.save(any(StudentGuardian.class)))
                .thenAnswer(invocation -> {
                    StudentGuardian guardian = invocation.getArgument(0);
                    guardian.setId(UUID.randomUUID());
                    return Mono.just(guardian);
                });
        when(guardianLinkRepository.findByGuardianIdAndStudentId(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.empty());
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(any(UUID.class)))
                .thenReturn(Flux.empty());
        when(guardianLinkRepository.save(any(StudentGuardianLink.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.enrollStudent(request))
                .assertNext(response -> {
                    assertThat(response.studentId()).isNotNull();
                    assertThat(response.admissionNumber()).matches(admissionNumberPattern(2));
                    assertThat(response.firstName()).isEqualTo("Ada");
                    assertThat(response.lastName()).isEqualTo("Lovelace");
                    assertThat(response.classId()).isEqualTo(CLASS_ID);
                    assertThat(response.className()).isEqualTo("Primary 1");
                    assertThat(response.parentCreated()).isTrue();
                })
                .verifyComplete();

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(studentCaptor.capture());
        Student savedStudent = studentCaptor.getValue();
        assertThat(savedStudent.getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(savedStudent.getAdmissionNumber()).matches(admissionNumberPattern(2));
        assertThat(savedStudent.getFirstName()).isEqualTo("Ada");
        assertThat(savedStudent.getLastName()).isEqualTo("Lovelace");
        assertThat(savedStudent.getGender()).isEqualTo("FEMALE");
        assertThat(savedStudent.getCurrentClassId()).isEqualTo(CLASS_ID);
        assertThat(savedStudent.getEnrollmentStatus()).isEqualTo("ACTIVE");
        assertThat(savedStudent.getUpdatedBy()).isEqualTo(USER_ID);

        ArgumentCaptor<StudentGuardian> guardianCaptor = ArgumentCaptor.forClass(StudentGuardian.class);
        verify(guardianRepository).save(guardianCaptor.capture());
        assertThat(guardianCaptor.getValue().getPhone()).isEqualTo("2348031234567");
        assertThat(guardianCaptor.getValue().getCreatedBy()).isEqualTo(USER_ID);

        verify(schoolRepository).findByIdAndIsActiveTrue(SCHOOL_ID);
        verify(classRepository).findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID);
        verify(sessionRepository).findByIdAndDeletedAtIsNull(SESSION_ID);
        verify(studentRepository).countActiveByCurrentClassId(CLASS_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should reject invalid student request before auth lookup")
    void shouldRejectInvalidStudentRequestBeforeAuthLookup() {
        EnrollStudentRequest request = new EnrollStudentRequest(
                " ",
                "Lovelace",
                null,
                "FEMALE",
                LocalDate.of(2018, 1, 1),
                CLASS_ID,
                List.of(),
                null);

        StepVerifier.create(studentService.enrollStudent(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_STUDENT_ENROLLMENT");
                    assertThat(exception.getField()).isEqualTo("firstName");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should reject duplicate primary guardians before auth lookup")
    void shouldRejectDuplicatePrimaryGuardiansBeforeAuthLookup() {
        EnrollStudentRequest request = new EnrollStudentRequest(
                "Ada",
                "Lovelace",
                null,
                "FEMALE",
                LocalDate.of(2018, 1, 1),
                CLASS_ID,
                List.of(validGuardian("08031234567", true), validGuardian("08031234568", true)),
                null);

        StepVerifier.create(studentService.enrollStudent(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_PRIMARY_GUARDIAN");
                    assertThat(exception.getField()).isEqualTo("guardians");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject invalid guardian phone before auth lookup")
    void shouldRejectInvalidGuardianPhoneBeforeAuthLookup() {
        EnrollStudentRequest request = new EnrollStudentRequest(
                "Ada",
                "Lovelace",
                null,
                "FEMALE",
                LocalDate.of(2018, 1, 1),
                CLASS_ID,
                List.of(validGuardian("12345", true)),
                null);

        StepVerifier.create(studentService.enrollStudent(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_GUARDIAN_PHONE");
                    assertThat(exception.getField()).isEqualTo("guardians[0].phone");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject enrollment for inactive school")
    void shouldRejectEnrollmentForInactiveSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(studentService.enrollStudent(validRequestWithoutGuardians()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(classRepository, never()).findByIdAndSchoolIdForUpdate(any(UUID.class), any(UUID.class));
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should reject enrollment when class is missing")
    void shouldRejectEnrollmentWhenClassIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(activeSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.enrollStudent(validRequestWithoutGuardians()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CLASS_NOT_FOUND");
                    assertThat(exception.getField()).isEqualTo("classId");
                })
                .verify();

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should reject enrollment into inactive class")
    void shouldRejectEnrollmentIntoInactiveClass() {
        ClassEntity inactiveClass = activeClass(2);
        inactiveClass.setIsActive(false);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(activeSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(inactiveClass));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.enrollStudent(validRequestWithoutGuardians()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CLASS_INACTIVE");
                    assertThat(exception.getField()).isEqualTo("classId");
                })
                .verify();

        verify(sessionRepository, never()).findByIdAndDeletedAtIsNull(any(UUID.class));
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should reject enrollment into closed session")
    void shouldRejectEnrollmentIntoClosedSession() {
        AcademicSession session = activeSession();
        session.setStatus("COMPLETED");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(activeSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass(2)));
        when(sessionRepository.findByIdAndDeletedAtIsNull(SESSION_ID)).thenReturn(Mono.just(session));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.enrollStudent(validRequestWithoutGuardians()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                    assertThat(exception.getField()).isEqualTo("academicSessionId");
                })
                .verify();

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should reject enrollment when class is full")
    void shouldRejectEnrollmentWhenClassIsFull() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(activeSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass(2)));
        when(sessionRepository.findByIdAndDeletedAtIsNull(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(2L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.enrollStudent(validRequestWithoutGuardians()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CLASS_FULL");
                    assertThat(exception.getField()).isEqualTo("classId");
                })
                .verify();

        verify(studentRepository, never()).countBySchoolIdAndDeletedAtIsNull(any(UUID.class));
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should map duplicate admission number save race to domain error")
    void shouldMapDuplicateAdmissionNumberSaveRaceToDomainError() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findByIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.just(activeSchool()));
        when(classRepository.findByIdAndSchoolIdForUpdate(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass(2)));
        when(sessionRepository.findByIdAndDeletedAtIsNull(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(0L));
        when(studentRepository.countBySchoolIdAndDeletedAtIsNull(SCHOOL_ID)).thenReturn(Mono.just(0L));
        when(studentRepository.save(any(Student.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate admission")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.enrollStudent(validRequestWithoutGuardians()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_ADMISSION_NUMBER");
                    assertThat(exception.getField()).isEqualTo("admissionNumber");
                    assertThat(exception.getCause()).isInstanceOf(DuplicateKeyException.class);
                })
                .verify();
    }

    @Test
    @DisplayName("Should list all student statuses and keep rows when guardian or class is missing")
    void shouldListAllStudentStatusesAndKeepRowsWhenGuardianOrClassIsMissing() {
        Student student = student("INACTIVE");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findBySchoolIdWithFilters(
                eq(SCHOOL_ID), eq(CLASS_ID), isNull(), eq("Ada"), eq(20), eq(0L)))
                .thenReturn(Flux.just(student));
        when(studentRepository.countBySchoolIdWithFilters(
                eq(SCHOOL_ID), eq(CLASS_ID), isNull(), eq("Ada")))
                .thenReturn(Mono.just(1L));
        when(guardianLinkRepository.findActivePrimaryByStudentId(STUDENT_ID)).thenReturn(Flux.empty());
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(studentService.listStudents(CLASS_ID, " ALL ", " Ada ", PageRequest.of(0, 20)))
                .assertNext(page -> {
                    assertThat(page).isEqualTo(new PageResponse<>(
                            page.content(), 0, 20, 1, 1));
                    assertThat(page.content()).hasSize(1);
                    StudentListResponse response = page.content().getFirst();
                    assertThat(response.studentId()).isEqualTo(STUDENT_ID);
                    assertThat(response.status()).isEqualTo("INACTIVE");
                    assertThat(response.currentClass()).isNull();
                    assertThat(response.parentName()).isNull();
                    assertThat(response.parentPhone()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject invalid list status before auth lookup")
    void shouldRejectInvalidListStatusBeforeAuthLookup() {
        StepVerifier.create(studentService.listStudents(null, "ARCHIVED", null, PageRequest.of(0, 10)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_STATUS");
                    assertThat(exception.getField()).isEqualTo("status");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject missing pageable before auth lookup")
    void shouldRejectMissingPageableBeforeAuthLookup() {
        StepVerifier.create(studentService.listStudents(null, "ACTIVE", null, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_PAGE_REQUEST");
                    assertThat(exception.getField()).isEqualTo("pageable");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should get student details for staff with active parents and class")
    void shouldGetStudentDetailsForStaffWithActiveParentsAndClass() {
        Student student = student("ACTIVE");
        StudentGuardianLink link = guardianLink(true);
        StudentGuardian guardian = guardian();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student));
        when(guardianLinkRepository.findActiveByStudentId(STUDENT_ID)).thenReturn(Flux.just(link));
        when(guardianRepository.findByIdAndDeletedAtIsNull(GUARDIAN_ID)).thenReturn(Mono.just(guardian));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass(30)));
        when(feeService.getStudentFees(STUDENT_ID)).thenReturn(Mono.just(List.of()));

        StepVerifier.create(studentService.getStudentDetails(STUDENT_ID))
                .assertNext(response -> {
                    assertThat(response.studentId()).isEqualTo(STUDENT_ID);
                    assertThat(response.currentClass()).isNotNull();
                    assertThat(response.currentClass().classId()).isEqualTo(CLASS_ID);
                    assertThat(response.parents()).hasSize(1);
                    StudentDetailResponse.ParentInfo parent = response.parents().getFirst();
                    assertThat(parent.userId()).isEqualTo(PARENT_USER_ID);
                    assertThat(parent.name()).isEqualTo("Grace Hopper");
                    assertThat(parent.isPrimaryContact()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return not found before parent access check when student is missing")
    void shouldReturnNotFoundBeforeParentAccessCheckWhenStudentIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(studentRepository.findByIdAndDeletedAtIsNull(STUDENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(studentService.getStudentDetails(STUDENT_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("STUDENT_NOT_FOUND");
                })
                .verify();

        verify(guardianLinkRepository, never()).findByGuardianUserIdAndStudentId(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should reject parent details request for unlinked student")
    void shouldRejectParentDetailsRequestForUnlinkedStudent() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(studentRepository.findByIdAndDeletedAtIsNull(STUDENT_ID)).thenReturn(Mono.just(student("ACTIVE")));
        when(guardianLinkRepository.findByGuardianUserIdAndStudentId(PARENT_USER_ID, STUDENT_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(studentService.getStudentDetails(STUDENT_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();

        verify(guardianLinkRepository, never()).findActiveByStudentId(any(UUID.class));
        verify(classRepository, never()).findByIdAndSchoolId(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should get my children for parent and keep child when class is missing")
    void shouldGetMyChildrenForParentAndKeepChildWhenClassIsMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianRepository.findByKeycloakId(PARENT_USER_ID)).thenReturn(Mono.just(guardian()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                .thenReturn(Flux.just(guardianLink(true)));
        when(studentRepository.findByIdAndDeletedAtIsNull(STUDENT_ID)).thenReturn(Mono.just(student("ACTIVE")));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(studentService.getMyChildren())
                .assertNext(children -> {
                    assertThat(children).hasSize(1);
                    MyChildrenResponse child = children.getFirst();
                    assertThat(child.studentId()).isEqualTo(STUDENT_ID);
                    assertThat(child.currentClass()).isNull();
                    assertThat(child.feeStatus().status()).isEqualTo("PENDING");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject my children request for non-parent")
    void shouldRejectMyChildrenRequestForNonParent() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));

        StepVerifier.create(studentService.getMyChildren())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();

        verify(guardianRepository, never()).findByKeycloakId(any(UUID.class));
    }

    @Test
    @DisplayName("Should update student under row lock without target class lock")
    void shouldUpdateStudentUnderRowLockWithoutTargetClassLock() {
        UpdateStudentRequest request = new UpdateStudentRequest(
                " Marie ",
                " ",
                " Curie ",
                " male ",
                LocalDate.of(2017, 2, 3),
                null,
                " suspended ",
                " ");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student("ACTIVE")));
        when(studentRepository.save(any(Student.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(activeClass(30)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.updateStudent(STUDENT_ID, request))
                .assertNext(response -> {
                    assertThat(response).isInstanceOf(UpdateStudentResponse.class);
                    assertThat(response.studentId()).isEqualTo(STUDENT_ID);
                    assertThat(response.firstName()).isEqualTo("Marie");
                    assertThat(response.lastName()).isEqualTo("Curie");
                    assertThat(response.className()).isEqualTo("Primary 1");
                    assertThat(response.enrollmentStatus()).isEqualTo("SUSPENDED");
                    assertThat(response.updatedAt()).isNotNull();
                })
                .verifyComplete();

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(studentCaptor.capture());
        Student savedStudent = studentCaptor.getValue();
        assertThat(savedStudent.getFirstName()).isEqualTo("Marie");
        assertThat(savedStudent.getLastName()).isEqualTo("Curie");
        assertThat(savedStudent.getMiddleName()).isNull();
        assertThat(savedStudent.getGender()).isEqualTo("MALE");
        assertThat(savedStudent.getMedicalNotes()).isNull();
        assertThat(savedStudent.getUpdatedBy()).isEqualTo(USER_ID);
        verify(classRepository, never()).findByIdAndSchoolIdForUpdate(any(UUID.class), any(UUID.class));
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should move student to locked target class when capacity is available")
    void shouldMoveStudentToLockedTargetClassWhenCapacityIsAvailable() {
        UpdateStudentRequest request = updateClassRequest(TARGET_CLASS_ID);
        ClassEntity targetClass = activeClass(TARGET_CLASS_ID, 2);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student("ACTIVE")));
        when(classRepository.findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(targetClass));
        when(sessionRepository.findByIdAndDeletedAtIsNull(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(studentRepository.countActiveByCurrentClassId(TARGET_CLASS_ID)).thenReturn(Mono.just(1L));
        when(studentRepository.save(any(Student.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(classRepository.findByIdAndSchoolId(TARGET_CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(targetClass));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.updateStudent(STUDENT_ID, request))
                .assertNext(response -> {
                    assertThat(response.currentClassId()).isEqualTo(TARGET_CLASS_ID);
                    assertThat(response.className()).isEqualTo("Primary 2");
                })
                .verifyComplete();

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getCurrentClassId()).isEqualTo(TARGET_CLASS_ID);
        verify(classRepository).findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID);
        verify(studentRepository).countActiveByCurrentClassId(TARGET_CLASS_ID);
    }

    @Test
    @DisplayName("Should reject class move when locked target class is full")
    void shouldRejectClassMoveWhenLockedTargetClassIsFull() {
        UpdateStudentRequest request = updateClassRequest(TARGET_CLASS_ID);
        ClassEntity targetClass = activeClass(TARGET_CLASS_ID, 1);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student("ACTIVE")));
        when(classRepository.findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(targetClass));
        when(sessionRepository.findByIdAndDeletedAtIsNull(SESSION_ID)).thenReturn(Mono.just(activeSession()));
        when(studentRepository.countActiveByCurrentClassId(TARGET_CLASS_ID)).thenReturn(Mono.just(1L));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.updateStudent(STUDENT_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("CLASS_FULL");
                    assertThat(exception.getField()).isEqualTo("classId");
                })
                .verify();

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should reject update into closed target session")
    void shouldRejectUpdateIntoClosedTargetSession() {
        AcademicSession closedSession = activeSession();
        closedSession.setStatus("COMPLETED");
        UpdateStudentRequest request = updateClassRequest(TARGET_CLASS_ID);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student("ACTIVE")));
        when(classRepository.findByIdAndSchoolIdForUpdate(TARGET_CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(activeClass(TARGET_CLASS_ID, 10)));
        when(sessionRepository.findByIdAndDeletedAtIsNull(SESSION_ID)).thenReturn(Mono.just(closedSession));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.updateStudent(STUDENT_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                })
                .verify();

        verify(studentRepository, never()).countActiveByCurrentClassId(any(UUID.class));
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should reject invalid update request before auth lookup")
    void shouldRejectInvalidUpdateRequestBeforeAuthLookup() {
        UpdateStudentRequest request = new UpdateStudentRequest(
                " ",
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        StepVerifier.create(studentService.updateStudent(STUDENT_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_STUDENT_UPDATE");
                    assertThat(exception.getField()).isEqualTo("firstName");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject update when student is missing under lock")
    void shouldRejectUpdateWhenStudentIsMissingUnderLock() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.updateStudent(STUDENT_ID, updateNameRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("STUDENT_NOT_FOUND");
                })
                .verify();

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Should map stale update save to domain error")
    void shouldMapStaleUpdateSaveToDomainError() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student("ACTIVE")));
        when(studentRepository.save(any(Student.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(studentService.updateStudent(STUDENT_ID, updateNameRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("STALE_RESOURCE");
                    assertThat(exception.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                })
                .verify();
    }

    private EnrollStudentRequest validRequest() {
        return new EnrollStudentRequest(
                " Ada ",
                " Lovelace ",
                null,
                "female",
                LocalDate.of(2018, 1, 1),
                CLASS_ID,
                List.of(validGuardian("08031234567", true)),
                " Asthma ");
    }

    private EnrollStudentRequest validRequestWithoutGuardians() {
        return new EnrollStudentRequest(
                "Ada",
                "Lovelace",
                null,
                "FEMALE",
                LocalDate.of(2018, 1, 1),
                CLASS_ID,
                List.of(),
                null);
    }

    private EnrollStudentRequest.GuardianInfo validGuardian(String phone, boolean primary) {
        return new EnrollStudentRequest.GuardianInfo(
                "Grace",
                "Hopper",
                phone,
                "grace@example.com",
                "Mother",
                primary,
                true,
                true,
                true,
                true,
                true,
                1);
    }

    private UpdateStudentRequest updateNameRequest() {
        return new UpdateStudentRequest(
                "Marie",
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private UpdateStudentRequest updateClassRequest(UUID classId) {
        return new UpdateStudentRequest(
                null,
                null,
                null,
                null,
                null,
                classId,
                null,
                null);
    }

    private String admissionNumberPattern(long sequence) {
        String year = String.valueOf(LocalDate.now().getYear()).substring(2);
        return String.format("STU%s%04d[A-F0-9]{4}", year, sequence);
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

    private School activeSchool() {
        return School.builder()
                .id(SCHOOL_ID)
                .name("Grace International School")
                .code("GIS")
                .isActive(true)
                .build();
    }

    private ClassEntity activeClass(int capacity) {
        return activeClass(CLASS_ID, capacity);
    }

    private ClassEntity activeClass(UUID classId, int capacity) {
        return ClassEntity.builder()
                .id(classId)
                .schoolId(SCHOOL_ID)
                .name(classId.equals(CLASS_ID) ? "Primary 1" : "Primary 2")
                .gradeLevel(classId.equals(CLASS_ID) ? "PRIMARY_1" : "PRIMARY_2")
                .academicSessionId(SESSION_ID)
                .capacity(capacity)
                .isActive(true)
                .build();
    }

    private AcademicSession activeSession() {
        return AcademicSession.builder()
                .id(SESSION_ID)
                .schoolId(SCHOOL_ID)
                .name("2025/2026 Academic Year")
                .startDate(LocalDate.of(2025, 9, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .isCurrent(true)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Student student(String status) {
        return Student.builder()
                .id(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .admissionNumber("STU260001ABCD")
                .firstName("Ada")
                .lastName("Lovelace")
                .middleName("Byron")
                .gender("FEMALE")
                .dateOfBirth(LocalDate.of(2018, 1, 1))
                .currentClassId(CLASS_ID)
                .enrollmentDate(LocalDate.of(2025, 9, 8))
                .enrollmentStatus(status)
                .medicalNotes("Asthma")
                .profilePhotoUrl("https://cdn.example.com/ada.png")
                .build();
    }

    private StudentGuardian guardian() {
        return StudentGuardian.builder()
                .id(GUARDIAN_ID)
                .schoolId(SCHOOL_ID)
                .userId(PARENT_USER_ID)
                .firstName("Grace")
                .lastName("Hopper")
                .phone("2348031234567")
                .isActive(true)
                .build();
    }

    private StudentGuardianLink guardianLink(boolean primary) {
        return StudentGuardianLink.builder()
                .id(UUID.randomUUID())
                .guardianId(GUARDIAN_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .relationship("MOTHER")
                .isPrimaryContact(primary)
                .contactPriority(1)
                .build();
    }

    private SchoolFeeUser parentUser() {
        return SchoolFeeUser.builder()
                .userId(PARENT_USER_ID)
                .email("parent@gis.edu")
                .userType("PARENT")
                .roles(Set.of("PARENT"))
                .build();
    }
}
