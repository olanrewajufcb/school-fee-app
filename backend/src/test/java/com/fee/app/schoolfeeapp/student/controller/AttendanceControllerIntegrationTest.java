package com.fee.app.schoolfeeapp.student.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.dto.request.CreateAttendanceSessionRequest;
import com.fee.app.schoolfeeapp.student.dto.request.MarkAttendanceRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateAttendanceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class AttendanceControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("school_fee_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                String.format("r2dbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);

        registry.add("spring.flyway.url", () ->
                String.format("jdbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private AcademicSessionRepository academicSessionRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
    }

    @Nested
    @DisplayName("Attendance Session Integration Tests")
    class SessionIntegrationTests {

        @Test
        @DisplayName("Should create and retrieve attendance sessions successfully")
        void shouldCreateAndRetrieveSessions() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedTeacherUser(SCHOOL_ID, USER_ID);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            Term term = seedTerm(session.getId(), "First Term", 1, true);
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);

            CreateAttendanceSessionRequest createRequest = new CreateAttendanceSessionRequest(
                    cls.getId(), term.getId(), LocalDate.of(2026, 6, 18), "MORNING_ARRIVAL");

            // 1. Create a session
            teacherClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/attendance/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createRequest)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.sessionId").exists()
                    .jsonPath("$.data.classId").isEqualTo(cls.getId().toString())
                    .jsonPath("$.data.termId").isEqualTo(term.getId().toString())
                    .jsonPath("$.data.sessionType").isEqualTo("MORNING_ARRIVAL")
                    .jsonPath("$.data.isComplete").isEqualTo(false);

            // 2. Concurrency/Idempotence check: Post same request again, should return existing session gracefully
            teacherClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/attendance/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createRequest)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.sessionId").exists();

            // 3. Get sessions for the class on specified date
            teacherClient(SCHOOL_ID)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/attendance/sessions")
                            .queryParam("classId", cls.getId().toString())
                            .queryParam("date", "2026-06-18")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data").isArray()
                    .jsonPath("$.data[0].classId").isEqualTo(cls.getId().toString());

            // 4. Get sessions without date (should resolve active term and retrieve sessions)
            teacherClient(SCHOOL_ID)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/attendance/sessions")
                            .queryParam("classId", cls.getId().toString())
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data").isArray()
                    .jsonPath("$.data[0].termId").isEqualTo(term.getId().toString());
        }

        @Test
        @DisplayName("Should return forbidden when non-authorized roles request sessions")
        void shouldRejectSessionsForUnauthorizedRoles() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            
            // Parents do not have access to create sessions
            parentClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/attendance/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CreateAttendanceSessionRequest(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), "MORNING_ARRIVAL"))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("Remaining Attendance Integration Tests")
    class RemainingAttendanceIntegrationTests {

        @Test
        @DisplayName("Should mark and update attendance record with time validation checks")
        @SuppressWarnings("unchecked")
        void shouldMarkAndUpdateAttendance() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedTeacherUser(SCHOOL_ID, USER_ID);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            Term term = seedTerm(session.getId(), "First Term", 1, true);
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = UUID.randomUUID();
            seedStudent(studentId, SCHOOL_ID, cls.getId(), "Tolu", "Adebayo", "STU260001");

            // 1. Create session
            CreateAttendanceSessionRequest createRequest = new CreateAttendanceSessionRequest(
                    cls.getId(), term.getId(), LocalDate.of(2026, 6, 18), "MORNING_ARRIVAL");

            Map<String, Object> sessionRes = (Map<String, Object>) teacherClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/attendance/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createRequest)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(Map.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(sessionRes).isNotNull();
            Map<String, Object> dataMap = (Map<String, Object>) sessionRes.get("data");
            UUID sessionId = UUID.fromString(dataMap.get("sessionId").toString());

            // 2. Mark attendance
            MarkAttendanceRequest markRequest = new MarkAttendanceRequest(List.of(
                    new MarkAttendanceRequest.AttendanceMark(
                            studentId, "PRESENT", "07:55", "Mother", null, null, null, null, "On time"
                    )
            ));

            Map<String, Object> markRes = (Map<String, Object>) teacherClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/attendance/sessions/" + sessionId + "/marks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(markRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Map.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(markRes).isNotNull();
            List<Map<String, Object>> marks = (List<Map<String, Object>>) markRes.get("data");
            UUID markId = UUID.fromString(marks.get(0).get("attendanceId").toString());

            // 3. Update attendance mark
            UpdateAttendanceRequest updateRequest = new UpdateAttendanceRequest(
                    "LATE", "08:15", "Father", "14:30", "Uncle", "John Doe", "08099998888", "Late excused"
            );

            teacherClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/attendance/marks/" + markId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updateRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.status").isEqualTo("LATE")
                    .jsonPath("$.data.arrivalTime").isEqualTo("08:15:00")
                    .jsonPath("$.data.departureTime").isEqualTo("14:30:00")
                    .jsonPath("$.data.notes").isEqualTo("Late excused");

            // 4. Time format validation failure check
            UpdateAttendanceRequest invalidRequest = new UpdateAttendanceRequest(
                    "LATE", "invalid-time", "Father", null, null, null, null, "Error format"
            );

            teacherClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/attendance/marks/" + markId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(invalidRequest)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_TIME_FORMAT");
        }

        @Test
        @DisplayName("Should restrict parent access according to guardian link parameters")
        void shouldRestrictParentAccess() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            Term term = seedTerm(session.getId(), "First Term", 1, true);
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);

            // Student A: Parent has canViewAttendance = true
            UUID studentIdA = UUID.randomUUID();
            seedStudent(studentIdA, SCHOOL_ID, cls.getId(), "Tolu", "Adebayo", "STU260001");

            // Student B: Parent has canViewAttendance = false
            UUID studentIdB = UUID.randomUUID();
            seedStudent(studentIdB, SCHOOL_ID, cls.getId(), "Bisi", "Adebayo", "STU260002");

            // Student C: Unlinked student
            UUID studentIdC = UUID.randomUUID();
            seedStudent(studentIdC, SCHOOL_ID, cls.getId(), "Charlie", "Brown", "STU260003");

            // Seed Parent guardian and user link
            UUID parentUserId = UUID.randomUUID();
            seedGuardianUser(SCHOOL_ID, parentUserId);
            UUID guardianId = UUID.randomUUID();
            seedGuardian(guardianId, SCHOOL_ID, parentUserId, "parent@gis.edu", "+2348012345678");

            seedGuardianLink(guardianId, studentIdA, true, true);
            seedGuardianLink(guardianId, studentIdB, false, false);

            // 1. Authorized parent querying student A history & summary
            parentClientMutated(SCHOOL_ID, parentUserId)
                    .get()
                    .uri("/api/v1/attendance/students/" + studentIdA + "?termId=" + term.getId())
                    .exchange()
                    .expectStatus().isOk();

            parentClientMutated(SCHOOL_ID, parentUserId)
                    .get()
                    .uri("/api/v1/attendance/students/" + studentIdA + "/summary?termId=" + term.getId())
                    .exchange()
                    .expectStatus().isOk();

            // 2. Unauthorized parent querying student B (forbidden)
            parentClientMutated(SCHOOL_ID, parentUserId)
                    .get()
                    .uri("/api/v1/attendance/students/" + studentIdB + "?termId=" + term.getId())
                    .exchange()
                    .expectStatus().isBadRequest();

            parentClientMutated(SCHOOL_ID, parentUserId)
                    .get()
                    .uri("/api/v1/attendance/students/" + studentIdB + "/summary?termId=" + term.getId())
                    .exchange()
                    .expectStatus().isBadRequest();

            // 3. Unauthorized parent querying unlinked student C (forbidden)
            parentClientMutated(SCHOOL_ID, parentUserId)
                    .get()
                    .uri("/api/v1/attendance/students/" + studentIdC + "?termId=" + term.getId())
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Should retrieve parent children and specific child details with unassigned class fallback")
        void shouldRetrieveParentChildrenAndFallback() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            Term term = seedTerm(session.getId(), "First Term", 1, true);

            // Student without class (current_class_id is NULL)
            UUID studentId = UUID.randomUUID();
            seedStudent(studentId, SCHOOL_ID, null, "Unassigned", "Child", "STU260009");

            UUID parentUserId = UUID.randomUUID();
            seedGuardianUser(SCHOOL_ID, parentUserId);
            UUID guardianId = UUID.randomUUID();
            seedGuardian(guardianId, SCHOOL_ID, parentUserId, "parent2@gis.edu", "+2348012345679");
            seedGuardianLink(guardianId, studentId, true, true);

            // 1. Get parent today's children attendance list (verifying fallback class name)
            parentClientMutated(SCHOOL_ID, parentUserId)
                    .get()
                    .uri("/api/v1/attendance/my-children")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data[0].studentId").isEqualTo(studentId.toString())
                    .jsonPath("$.data[0].className").isEqualTo("Unassigned Class");

            // 2. Get specific child history (verifying fallback class name)
            parentClientMutated(SCHOOL_ID, parentUserId)
                    .get()
                    .uri("/api/v1/attendance/my-children/" + studentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data[0].studentId").isEqualTo(studentId.toString())
                    .jsonPath("$.data[0].className").isEqualTo("Unassigned Class");
        }

        @Test
        @DisplayName("Should retrieve today's class attendance successfully for teacher")
        void shouldRetrieveTodayClassAttendanceSuccessfully() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedTeacherUser(SCHOOL_ID, USER_ID);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            Term term = seedTerm(session.getId(), "First Term", 1, true);
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = UUID.randomUUID();
            seedStudent(studentId, SCHOOL_ID, cls.getId(), "Tolu", "Adebayo", "STU260001");

            LocalDate today = LocalDate.now();
            
            // Create session
            CreateAttendanceSessionRequest createRequest = new CreateAttendanceSessionRequest(
                    cls.getId(), term.getId(), today, "MORNING_ARRIVAL");

            Map<String, Object> sessionRes = (Map<String, Object>) teacherClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/attendance/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createRequest)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(Map.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(sessionRes).isNotNull();
            Map<String, Object> dataMap = (Map<String, Object>) sessionRes.get("data");
            UUID sessionId = UUID.fromString(dataMap.get("sessionId").toString());

            // Mark attendance
            MarkAttendanceRequest markRequest = new MarkAttendanceRequest(List.of(
                    new MarkAttendanceRequest.AttendanceMark(
                            studentId, "PRESENT", "07:55", "Mother", null, null, null, null, "On time"
                    )
            ));

            teacherClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/attendance/sessions/" + sessionId + "/marks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(markRequest)
                    .exchange()
                    .expectStatus().isOk();

            // Retrieve today's class attendance
            teacherClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/attendance/classes/" + cls.getId() + "/today")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.classId").isEqualTo(cls.getId().toString())
                    .jsonPath("$.data.className").isEqualTo("Primary 1")
                    .jsonPath("$.data.totalStudents").isEqualTo(1)
                    .jsonPath("$.data.present").isEqualTo(1)
                    .jsonPath("$.data.absent").isEqualTo(0)
                    .jsonPath("$.data.late").isEqualTo(0)
                    .jsonPath("$.data.notMarked").isEqualTo(0)
                    .jsonPath("$.data.students[0].studentId").isEqualTo(studentId.toString())
                    .jsonPath("$.data.students[0].status").isEqualTo("PRESENT");
        }
    }

    private WebTestClient teacherClient(UUID schoolId) {
        return authenticatedClient(schoolId, "TEACHER", "TEACHER");
    }

    private WebTestClient parentClient(UUID schoolId) {
        return authenticatedClient(schoolId, "PARENT", "PARENT");
    }

    private WebTestClient authenticatedClient(UUID schoolId, String userType, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        return webTestClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt
                        .subject(USER_ID.toString())
                        .claim("preferred_username", "testuser")
                        .claim("email", "test@school.edu")
                        .claim("given_name", "Test")
                        .claim("family_name", "User")
                        .claim("phone_number", "+2348012345678")
                        .claim("school_id", schoolId != null ? schoolId.toString() : "*")
                        .claim("user_type", userType)
                        .claim("realm_access", Map.of("roles", roleList)))
                .authorities(roleList.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new)));
    }

    private School seedSchool(UUID schoolId, String schoolName, String schoolCode, boolean active) {
        School school = School.builder()
                .id(schoolId)
                .name(schoolName)
                .code(schoolCode)
                .email("hello-" + schoolCode.toLowerCase() + "@gis.edu")
                .phone("+2348012345678")
                .address("12 School Road")
                .city("Lagos")
                .state("Lagos")
                .country("Nigeria")
                .logoUrl("https://cdn.example.com/logo.png")
                .paymentConfig(objectMapper.createObjectNode())
                .smsConfig(objectMapper.createObjectNode())
                .termConfig(objectMapper.createObjectNode())
                .isActive(active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return schoolRepository.save(school).block();
    }

    private AcademicSession seedSession(UUID schoolId, String name, boolean current, String status) {
        AcademicSession session = AcademicSession.builder()
                .schoolId(schoolId)
                .name(name)
                .startDate(LocalDate.of(2025, 9, 8))
                .endDate(LocalDate.of(2026, 9, 7))
                .isCurrent(current)
                .status(status)
                .build();

        return academicSessionRepository.save(session).block();
    }

    private Term seedTerm(UUID sessionId, String name, int termNumber, boolean current) {
        UUID termId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO school.terms (
                    id, session_id, name, term_number, start_date, end_date, is_current
                )
                VALUES (
                    :id, :sessionId, :name, :termNumber, :startDate, :endDate, :current
                )
                """)
                .bind("id", termId)
                .bind("sessionId", sessionId)
                .bind("name", name)
                .bind("termNumber", termNumber)
                .bind("startDate", LocalDate.of(2025, 9, 8))
                .bind("endDate", LocalDate.of(2025, 12, 18))
                .bind("current", current)
                .fetch()
                .rowsUpdated()
                .block();
        return Term.builder().id(termId).name(name).termNumber((short) termNumber).isCurrent(current).build();
    }

    private ClassEntity seedClass(
            UUID schoolId,
            UUID sessionId,
            String name,
            String gradeLevel,
            int capacity,
            boolean active) {
        ClassEntity cls = ClassEntity.builder()
                .schoolId(schoolId)
                .name(name)
                .gradeLevel(gradeLevel)
                .section("A")
                .academicSessionId(sessionId)
                .capacity(capacity)
                .isActive(active)
                .build();

        return classRepository.save(cls).block();
    }

    private void seedTeacherUser(UUID schoolId, UUID userId) {
        databaseClient.sql("""
                INSERT INTO auth.users (
                    id,
                    keycloak_id,
                    school_id,
                    email,
                    phone,
                    first_name,
                    last_name,
                    user_type,
                    is_active
                )
                VALUES (
                    :userId,
                    :keycloakId,
                    :schoolId,
                    :email,
                    :phone,
                    'Teacher',
                    'Test',
                    'TEACHER',
                    true
                )
                """)
                .bind("userId", userId)
                .bind("keycloakId", userId)
                .bind("schoolId", schoolId)
                .bind("email", "teacher-" + userId + "@gis.edu")
                .bind("phone", "2348031234567")
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void cleanDatabase() {
        databaseClient.sql("DELETE FROM attendance.student_attendance").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM attendance.attendance_sessions").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM outbox.outbox_events").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notifications").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notification_templates").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_structure_classes").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_categories").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.student_guardian_links").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.student_class_history").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.students").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.student_guardians").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM auth.user_school_roles").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM auth.users").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.classes").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.terms").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.academic_sessions").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.schools").fetch().rowsUpdated().block();
    }

    private void seedStudent(UUID studentId, UUID schoolId, UUID classId, String firstName, String lastName, String admissionNumber) {
        var spec = databaseClient.sql("""
                INSERT INTO school.students (
                    id, school_id, current_class_id, first_name, last_name, admission_number, enrollment_date, enrollment_status
                )
                VALUES (
                    :id, :schoolId, :classId, :firstName, :lastName, :admissionNumber, :enrollmentDate, 'ACTIVE'
                )
                """)
                .bind("id", studentId)
                .bind("schoolId", schoolId)
                .bind("firstName", firstName)
                .bind("lastName", lastName)
                .bind("admissionNumber", admissionNumber)
                .bind("enrollmentDate", LocalDate.now());

        if (classId != null) {
            spec = spec.bind("classId", classId);
        } else {
            spec = spec.bindNull("classId", UUID.class);
        }

        spec.fetch()
                .rowsUpdated()
                .block();
    }

    private void seedGuardian(UUID guardianId, UUID schoolId, UUID keycloakId, String email, String phone) {
        databaseClient.sql("""
                INSERT INTO school.student_guardians (
                    id, school_id, user_id, first_name, last_name, email, phone, is_active
                )
                VALUES (
                    :id, :schoolId, :keycloakId, 'Guardian', 'Test', :email, :phone, true
                )
                """)
                .bind("id", guardianId)
                .bind("schoolId", schoolId)
                .bind("keycloakId", keycloakId)
                .bind("email", email)
                .bind("phone", phone)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedGuardianLink(UUID guardianId, UUID studentId, boolean canViewAttendance, boolean isPrimary) {
        databaseClient.sql("""
                INSERT INTO school.student_guardian_links (
                    id, school_id, guardian_id, student_id, relationship, is_primary_contact, can_view_attendance
                )
                VALUES (
                    :id, :schoolId, :guardianId, :studentId, 'MOTHER', :isPrimary, :canViewAttendance
                )
                """)
                .bind("id", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
                .bind("guardianId", guardianId)
                .bind("studentId", studentId)
                .bind("isPrimary", isPrimary)
                .bind("canViewAttendance", canViewAttendance)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedGuardianUser(UUID schoolId, UUID userId) {
        databaseClient.sql("""
                INSERT INTO auth.users (
                    id,
                    keycloak_id,
                    school_id,
                    email,
                    phone,
                    first_name,
                    last_name,
                    user_type,
                    is_active
                )
                VALUES (
                    :userId,
                    :keycloakId,
                    :schoolId,
                    :email,
                    :phone,
                    'Guardian',
                    'User',
                    'PARENT',
                    true
                )
                """)
                .bind("userId", userId)
                .bind("keycloakId", userId)
                .bind("schoolId", schoolId)
                .bind("email", "parent-" + userId + "@gis.edu")
                .bind("phone", "2348012345678")
                .fetch()
                .rowsUpdated()
                .block();
    }

    private WebTestClient parentClientMutated(UUID schoolId, UUID userId) {
        List<String> roleList = List.of("PARENT");
        return webTestClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt
                        .subject(userId.toString())
                        .claim("preferred_username", "testparent")
                        .claim("email", "parent@school.edu")
                        .claim("given_name", "Parent")
                        .claim("family_name", "User")
                        .claim("phone_number", "+2348012345678")
                        .claim("school_id", schoolId != null ? schoolId.toString() : "*")
                        .claim("user_type", "PARENT")
                        .claim("realm_access", Map.of("roles", roleList)))
                .authorities(roleList.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new)));
    }
}
