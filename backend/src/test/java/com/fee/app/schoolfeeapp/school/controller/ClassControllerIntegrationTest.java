package com.fee.app.schoolfeeapp.school.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.dto.request.CreateClassRequest;
import com.fee.app.schoolfeeapp.school.dto.request.PromoteStudentsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.UpdateClassRequest;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class ClassControllerIntegrationTest {

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
    private AcademicSessionRepository sessionRepository;

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
    private static final UUID OTHER_SCHOOL_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID TEACHER_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
    }

    @Nested
    @DisplayName("POST /api/v1/schools/current/classes")
    class CreateClassIntegrationTests {

        @Test
        @DisplayName("Should create class for school admin")
        void shouldCreateClassForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            CreateClassRequest request = new CreateClassRequest(
                    "  Primary 1  ",
                    "  Grade 1  ",
                    "  A  ",
                    session.getId(),
                    TEACHER_ID,
                    35);

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.classId").exists()
                    .jsonPath("$.data.name").isEqualTo("Primary 1")
                    .jsonPath("$.data.gradeLevel").isEqualTo("Grade 1")
                    .jsonPath("$.data.section").isEqualTo("A")
                    .jsonPath("$.data.sessionName").isEqualTo("2025/2026 Academic Year")
                    .jsonPath("$.data.classTeacher.userId").isEqualTo(TEACHER_ID.toString())
                    .jsonPath("$.data.capacity").isEqualTo(35)
                    .jsonPath("$.data.currentEnrollment").isEqualTo(0)
                    .jsonPath("$.data.availableSpots").isEqualTo(35)
                    .jsonPath("$.data.status").isEqualTo("ACTIVE");

            assertThat(fetchOne("""
                    SELECT name, grade_level, section, academic_session_id, class_teacher_id,
                           capacity, is_active
                    FROM school.classes
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .containsEntry("name", "Primary 1")
                    .containsEntry("grade_level", "Grade 1")
                    .containsEntry("section", "A")
                    .containsEntry("academic_session_id", session.getId())
                    .containsEntry("class_teacher_id", TEACHER_ID)
                    .containsEntry("capacity", 35)
                    .containsEntry("is_active", true);
        }

        @Test
        @DisplayName("Should return bad request for duplicate class")
        void shouldReturnBadRequestForDuplicateClass() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            CreateClassRequest request = validRequest(session.getId(), "Primary 1");

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated();

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("DUPLICATE_CLASS")
                    .jsonPath("$.errors[0].field").isEqualTo("name");

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.classes
                    WHERE school_id = :schoolId AND academic_session_id = :sessionId
                    """, Map.of("schoolId", SCHOOL_ID, "sessionId", session.getId())))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should return bad request for inactive school")
        void shouldReturnBadRequestForInactiveSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(session.getId(), "Primary 1"))
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SCHOOL_NOT_FOUND");
        }

        @Test
        @DisplayName("Should return bad request for session in another school")
        void shouldReturnBadRequestForSessionInAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSession = seedSession(OTHER_SCHOOL_ID, "Other Academic Year", true, "ACTIVE");

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(otherSession.getId(), "Primary 1"))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_NOT_IN_SCHOOL");
        }

        @Test
        @DisplayName("Should return bad request for closed session")
        void shouldReturnBadRequestForClosedSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession closedSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(closedSession.getId(), "Primary 1"))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_ALREADY_CLOSED")
                    .jsonPath("$.errors[0].field").isEqualTo("academicSessionId");
        }

        @Test
        @DisplayName("Should return bad request for invalid payload")
        void shouldReturnBadRequestForInvalidPayload() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CreateClassRequest(" ", "Grade 1", null, session.getId(), null, 35))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Should reject create class without authentication")
        void shouldRejectCreateClassWithoutAuthentication() {
            webTestClient
                    .post()
                    .uri("/api/v1/schools/current/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(UUID.randomUUID(), "Primary 1"))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject create class for accountant role")
        void shouldRejectCreateClassForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .post()
                    .uri("/api/v1/schools/current/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(UUID.randomUUID(), "Primary 1"))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/schools/current/classes")
    class ListClassesIntegrationTests {

        @Test
        @DisplayName("Should list active classes for current session")
        void shouldListActiveClassesForCurrentSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession currentSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            AcademicSession previousSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            ClassEntity primaryTwo = seedClass(SCHOOL_ID, currentSession.getId(), "Primary 2", "Grade 2", "A", null, 40, true);
            ClassEntity primaryOne = seedClass(SCHOOL_ID, currentSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            seedClass(SCHOOL_ID, previousSession.getId(), "Old Primary 1", "Grade 1", "A", null, 35, true);
            seedClass(SCHOOL_ID, currentSession.getId(), "Inactive Primary", "Grade 1", "B", null, 35, false);
            seedStudent(SCHOOL_ID, primaryOne.getId(), "ADM-001", "Ada", "Lovelace");
            seedStudent(SCHOOL_ID, primaryOne.getId(), "ADM-002", "Grace", "Hopper");
            UUID deletedStudentId = seedStudent(SCHOOL_ID, primaryOne.getId(), "ADM-003", "Deleted", "Student");
            markStudentDeleted(deletedStudentId);
            seedStudent(SCHOOL_ID, primaryTwo.getId(), "ADM-004", "Katherine", "Johnson");

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/schools/current/classes")
                            .queryParam("sessionId", "current")
                            .queryParam("status", "ACTIVE")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.length()").isEqualTo(2)
                    .jsonPath("$.data[0].classId").isEqualTo(primaryOne.getId().toString())
                    .jsonPath("$.data[0].name").isEqualTo("Primary 1")
                    .jsonPath("$.data[0].sessionName").isEqualTo("2025/2026 Academic Year")
                    .jsonPath("$.data[0].classTeacher.userId").isEqualTo(TEACHER_ID.toString())
                    .jsonPath("$.data[0].currentEnrollment").isEqualTo(2)
                    .jsonPath("$.data[0].availableSpots").isEqualTo(33)
                    .jsonPath("$.data[0].status").isEqualTo("ACTIVE")
                    .jsonPath("$.data[1].classId").isEqualTo(primaryTwo.getId().toString())
                    .jsonPath("$.data[1].currentEnrollment").isEqualTo(1)
                    .jsonPath("$.data[1].availableSpots").isEqualTo(39);
        }

        @Test
        @DisplayName("Should list inactive classes by explicit session and grade")
        void shouldListInactiveClassesByExplicitSessionAndGrade() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity inactiveClass = seedClass(SCHOOL_ID, session.getId(), "Primary 1 Archive", "Grade 1", "B", null, 35, false);

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/schools/current/classes")
                            .queryParam("sessionId", session.getId())
                            .queryParam("gradeLevel", " Grade 1 ")
                            .queryParam("status", "INACTIVE")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.length()").isEqualTo(1)
                    .jsonPath("$.data[0].classId").isEqualTo(inactiveClass.getId().toString())
                    .jsonPath("$.data[0].status").isEqualTo("INACTIVE")
                    .jsonPath("$.data[0].sessionName").isEqualTo("2025/2026 Academic Year");
        }

        @Test
        @DisplayName("Should return empty list when current session is missing")
        void shouldReturnEmptyListWhenCurrentSessionIsMissing() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", false, "ACTIVE");
            seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/schools/current/classes")
                            .queryParam("sessionId", "current")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.length()").isEqualTo(0);
        }

        @Test
        @DisplayName("Should return bad request for invalid session id filter")
        void shouldReturnBadRequestForInvalidSessionIdFilter() {
            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/schools/current/classes")
                            .queryParam("sessionId", "not-a-uuid")
                            .build())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_CLASS_FILTER")
                    .jsonPath("$.errors[0].field").isEqualTo("sessionId");
        }

        @Test
        @DisplayName("Should return bad request for invalid status filter")
        void shouldReturnBadRequestForInvalidStatusFilter() {
            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/schools/current/classes")
                            .queryParam("status", "ARCHIVED")
                            .build())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_CLASS_FILTER")
                    .jsonPath("$.errors[0].field").isEqualTo("status");
        }

        @Test
        @DisplayName("Should return bad request for inactive school")
        void shouldReturnBadRequestForInactiveSchoolOnList() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false);

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools/current/classes")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SCHOOL_NOT_FOUND");
        }

        @Test
        @DisplayName("Should reject list classes without authentication")
        void shouldRejectListClassesWithoutAuthentication() {
            webTestClient
                    .get()
                    .uri("/api/v1/schools/current/classes")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject list classes for student role")
        void shouldRejectListClassesForStudentRole() {
            authenticatedClient(SCHOOL_ID, "STUDENT", "STUDENT")
                    .get()
                    .uri("/api/v1/schools/current/classes")
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/schools/current/classes/{classId}")
    class GetClassDetailsIntegrationTests {

        @Test
        @DisplayName("Should get class details for school admin")
        void shouldGetClassDetailsForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            UUID femaleStudentId = seedStudent(SCHOOL_ID, cls.getId(), "ADM-001", "Ada", "Lovelace");
            UUID maleStudentId = seedStudent(SCHOOL_ID, cls.getId(), "ADM-002", "Alan", "Turing");
            UUID deletedStudentId = seedStudent(SCHOOL_ID, cls.getId(), "ADM-003", "Deleted", "Student");
            setStudentGender(femaleStudentId, "FEMALE");
            setStudentGender(maleStudentId, "MALE");
            setStudentGender(deletedStudentId, "MALE");
            markStudentDeleted(deletedStudentId);

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.classId").isEqualTo(cls.getId().toString())
                    .jsonPath("$.data.name").isEqualTo("Primary 1")
                    .jsonPath("$.data.gradeLevel").isEqualTo("Grade 1")
                    .jsonPath("$.data.section").isEqualTo("A")
                    .jsonPath("$.data.sessionName").isEqualTo("2025/2026 Academic Year")
                    .jsonPath("$.data.classTeacher.userId").isEqualTo(TEACHER_ID.toString())
                    .jsonPath("$.data.capacity").isEqualTo(35)
                    .jsonPath("$.data.currentEnrollment").isEqualTo(2)
                    .jsonPath("$.data.students.length()").isEqualTo(2)
                    .jsonPath("$.data.students[0].studentId").isEqualTo(femaleStudentId.toString())
                    .jsonPath("$.data.students[1].studentId").isEqualTo(maleStudentId.toString())
                    .jsonPath("$.data.statistics.maleCount").isEqualTo(1)
                    .jsonPath("$.data.statistics.femaleCount").isEqualTo(1)
                    .jsonPath("$.data.statistics.pendingFees").isEqualTo(2);
        }

        @Test
        @DisplayName("Should get inactive class details for active school")
        void shouldGetInactiveClassDetailsForActiveSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1 Archive", "Grade 1", "A", TEACHER_ID, 35, false);

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.classId").isEqualTo(cls.getId().toString())
                    .jsonPath("$.data.currentEnrollment").isEqualTo(0)
                    .jsonPath("$.data.students.length()").isEqualTo(0);
        }

        @Test
        @DisplayName("Should return bad request for inactive school")
        void shouldReturnBadRequestForInactiveSchoolOnGetDetails() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SCHOOL_NOT_FOUND");
        }

        @Test
        @DisplayName("Should return bad request for class in another school")
        void shouldReturnBadRequestForClassDetailsInAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSession = seedSession(OTHER_SCHOOL_ID, "Other Academic Year", true, "ACTIVE");
            ClassEntity otherClass = seedClass(OTHER_SCHOOL_ID, otherSession.getId(), "Primary 1", "Grade 1", "A", null, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools/current/classes/{classId}", otherClass.getId())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("CLASS_NOT_FOUND");
        }

        @Test
        @DisplayName("Should reject get class details without authentication")
        void shouldRejectGetClassDetailsWithoutAuthentication() {
            webTestClient
                    .get()
                    .uri("/api/v1/schools/current/classes/{classId}", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject get class details for student role")
        void shouldRejectGetClassDetailsForStudentRole() {
            authenticatedClient(SCHOOL_ID, "STUDENT", "STUDENT")
                    .get()
                    .uri("/api/v1/schools/current/classes/{classId}", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/schools/current/classes/{classId}")
    class UpdateClassIntegrationTests {

        @Test
        @DisplayName("Should update class for school admin")
        void shouldUpdateClassForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            UUID newTeacherId = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890124");

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateClassRequest("  Primary 1 Gold  ", "Grade 1", newTeacherId, 45))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.classId").isEqualTo(cls.getId().toString())
                    .jsonPath("$.data.name").isEqualTo("Primary 1 Gold")
                    .jsonPath("$.data.classTeacher").isEqualTo(newTeacherId.toString())
                    .jsonPath("$.data.capacity").isEqualTo(45)
                    .jsonPath("$.data.updatedAt").exists();

            assertThat(fetchOne("""
                    SELECT name, grade_level, class_teacher_id, capacity, updated_by
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("name", "Primary 1 Gold")
                    .containsEntry("grade_level", "Grade 1")
                    .containsEntry("class_teacher_id", newTeacherId)
                    .containsEntry("capacity", 45)
                    .containsEntry("updated_by", USER_ID);
        }

        @Test
        @DisplayName("Should return bad request when capacity is below enrollment")
        void shouldReturnBadRequestWhenCapacityIsBelowEnrollment() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            seedStudent(SCHOOL_ID, cls.getId(), "ADM-001", "Ada", "Lovelace");
            seedStudent(SCHOOL_ID, cls.getId(), "ADM-002", "Grace", "Hopper");

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateClassRequest(null, null, null, 1))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("CAPACITY_TOO_LOW")
                    .jsonPath("$.errors[0].field").isEqualTo("capacity");

            assertThat(fetchOne("""
                    SELECT capacity
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("capacity", 35);
        }

        @Test
        @DisplayName("Should return bad request for duplicate class name update")
        void shouldReturnBadRequestForDuplicateClassNameUpdate() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, session.getId(), "Primary 2", "Grade 2", "B", null, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/classes/{classId}", targetClass.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateClassRequest("Primary 1", null, null, null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("DUPLICATE_CLASS")
                    .jsonPath("$.errors[0].field").isEqualTo("name");
        }

        @Test
        @DisplayName("Should return bad request for empty update")
        void shouldReturnBadRequestForEmptyUpdate() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_CLASS_UPDATE");
        }

        @Test
        @DisplayName("Should return bad request for inactive school")
        void shouldReturnBadRequestForInactiveSchoolOnUpdate() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateClassRequest("Primary 1 Gold", null, null, null))
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SCHOOL_NOT_FOUND");
        }

        @Test
        @DisplayName("Should return bad request for class in another school")
        void shouldReturnBadRequestForClassInAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSession = seedSession(OTHER_SCHOOL_ID, "Other Academic Year", true, "ACTIVE");
            ClassEntity otherClass = seedClass(OTHER_SCHOOL_ID, otherSession.getId(), "Primary 1", "Grade 1", "A", null, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/classes/{classId}", otherClass.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateClassRequest("Primary 1 Gold", null, null, null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("CLASS_NOT_FOUND");
        }

        @Test
        @DisplayName("Should return bad request for inactive class")
        void shouldReturnBadRequestForInactiveClass() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, false);

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateClassRequest("Primary 1 Gold", null, null, null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("CLASS_NOT_ACTIVE")
                    .jsonPath("$.errors[0].field").isEqualTo("classId");
        }

        @Test
        @DisplayName("Should return bad request for closed session")
        void shouldReturnBadRequestForClosedSessionOnUpdate() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateClassRequest("Primary 1 Gold", null, null, null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_ALREADY_CLOSED")
                    .jsonPath("$.errors[0].field").isEqualTo("academicSessionId");
        }

        @Test
        @DisplayName("Should reject update class without authentication")
        void shouldRejectUpdateClassWithoutAuthentication() {
            webTestClient
                    .put()
                    .uri("/api/v1/schools/current/classes/{classId}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateClassRequest("Primary 1 Gold", null, null, null))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject update class for accountant role")
        void shouldRejectUpdateClassForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .put()
                    .uri("/api/v1/schools/current/classes/{classId}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateClassRequest("Primary 1 Gold", null, null, null))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/schools/current/classes/{classId}")
    class DeactivateClassIntegrationTests {

        @Test
        @DisplayName("Should deactivate empty class for school admin")
        void shouldDeactivateEmptyClassForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .delete()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data").doesNotExist();

            assertThat(fetchOne("""
                    SELECT is_active, updated_by
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("is_active", false)
                    .containsEntry("updated_by", USER_ID);
        }

        @Test
        @DisplayName("Should return bad request when active students are enrolled")
        void shouldReturnBadRequestWhenActiveStudentsAreEnrolled() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            seedStudent(SCHOOL_ID, cls.getId(), "ADM-001", "Ada", "Lovelace");

            schoolAdminClient(SCHOOL_ID)
                    .delete()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("CLASS_HAS_STUDENTS")
                    .jsonPath("$.errors[0].field").isEqualTo("classId");

            assertThat(fetchOne("""
                    SELECT is_active
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("is_active", true);
        }

        @Test
        @DisplayName("Should return bad request for inactive school")
        void shouldReturnBadRequestForInactiveSchoolOnDeactivate() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .delete()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SCHOOL_NOT_FOUND");
        }

        @Test
        @DisplayName("Should return bad request for inactive class")
        void shouldReturnBadRequestForInactiveClassOnDeactivate() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, false);

            schoolAdminClient(SCHOOL_ID)
                    .delete()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("CLASS_NOT_ACTIVE")
                    .jsonPath("$.errors[0].field").isEqualTo("classId");
        }

        @Test
        @DisplayName("Should return bad request for closed session")
        void shouldReturnBadRequestForClosedSessionOnDeactivate() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            schoolAdminClient(SCHOOL_ID)
                    .delete()
                    .uri("/api/v1/schools/current/classes/{classId}", cls.getId())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_ALREADY_CLOSED")
                    .jsonPath("$.errors[0].field").isEqualTo("academicSessionId");
        }

        @Test
        @DisplayName("Should reject deactivate class without authentication")
        void shouldRejectDeactivateClassWithoutAuthentication() {
            webTestClient
                    .delete()
                    .uri("/api/v1/schools/current/classes/{classId}", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject deactivate class for accountant role")
        void shouldRejectDeactivateClassForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .delete()
                    .uri("/api/v1/schools/current/classes/{classId}", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/schools/current/classes/promote")
    class PromoteStudentsIntegrationTests {

        @Test
        @DisplayName("Should promote students for school admin")
        void shouldPromoteStudentsForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession sourceSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            AcademicSession targetSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, sourceSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, targetSession.getId(), "Primary 2", "Grade 2", "A", null, 35, true);
            UUID firstStudentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-001", "Ada", "Lovelace");
            UUID secondStudentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-002", "Grace", "Hopper");

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes/promote")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new PromoteStudentsRequest(
                            sourceClass.getId(),
                            targetClass.getId(),
                            List.of(firstStudentId, secondStudentId),
                            targetSession.getId()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.promotionId").exists()
                    .jsonPath("$.data.fromClass").isEqualTo("Primary 1")
                    .jsonPath("$.data.toClass").isEqualTo("Primary 2")
                    .jsonPath("$.data.studentsPromoted").isEqualTo(2)
                    .jsonPath("$.data.failedPromotions.length()").isEqualTo(0);

            assertThat(countRows("""
                    SELECT COUNT(*)
                    FROM school.students
                    WHERE current_class_id = :classId
                      AND updated_by = :updatedBy
                    """, Map.of("classId", targetClass.getId(), "updatedBy", USER_ID)))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Should return partial failures for invalid selected students")
        void shouldReturnPartialFailuresForInvalidSelectedStudents() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession sourceSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            AcademicSession targetSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, sourceSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, targetSession.getId(), "Primary 2", "Grade 2", "A", null, 35, true);
            UUID validStudentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-001", "Ada", "Lovelace");
            UUID wrongClassStudentId = seedStudent(SCHOOL_ID, targetClass.getId(), "ADM-002", "Grace", "Hopper");
            UUID missingStudentId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes/promote")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new PromoteStudentsRequest(
                            sourceClass.getId(),
                            targetClass.getId(),
                            List.of(validStudentId, missingStudentId, wrongClassStudentId),
                            targetSession.getId()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.studentsPromoted").isEqualTo(1)
                    .jsonPath("$.data.failedPromotions.length()").isEqualTo(2)
                    .jsonPath("$.data.failedPromotions[0].studentId").isEqualTo(missingStudentId.toString())
                    .jsonPath("$.data.failedPromotions[1].studentId").isEqualTo(wrongClassStudentId.toString());

            assertThat(fetchOne("""
                    SELECT current_class_id, updated_by
                    FROM school.students
                    WHERE id = :studentId
                    """, Map.of("studentId", validStudentId)))
                    .containsEntry("current_class_id", targetClass.getId())
                    .containsEntry("updated_by", USER_ID);
        }

        @Test
        @DisplayName("Should return bad request when promotion exceeds target capacity")
        void shouldReturnBadRequestWhenPromotionExceedsTargetCapacity() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession sourceSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            AcademicSession targetSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, sourceSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, targetSession.getId(), "Primary 2", "Grade 2", "A", null, 1, true);
            UUID firstStudentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-001", "Ada", "Lovelace");
            UUID secondStudentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-002", "Grace", "Hopper");
            seedStudent(SCHOOL_ID, targetClass.getId(), "ADM-003", "Katherine", "Johnson");

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes/promote")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new PromoteStudentsRequest(
                            sourceClass.getId(),
                            targetClass.getId(),
                            List.of(firstStudentId, secondStudentId),
                            targetSession.getId()))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INSUFFICIENT_CAPACITY")
                    .jsonPath("$.errors[0].field").isEqualTo("studentIds");

            assertThat(countRows("""
                    SELECT COUNT(*)
                    FROM school.students
                    WHERE current_class_id = :classId
                      AND id IN (:firstStudentId, :secondStudentId)
                    """, Map.of(
                    "classId", sourceClass.getId(),
                    "firstStudentId", firstStudentId,
                    "secondStudentId", secondStudentId)))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Should return bad request for duplicate selected students")
        void shouldReturnBadRequestForDuplicateSelectedStudents() {
            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes/promote")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new PromoteStudentsRequest(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            List.of(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                                    UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")),
                            UUID.randomUUID()))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("DUPLICATE_STUDENTS")
                    .jsonPath("$.errors[0].field").isEqualTo("studentIds");
        }

        @Test
        @DisplayName("Should return bad request when target class is outside new session")
        void shouldReturnBadRequestWhenTargetClassIsOutsideNewSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession sourceSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            AcademicSession targetSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            AcademicSession otherSession = seedSession(SCHOOL_ID, "2026/2027 Academic Year", false, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, sourceSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, otherSession.getId(), "Primary 2", "Grade 2", "A", null, 35, true);
            UUID studentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-001", "Ada", "Lovelace");

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/classes/promote")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new PromoteStudentsRequest(
                            sourceClass.getId(),
                            targetClass.getId(),
                            List.of(studentId),
                            targetSession.getId()))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("TARGET_CLASS_SESSION_MISMATCH")
                    .jsonPath("$.errors[0].field").isEqualTo("newSessionId");
        }

        @Test
        @DisplayName("Should reject promote students without authentication")
        void shouldRejectPromoteStudentsWithoutAuthentication() {
            webTestClient
                    .post()
                    .uri("/api/v1/schools/current/classes/promote")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new PromoteStudentsRequest(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            List.of(UUID.randomUUID()),
                            UUID.randomUUID()))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject promote students for accountant role")
        void shouldRejectPromoteStudentsForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .post()
                    .uri("/api/v1/schools/current/classes/promote")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new PromoteStudentsRequest(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            List.of(UUID.randomUUID()),
                            UUID.randomUUID()))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    private WebTestClient schoolAdminClient(UUID schoolId) {
        return authenticatedClient(schoolId, "SCHOOL_ADMIN", "SCHOOL_ADMIN", "ACCOUNTANT");
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

    private CreateClassRequest validRequest(UUID sessionId, String className) {
        return new CreateClassRequest(
                className,
                "Grade 1",
                "A",
                sessionId,
                TEACHER_ID,
                35);
    }

    private School seedSchool(UUID schoolId, String schoolName, String schoolCode, boolean active) {
        JsonNode paymentConfig = objectMapper.valueToTree(Map.of(
                "paystackPublicKey", "123456",
                "paystackSubaccountCode", schoolCode,
                "acceptedPaymentMethods", List.of("PAYSTACK", "CARD")));
        JsonNode smsConfig = objectMapper.valueToTree(Map.of(
                "provider", "AFRICASTALKING",
                "senderId", schoolCode));

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
                .paymentConfig(paymentConfig)
                .smsConfig(smsConfig)
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

        AcademicSession savedSession = sessionRepository.save(session).block();
        if ("COMPLETED".equals(status)) {
            databaseClient.sql("""
                    UPDATE school.academic_sessions
                    SET closed_at = NOW(), closed_by = :closedBy
                    WHERE id = :sessionId
                    """)
                    .bind("closedBy", USER_ID)
                    .bind("sessionId", savedSession.getId())
                    .fetch()
                    .rowsUpdated()
                    .block();
        }
        return savedSession;
    }

    private ClassEntity seedClass(
            UUID schoolId,
            UUID sessionId,
            String name,
            String gradeLevel,
            String section,
            UUID teacherId,
            int capacity,
            boolean active) {
        ClassEntity cls = ClassEntity.builder()
                .schoolId(schoolId)
                .name(name)
                .gradeLevel(gradeLevel)
                .section(section)
                .academicSessionId(sessionId)
                .classTeacherId(teacherId)
                .capacity(capacity)
                .isActive(active)
                .build();

        return classRepository.save(cls).block();
    }

    private UUID seedStudent(
            UUID schoolId,
            UUID classId,
            String admissionNumber,
            String firstName,
            String lastName) {
        UUID studentId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO school.students (
                    id,
                    school_id,
                    admission_number,
                    first_name,
                    last_name,
                    current_class_id,
                    enrollment_date,
                    enrollment_status
                )
                VALUES (
                    :studentId,
                    :schoolId,
                    :admissionNumber,
                    :firstName,
                    :lastName,
                    :classId,
                    :enrollmentDate,
                    'ACTIVE'
                )
                """)
                .bind("studentId", studentId)
                .bind("schoolId", schoolId)
                .bind("admissionNumber", admissionNumber)
                .bind("firstName", firstName)
                .bind("lastName", lastName)
                .bind("classId", classId)
                .bind("enrollmentDate", LocalDate.of(2025, 9, 8))
                .fetch()
                .rowsUpdated()
                .block();
        return studentId;
    }

    private void markStudentDeleted(UUID studentId) {
        databaseClient.sql("""
                UPDATE school.students
                SET deleted_at = NOW(), deleted_by = :deletedBy
                WHERE id = :studentId
                """)
                .bind("deletedBy", USER_ID)
                .bind("studentId", studentId)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void setStudentGender(UUID studentId, String gender) {
        databaseClient.sql("""
                UPDATE school.students
                SET gender = :gender
                WHERE id = :studentId
                """)
                .bind("gender", gender)
                .bind("studentId", studentId)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void cleanDatabase() {
        databaseClient.sql("DELETE FROM outbox.outbox_events").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notifications").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notification_templates").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_structure_classes").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_categories").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.student_class_history").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.students").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.classes").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.terms").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.academic_sessions").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.schools").fetch().rowsUpdated().block();
    }

    private long countRows(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return ((Number) spec
                .map((row, metadata) -> row.get("count"))
                .one()
                .block()).longValue();
    }

    private Map<String, Object> fetchOne(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return spec.fetch().one().block();
    }
}
