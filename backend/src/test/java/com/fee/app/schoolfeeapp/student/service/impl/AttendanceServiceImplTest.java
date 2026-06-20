package com.fee.app.schoolfeeapp.student.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLinkProjection;
import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.domain.OutboxEvent;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.repository.OutboxEventRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import com.fee.app.schoolfeeapp.student.domain.AttendanceSession;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.domain.StudentAttendance;
import com.fee.app.schoolfeeapp.student.dto.request.CreateAttendanceSessionRequest;
import com.fee.app.schoolfeeapp.student.dto.request.MarkAttendanceRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateAttendanceRequest;
import com.fee.app.schoolfeeapp.student.dto.response.AttendanceSessionResponse;
import com.fee.app.schoolfeeapp.student.dto.response.AttendanceResponse;
import com.fee.app.schoolfeeapp.student.repository.AttendanceSessionRepository;
import java.time.LocalTime;
import com.fee.app.schoolfeeapp.student.repository.StudentAttendanceRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AttendanceServiceImplTest {

    @Mock
    private AttendanceSessionRepository sessionRepository;
    @Mock
    private StudentAttendanceRepository attendanceRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private StudentGuardianRepository guardianRepository;
    @Mock
    private StudentGuardianLinkRepository guardianLinkRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TermRepository termRepository;

    private AttendanceServiceImpl attendanceService;

    private static final UUID SCHOOL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID KEYCLOAK_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID LOCAL_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID CLASS_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID TERM_ID = UUID.fromString("10000000-0000-0000-0000-000000000006");
    private static final UUID STUDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000007");
    private static final UUID ATTENDANCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000008");
    private static final UUID GUARDIAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000009");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        attendanceService = new AttendanceServiceImpl(
                sessionRepository,
                attendanceRepository,
                studentRepository,
                classRepository,
                guardianRepository,
                guardianLinkRepository,
                outboxEventRepository,
                jwtUtils,
                transactionalOperator,
                new ObjectMapper(),
                userRepository,
                termRepository);

        org.mockito.Mockito.lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        org.mockito.Mockito.lenient().when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_USER_ID))
                .thenReturn(Mono.just(User.builder().id(LOCAL_USER_ID).build()));
        org.mockito.Mockito.lenient().when(sessionRepository.findByIdForUpdate(SESSION_ID))
                .thenReturn(Mono.just(attendanceSession()));
        org.mockito.Mockito.lenient().when(sessionRepository.findById(SESSION_ID))
                .thenReturn(Mono.just(attendanceSession()));
        org.mockito.Mockito.lenient().when(sessionRepository.save(any(AttendanceSession.class)))
                .thenAnswer(invocation -> {
                    AttendanceSession s = invocation.getArgument(0);
                    if (s.getId() == null) {
                        s.setId(SESSION_ID);
                    }
                    return Mono.just(s);
                });
        org.mockito.Mockito.lenient().when(studentRepository.findById(STUDENT_ID))
                .thenReturn(Mono.just(student()));
        org.mockito.Mockito.lenient().when(studentRepository.findAllById(any(Iterable.class)))
                .thenReturn(Flux.just(student()));
        org.mockito.Mockito.lenient().when(sessionRepository.findAllById(any(Iterable.class)))
                .thenReturn(Flux.just(attendanceSession()));
    }

    @Test
    @DisplayName("Submitting the same attendance twice updates the existing mark and sends one notification")
    void shouldMakeAttendanceSubmissionIdempotent() {
        AtomicReference<StudentAttendance> storedAttendance = new AtomicReference<>();

        when(attendanceRepository.findByStudentIdAndSessionId(STUDENT_ID, SESSION_ID))
                .thenAnswer(invocation -> Mono.justOrEmpty(storedAttendance.get()));
        when(attendanceRepository.upsertAttendanceMark(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    StudentAttendance attendance = StudentAttendance.builder()
                            .id(ATTENDANCE_ID)
                            .schoolId(invocation.getArgument(0))
                            .sessionId(invocation.getArgument(1))
                            .studentId(invocation.getArgument(2))
                            .classId(invocation.getArgument(3))
                            .termId(invocation.getArgument(4))
                            .date(invocation.getArgument(5))
                            .status(invocation.getArgument(6))
                            .arrivalTime(invocation.getArgument(7))
                            .broughtBy(invocation.getArgument(8))
                            .departureTime(invocation.getArgument(9))
                            .pickedUpBy(invocation.getArgument(10))
                            .pickUpPersonName(invocation.getArgument(11))
                            .pickUpPersonPhone(invocation.getArgument(12))
                            .notes(invocation.getArgument(13))
                            .markedBy(invocation.getArgument(14))
                            .build();
                    storedAttendance.set(attendance);
                    return Mono.just(attendance);
                });
        stubGuardianAndOutbox();

        MarkAttendanceRequest request = attendanceRequest("PRESENT", "07:55");

        StepVerifier.create(attendanceService.markAttendance(SESSION_ID, request))
                .assertNext(result -> {
                    assertThat(result).hasSize(1);
                    assertThat(result.getFirst().status()).isEqualTo("PRESENT");
                    assertThat(result.getFirst().arrivalTime().toString()).isEqualTo("07:55");
                })
                .verifyComplete();

        StepVerifier.create(attendanceService.markAttendance(SESSION_ID, request))
                .assertNext(result -> {
                    assertThat(result).hasSize(1);
                    assertThat(result.getFirst().attendanceId()).isEqualTo(ATTENDANCE_ID);
                })
                .verifyComplete();

        verify(attendanceRepository, times(2)).upsertAttendanceMark(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
        verify(sessionRepository, times(2)).findByIdForUpdate(SESSION_ID);
        verify(transactionalOperator, times(2)).transactional(any(Mono.class));
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent event = eventCaptor.getValue();

        assertThat(event.getAggregateId()).isEqualTo(ATTENDANCE_ID);
        assertThat(event.getAggregateType()).isEqualTo("STUDENT_ATTENDANCE");
        assertThat(event.getEventType()).isEqualTo("ATTENDANCE_NOTIFICATION");
        assertThat(event.getPayload().path("schoolId").asText()).isEqualTo(SCHOOL_ID.toString());
        assertThat(event.getPayload().path("schoolName").asText()).isEqualTo("Grace International School");
        assertThat(event.getPayload().path("guardianEmail").asText()).isEqualTo("parent@example.com");
    }

    @Test
    @DisplayName("Parent attendance resolves the guardian by Keycloak identity")
    void shouldLoadParentAttendanceUsingKeycloakIdentity() {
        LocalDate today = LocalDate.now();
        SchoolFeeUser parent = SchoolFeeUser.builder()
                .userId(KEYCLOAK_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("PARENT")
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parent));
        when(guardianRepository.findByKeycloakId(KEYCLOAK_USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder()
                        .id(GUARDIAN_ID)
                        .schoolId(SCHOOL_ID)
                        .userId(LOCAL_USER_ID)
                        .build()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                .thenReturn(Flux.just(StudentGuardianLinkProjection.builder()
                        .guardianId(GUARDIAN_ID)
                        .studentId(STUDENT_ID)
                        .canViewAttendance(true)
                        .build()));
        when(attendanceRepository.findByStudentIdAndDate(STUDENT_ID, today))
                .thenReturn(Flux.just(StudentAttendance.builder()
                        .id(ATTENDANCE_ID)
                        .schoolId(SCHOOL_ID)
                        .sessionId(SESSION_ID)
                        .studentId(STUDENT_ID)
                        .classId(CLASS_ID)
                        .termId(TERM_ID)
                        .date(today)
                        .status("PRESENT")
                        .arrivalTime(java.time.LocalTime.of(7, 55))
                        .broughtBy("Mother")
                        .build()));
        when(classRepository.findById(CLASS_ID))
                .thenReturn(Mono.just(ClassEntity.builder().id(CLASS_ID).name("Primary 3A").build()));

        StepVerifier.create(attendanceService.getMyChildrenTodayAttendance())
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    assertThat(results.getFirst().studentId()).isEqualTo(STUDENT_ID);
                    assertThat(results.getFirst().arrivalStatus()).isEqualTo("ON_TIME");
                    assertThat(results.getFirst().broughtBy()).isEqualTo("Mother");
                })
                .verifyComplete();

        verify(guardianRepository).findByKeycloakId(KEYCLOAK_USER_ID);
        verify(guardianRepository, never()).findByUserIdAndDeletedAtIsNull(any());
    }

    private void stubGuardianAndOutbox() {
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(STUDENT_ID))
                .thenReturn(Flux.just(StudentGuardianLink.builder()
                        .guardianId(GUARDIAN_ID)
                        .studentId(STUDENT_ID)
                        .isPrimaryContact(true)
                        .build()));
        when(guardianRepository.findById(GUARDIAN_ID))
                .thenReturn(Mono.just(StudentGuardian.builder()
                        .id(GUARDIAN_ID)
                        .schoolId(SCHOOL_ID)
                        .firstName("Amina")
                        .lastName("Adebayo")
                        .phone("08012345678")
                        .email("parent@example.com")
                        .build()));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> {
                    OutboxEvent event = invocation.getArgument(0);
                    event.setId(UUID.randomUUID());
                    return Mono.just(event);
                });
    }

    private MarkAttendanceRequest attendanceRequest(String status, String arrivalTime) {
        return new MarkAttendanceRequest(List.of(new MarkAttendanceRequest.AttendanceMark(
                STUDENT_ID,
                status,
                arrivalTime,
                "Mother",
                null,
                null,
                null,
                null,
                "Morning register")));
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(KEYCLOAK_USER_ID)
                .schoolId(SCHOOL_ID)
                .schoolName("Grace International School")
                .userType("TEACHER")
                .build();
    }

    private AttendanceSession attendanceSession() {
        return AttendanceSession.builder()
                .id(SESSION_ID)
                .schoolId(SCHOOL_ID)
                .classId(CLASS_ID)
                .termId(TERM_ID)
                .date(LocalDate.of(2026, 6, 18))
                .sessionType("MORNING_ARRIVAL")
                .isComplete(false)
                .build();
    }

    private Student student() {
        return Student.builder()
                .id(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .admissionNumber("STU260001")
                .firstName("Tolu")
                .lastName("Adebayo")
                .currentClassId(CLASS_ID)
                .build();
    }

    @Test
    @DisplayName("Should create session successfully when session does not exist")
    void shouldCreateSessionSuccessfully() {
        CreateAttendanceSessionRequest request = new CreateAttendanceSessionRequest(
                CLASS_ID, TERM_ID, LocalDate.of(2026, 6, 18), "MORNING_ARRIVAL");

        when(sessionRepository.findByClassIdAndDateAndSessionType(CLASS_ID, request.date(), request.sessionType()))
                .thenReturn(Mono.empty());

        StepVerifier.create(attendanceService.createSession(request))
                .assertNext(response -> {
                    assertThat(response.sessionId()).isNotNull();
                    assertThat(response.classId()).isEqualTo(CLASS_ID);
                    assertThat(response.termId()).isEqualTo(TERM_ID);
                    assertThat(response.sessionType()).isEqualTo("MORNING_ARRIVAL");
                    assertThat(response.isComplete()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return existing session when it already exists on createSession")
    void shouldReturnExistingSessionWhenItAlreadyExists() {
        CreateAttendanceSessionRequest request = new CreateAttendanceSessionRequest(
                CLASS_ID, TERM_ID, LocalDate.of(2026, 6, 18), "MORNING_ARRIVAL");

        AttendanceSession existingSession = attendanceSession();
        existingSession.setComplete(true);

        when(sessionRepository.findByClassIdAndDateAndSessionType(CLASS_ID, request.date(), request.sessionType()))
                .thenReturn(Mono.just(existingSession));
        when(attendanceRepository.findBySessionId(SESSION_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(attendanceService.createSession(request))
                .assertNext(response -> {
                    assertThat(response.sessionId()).isEqualTo(SESSION_ID);
                    assertThat(response.isComplete()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle concurrency and fetch existing session when duplicate key error occurs")
    void shouldHandleConcurrencyOnCreateSession() {
        CreateAttendanceSessionRequest request = new CreateAttendanceSessionRequest(
                CLASS_ID, TERM_ID, LocalDate.of(2026, 6, 18), "MORNING_ARRIVAL");

        when(sessionRepository.findByClassIdAndDateAndSessionType(CLASS_ID, request.date(), request.sessionType()))
                .thenReturn(Mono.empty());
        when(sessionRepository.save(any(AttendanceSession.class)))
                .thenReturn(Mono.error(new org.springframework.dao.DataIntegrityViolationException("Duplicate key")));
        
        // Mock the retry find lookup
        AttendanceSession existingSession = attendanceSession();
        when(sessionRepository.findByClassIdAndDateAndSessionType(CLASS_ID, request.date(), request.sessionType()))
                .thenReturn(Mono.empty()) // first time
                .thenReturn(Mono.just(existingSession)); // second time (retry)
        
        when(attendanceRepository.findBySessionId(SESSION_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(attendanceService.createSession(request))
                .assertNext(response -> {
                    assertThat(response.sessionId()).isEqualTo(SESSION_ID);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch sessions for a class and date")
    void shouldGetSessionsWithDate() {
        LocalDate date = LocalDate.of(2026, 6, 18);
        AttendanceSession session = attendanceSession();
        
        when(sessionRepository.findByClassIdAndDate(CLASS_ID, date))
                .thenReturn(Flux.just(session));
        when(attendanceRepository.findBySessionId(SESSION_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(attendanceService.getSessions(CLASS_ID, date))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).sessionId()).isEqualTo(SESSION_ID);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch sessions for current term when date is null")
    void shouldGetSessionsForCurrentTerm() {
        Term activeTerm = Term.builder().id(TERM_ID).name("First Term").build();
        AttendanceSession session = attendanceSession();

        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID))
                .thenReturn(Flux.just(activeTerm));
        when(sessionRepository.findByClassIdAndTermId(CLASS_ID, TERM_ID))
                .thenReturn(Flux.just(session));
        when(attendanceRepository.findBySessionId(SESSION_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(attendanceService.getSessions(CLASS_ID, null))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).sessionId()).isEqualTo(SESSION_ID);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return session marks successfully with correct student details and session type")
    void shouldGetSessionMarksSuccessfully() {
        AttendanceSession session = attendanceSession();
        StudentAttendance mark = StudentAttendance.builder()
                .id(ATTENDANCE_ID)
                .sessionId(SESSION_ID)
                .studentId(STUDENT_ID)
                .status("PRESENT")
                .date(LocalDate.of(2026, 6, 18))
                .arrivalTime(java.time.LocalTime.of(7, 45))
                .build();

        when(sessionRepository.findById(SESSION_ID))
                .thenReturn(Mono.just(session));
        when(attendanceRepository.findBySessionId(SESSION_ID))
                .thenReturn(Flux.just(mark));
        when(studentRepository.findAllById(List.of(STUDENT_ID)))
                .thenReturn(Flux.just(student()));

        StepVerifier.create(attendanceService.getSessionMarks(SESSION_ID))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).studentId()).isEqualTo(STUDENT_ID);
                    assertThat(list.get(0).studentName()).isEqualTo("Tolu Adebayo");
                    assertThat(list.get(0).status()).isEqualTo("PRESENT");
                    assertThat(list.get(0).sessionType()).isEqualTo("MORNING_ARRIVAL");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw exception when session is not found in getSessionMarks")
    void shouldThrowExceptionWhenSessionNotFoundInGetSessionMarks() {
        when(sessionRepository.findById(SESSION_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(attendanceService.getSessionMarks(SESSION_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SESSION_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should update attendance mark successfully with valid times")
    void shouldUpdateAttendanceMarkSuccessfully() {
        UpdateAttendanceRequest request = new UpdateAttendanceRequest(
                "LATE", "08:15", "Father", "14:30", "Uncle", "John Doe", "08099998888", "Excused late arrival"
        );
        StudentAttendance existingMark = StudentAttendance.builder()
                .id(ATTENDANCE_ID)
                .sessionId(SESSION_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .status("PRESENT")
                .date(LocalDate.of(2026, 6, 18))
                .build();

        when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Mono.just(existingMark));
        when(attendanceRepository.save(any(StudentAttendance.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Mono.just(attendanceSession()));

        StepVerifier.create(attendanceService.updateAttendanceMark(ATTENDANCE_ID, request))
                .assertNext(response -> {
                    assertThat(response.attendanceId()).isEqualTo(ATTENDANCE_ID);
                    assertThat(response.status()).isEqualTo("LATE");
                    assertThat(response.arrivalTime()).isEqualTo(LocalTime.of(8, 15));
                    assertThat(response.broughtBy()).isEqualTo("Father");
                    assertThat(response.departureTime()).isEqualTo(LocalTime.of(14, 30));
                    assertThat(response.pickedUpBy()).isEqualTo("Uncle");
                    assertThat(response.pickUpPersonName()).isEqualTo("John Doe");
                    assertThat(response.pickUpPersonPhone()).isEqualTo("08099998888");
                    assertThat(response.notes()).isEqualTo("Excused late arrival");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw SchoolFeeException when updating attendance mark with invalid arrival time format")
    void shouldThrowExceptionForInvalidArrivalTimeFormat() {
        UpdateAttendanceRequest request = new UpdateAttendanceRequest(
                "LATE", "invalid-time", "Father", "14:30", "Uncle", "John Doe", "08099998888", "Excused late arrival"
        );
        StudentAttendance existingMark = StudentAttendance.builder()
                .id(ATTENDANCE_ID)
                .sessionId(SESSION_ID)
                .studentId(STUDENT_ID)
                .status("PRESENT")
                .build();

        when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Mono.just(existingMark));

        StepVerifier.create(attendanceService.updateAttendanceMark(ATTENDANCE_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_TIME_FORMAT");
                    assertThat(error.getMessage()).contains("Arrival time must be in HH:mm format");
                })
                .verify();
    }

    @Test
    @DisplayName("Should throw SchoolFeeException when updating attendance mark with invalid departure time format")
    void shouldThrowExceptionForInvalidDepartureTimeFormat() {
        UpdateAttendanceRequest request = new UpdateAttendanceRequest(
                "LATE", "08:15", "Father", "invalid-time", "Uncle", "John Doe", "08099998888", "Excused late arrival"
        );
        StudentAttendance existingMark = StudentAttendance.builder()
                .id(ATTENDANCE_ID)
                .sessionId(SESSION_ID)
                .studentId(STUDENT_ID)
                .status("PRESENT")
                .build();

        when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Mono.just(existingMark));

        StepVerifier.create(attendanceService.updateAttendanceMark(ATTENDANCE_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_TIME_FORMAT");
                    assertThat(error.getMessage()).contains("Departure time must be in HH:mm format");
                })
                .verify();
    }

    @Test
    @DisplayName("Should fetch student attendance history successfully for teacher")
    void shouldGetStudentAttendanceForTeacher() {
        StudentAttendance mark = StudentAttendance.builder()
                .id(ATTENDANCE_ID)
                .sessionId(SESSION_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .status("PRESENT")
                .date(LocalDate.of(2026, 6, 18))
                .build();

        when(attendanceRepository.findByStudentIdAndTermIdOrderByDateDesc(STUDENT_ID, TERM_ID))
                .thenReturn(Flux.just(mark));

        StepVerifier.create(attendanceService.getStudentAttendance(STUDENT_ID, TERM_ID))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).studentId()).isEqualTo(STUDENT_ID);
                    assertThat(list.get(0).status()).isEqualTo("PRESENT");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch student attendance history successfully for linked parent")
    void shouldGetStudentAttendanceForLinkedParent() {
        SchoolFeeUser parent = SchoolFeeUser.builder()
                .userId(KEYCLOAK_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("PARENT")
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parent));
        when(guardianRepository.findByKeycloakId(KEYCLOAK_USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(GUARDIAN_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndStudentIdAndDeletedAtIsNull(GUARDIAN_ID, STUDENT_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder().guardianId(GUARDIAN_ID).studentId(STUDENT_ID).canViewAttendance(true).build()));

        StudentAttendance mark = StudentAttendance.builder()
                .id(ATTENDANCE_ID)
                .sessionId(SESSION_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .status("PRESENT")
                .date(LocalDate.of(2026, 6, 18))
                .build();

        when(attendanceRepository.findByStudentIdAndTermIdOrderByDateDesc(STUDENT_ID, TERM_ID))
                .thenReturn(Flux.just(mark));

        StepVerifier.create(attendanceService.getStudentAttendance(STUDENT_ID, TERM_ID))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).studentId()).isEqualTo(STUDENT_ID);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw access denied when parent has no permission to view child's attendance")
    void shouldThrowAccessDeniedWhenParentHasNoPermission() {
        SchoolFeeUser parent = SchoolFeeUser.builder()
                .userId(KEYCLOAK_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("PARENT")
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parent));
        when(guardianRepository.findByKeycloakId(KEYCLOAK_USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(GUARDIAN_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndStudentIdAndDeletedAtIsNull(GUARDIAN_ID, STUDENT_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder().guardianId(GUARDIAN_ID).studentId(STUDENT_ID).canViewAttendance(false).build()));

        StepVerifier.create(attendanceService.getStudentAttendance(STUDENT_ID, TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should fetch student attendance summary successfully for authorized user")
    void shouldGetStudentAttendanceSummarySuccessfully() {
        com.fee.app.schoolfeeapp.student.repository.StudentAttendanceRepository.AttendanceCounts counts =
                mock(com.fee.app.schoolfeeapp.student.repository.StudentAttendanceRepository.AttendanceCounts.class);
        when(counts.getTotal()).thenReturn(10L);
        when(counts.getPresent()).thenReturn(8L);
        when(counts.getAbsent()).thenReturn(1L);
        when(counts.getLate()).thenReturn(1L);

        when(attendanceRepository.getAttendanceCounts(STUDENT_ID, TERM_ID))
                .thenReturn(Mono.just(counts));

        StepVerifier.create(attendanceService.getStudentAttendanceSummary(STUDENT_ID, TERM_ID))
                .assertNext(summary -> {
                    assertThat(summary.totalSchoolDays()).isEqualTo(10);
                    assertThat(summary.daysPresent()).isEqualTo(8);
                    assertThat(summary.daysAbsent()).isEqualTo(1);
                    assertThat(summary.daysLate()).isEqualTo(1);
                    assertThat(summary.attendancePercentage()).isEqualTo(80.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch today's attendance for parent's children with unassigned class fallback")
    void shouldGetMyChildrenTodayAttendanceWithUnassignedClassFallback() {
        LocalDate today = LocalDate.now();
        SchoolFeeUser parent = SchoolFeeUser.builder()
                .userId(KEYCLOAK_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("PARENT")
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parent));
        when(guardianRepository.findByKeycloakId(KEYCLOAK_USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(GUARDIAN_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                .thenReturn(Flux.just(StudentGuardianLinkProjection.builder()
                        .guardianId(GUARDIAN_ID)
                        .studentId(STUDENT_ID)
                        .canViewAttendance(true)
                        .build()));

        // Student has null currentClassId
        Student unassignedStudent = Student.builder()
                .id(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .firstName("Tolu")
                .lastName("Adebayo")
                .currentClassId(null)
                .build();
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Mono.just(unassignedStudent));

        StudentAttendance record = StudentAttendance.builder()
                .id(ATTENDANCE_ID)
                .sessionId(SESSION_ID)
                .studentId(STUDENT_ID)
                .date(today)
                .status("PRESENT")
                .arrivalTime(LocalTime.of(7, 45))
                .build();
        when(attendanceRepository.findByStudentIdAndDate(STUDENT_ID, today)).thenReturn(Flux.just(record));

        StepVerifier.create(attendanceService.getMyChildrenTodayAttendance())
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).studentId()).isEqualTo(STUDENT_ID);
                    assertThat(list.get(0).className()).isEqualTo("Unassigned Class");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch date attendance for parent's child successfully")
    void shouldGetMyChildrenAttendanceForChildSuccessfully() {
        LocalDate date = LocalDate.of(2026, 6, 18);
        SchoolFeeUser parent = SchoolFeeUser.builder()
                .userId(KEYCLOAK_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("PARENT")
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parent));
        when(guardianRepository.findByKeycloakId(KEYCLOAK_USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(GUARDIAN_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndStudentIdAndDeletedAtIsNull(GUARDIAN_ID, STUDENT_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder().guardianId(GUARDIAN_ID).studentId(STUDENT_ID).canViewAttendance(true).build()));

        Student unassignedStudent = Student.builder()
                .id(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .firstName("Tolu")
                .lastName("Adebayo")
                .currentClassId(null)
                .build();
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Mono.just(unassignedStudent));

        StudentAttendance record = StudentAttendance.builder()
                .id(ATTENDANCE_ID)
                .sessionId(SESSION_ID)
                .studentId(STUDENT_ID)
                .date(date)
                .status("PRESENT")
                .arrivalTime(LocalTime.of(7, 45))
                .build();
        when(attendanceRepository.findByStudentIdAndDate(STUDENT_ID, date)).thenReturn(Flux.just(record));

        StepVerifier.create(attendanceService.getMyChildrenAttendance(STUDENT_ID, date))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).studentId()).isEqualTo(STUDENT_ID);
                    assertThat(list.get(0).className()).isEqualTo("Unassigned Class");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw access denied when non-parent calls getMyChildrenTodayAttendance")
    void shouldThrowAccessDeniedForNonParentTodayAttendance() {
        // Teacher is in default setup, but we mock jwtUtils to return teacher specifically just in case
        SchoolFeeUser teacher = SchoolFeeUser.builder()
                .userId(KEYCLOAK_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("TEACHER")
                .build();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(teacher));

        StepVerifier.create(attendanceService.getMyChildrenTodayAttendance())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should throw SchoolFeeException CLASS_NOT_FOUND when class does not exist on getTodayClassAttendance")
    void shouldThrowExceptionWhenClassNotFoundOnTodayAttendance() {
        when(classRepository.findById(CLASS_ID)).thenReturn(Mono.empty());

        StepVerifier.create(attendanceService.getTodayClassAttendance(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("CLASS_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should throw SchoolFeeException ACCESS_DENIED when class belongs to another school on getTodayClassAttendance")
    void shouldThrowExceptionWhenClassBelongsToAnotherSchoolOnTodayAttendance() {
        ClassEntity otherSchoolClass = ClassEntity.builder()
                .id(CLASS_ID)
                .name("Primary 3A")
                .schoolId(UUID.randomUUID()) // different school
                .build();
        when(classRepository.findById(CLASS_ID)).thenReturn(Mono.just(otherSchoolClass));

        StepVerifier.create(attendanceService.getTodayClassAttendance(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                    assertThat(error.getMessage()).contains("Class does not belong to your school");
                })
                .verify();
    }

    @Test
    @DisplayName("Should return today class attendance with all students marked NOT_MARKED when no records exist")
    void shouldGetTodayClassAttendanceWithNoRecords() {
        LocalDate today = LocalDate.now();
        ClassEntity classEntity = ClassEntity.builder()
                .id(CLASS_ID)
                .name("Primary 3A")
                .schoolId(SCHOOL_ID)
                .build();
        Student student1 = Student.builder()
                .id(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .firstName("Tolu")
                .lastName("Adebayo")
                .admissionNumber("STU260001")
                .currentClassId(CLASS_ID)
                .build();
        
        when(classRepository.findById(CLASS_ID)).thenReturn(Mono.just(classEntity));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID))
                .thenReturn(Flux.just(student1));
        when(attendanceRepository.findByClassIdAndDate(CLASS_ID, today))
                .thenReturn(Flux.empty());

        StepVerifier.create(attendanceService.getTodayClassAttendance(CLASS_ID))
                .assertNext(response -> {
                    assertThat(response.classId()).isEqualTo(CLASS_ID);
                    assertThat(response.className()).isEqualTo("Primary 3A");
                    assertThat(response.date()).isEqualTo(today.toString());
                    assertThat(response.totalStudents()).isEqualTo(1);
                    assertThat(response.present()).isEqualTo(0);
                    assertThat(response.absent()).isEqualTo(0);
                    assertThat(response.late()).isEqualTo(0);
                    assertThat(response.notMarked()).isEqualTo(1);
                    assertThat(response.students()).hasSize(1);
                    assertThat(response.students().get(0).studentId()).isEqualTo(STUDENT_ID);
                    assertThat(response.students().get(0).status()).isEqualTo("NOT_MARKED");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return today class attendance with mixed records and verify all count and mapping fallback branches")
    void shouldGetTodayClassAttendanceWithMixedRecordsAndFallbacks() {
        LocalDate today = LocalDate.now();
        ClassEntity classEntity = ClassEntity.builder()
                .id(CLASS_ID)
                .name("Primary 3A")
                .schoolId(SCHOOL_ID)
                .build();

        UUID studentId1 = STUDENT_ID;
        UUID studentId2 = UUID.randomUUID();
        UUID studentId3 = UUID.randomUUID();

        Student student1 = Student.builder()
                .id(studentId1)
                .schoolId(SCHOOL_ID)
                .firstName("Tolu")
                .lastName("Adebayo")
                .admissionNumber("STU260001")
                .currentClassId(CLASS_ID)
                .build();
        Student student2 = Student.builder()
                .id(studentId2)
                .schoolId(SCHOOL_ID)
                .firstName("Funmi")
                .lastName("Adebayo")
                .admissionNumber("STU260002")
                .currentClassId(CLASS_ID)
                .build();
        Student student3 = Student.builder()
                .id(studentId3)
                .schoolId(SCHOOL_ID)
                .firstName("Seyi")
                .lastName("Adebayo")
                .admissionNumber("STU260003")
                .currentClassId(CLASS_ID)
                .build();

        UUID recordId1 = UUID.randomUUID();
        StudentAttendance rec1 = StudentAttendance.builder()
                .id(recordId1)
                .studentId(studentId1)
                .sessionId(SESSION_ID)
                .classId(CLASS_ID)
                .status("PRESENT")
                .date(today)
                .arrivalTime(LocalTime.of(7, 45))
                .build();

        UUID recordId2 = UUID.randomUUID();
        StudentAttendance rec2 = StudentAttendance.builder()
                .id(recordId2)
                .studentId(studentId2)
                .sessionId(SESSION_ID)
                .classId(CLASS_ID)
                .status("LATE")
                .date(today)
                .arrivalTime(LocalTime.of(8, 20))
                .build();

        UUID recordId3 = UUID.randomUUID();
        UUID unknownStudentId = UUID.randomUUID();
        StudentAttendance rec3 = StudentAttendance.builder()
                .id(recordId3)
                .studentId(unknownStudentId)
                .sessionId(SESSION_ID)
                .classId(CLASS_ID)
                .status("ABSENT")
                .date(today)
                .build();

        UUID recordId4 = UUID.randomUUID();
        UUID unknownSessionId = UUID.randomUUID();
        StudentAttendance rec4 = StudentAttendance.builder()
                .id(recordId4)
                .studentId(studentId1)
                .sessionId(unknownSessionId)
                .classId(CLASS_ID)
                .status("PRESENT")
                .date(today)
                .build();

        when(classRepository.findById(CLASS_ID)).thenReturn(Mono.just(classEntity));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID))
                .thenReturn(Flux.just(student1, student2, student3));
        when(attendanceRepository.findByClassIdAndDate(CLASS_ID, today))
                .thenReturn(Flux.just(rec1, rec2, rec3, rec4));

        AttendanceSession morningSession = attendanceSession();
        when(sessionRepository.findAllById(any(Iterable.class)))
                .thenReturn(Flux.just(morningSession));

        StepVerifier.create(attendanceService.getTodayClassAttendance(CLASS_ID))
                .assertNext(response -> {
                    assertThat(response.classId()).isEqualTo(CLASS_ID);
                    assertThat(response.className()).isEqualTo("Primary 3A");
                    assertThat(response.totalStudents()).isEqualTo(3);
                    assertThat(response.present()).isEqualTo(2);
                    assertThat(response.late()).isEqualTo(1);
                    assertThat(response.absent()).isEqualTo(1);
                    assertThat(response.notMarked()).isEqualTo(0);
                    assertThat(response.students()).hasSize(5);

                    AttendanceResponse student3Response = response.students().stream()
                            .filter(s -> studentId3.equals(s.studentId()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(student3Response.status()).isEqualTo("NOT_MARKED");
                    assertThat(student3Response.studentName()).isEqualTo("Seyi Adebayo");

                    AttendanceResponse unknownResponse = response.students().stream()
                            .filter(s -> unknownStudentId.equals(s.studentId()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(unknownResponse.studentName()).isEqualTo("Unknown Student");
                    assertThat(unknownResponse.admissionNumber()).isEqualTo("");
                    assertThat(unknownResponse.status()).isEqualTo("ABSENT");
                    assertThat(unknownResponse.sessionType()).isEqualTo("MORNING_ARRIVAL");

                    AttendanceResponse missingSessionResponse = response.students().stream()
                            .filter(s -> recordId4.equals(s.attendanceId()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(missingSessionResponse.sessionType()).isEqualTo("");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw SchoolFeeException STUDENT_NOT_FOUND when teacher queries student that does not exist")
    void shouldThrowExceptionWhenStudentNotFoundForTeacher() {
        SchoolFeeUser teacher = SchoolFeeUser.builder()
                .userId(KEYCLOAK_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("TEACHER")
                .build();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(teacher));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(attendanceService.getStudentAttendance(STUDENT_ID, TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("STUDENT_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should throw SchoolFeeException ACCESS_DENIED when teacher queries student belonging to another school")
    void shouldThrowExceptionWhenStudentBelongsToAnotherSchoolForTeacher() {
        SchoolFeeUser teacher = SchoolFeeUser.builder()
                .userId(KEYCLOAK_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("TEACHER")
                .build();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(teacher));

        Student otherSchoolStudent = Student.builder()
                .id(STUDENT_ID)
                .schoolId(UUID.randomUUID())
                .firstName("Tolu")
                .lastName("Adebayo")
                .build();
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Mono.just(otherSchoolStudent));

        StepVerifier.create(attendanceService.getStudentAttendance(STUDENT_ID, TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                    assertThat(error.getMessage()).contains("Student does not belong to your school");
                })
                .verify();
    }
}


