package com.fee.app.schoolfeeapp.student.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.dto.request.EnrollStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateStudentRequest;
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
class StudentControllerIntegrationTest {

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

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
    }

    @Nested
    @DisplayName("POST /api/v1/students")
    class EnrollStudentIntegrationTests {

        @Test
        @DisplayName("Should enroll student for school admin")
        void shouldEnrollStudentForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/students")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(cls.getId()))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.studentId").exists()
                    .jsonPath("$.data.admissionNumber").exists()
                    .jsonPath("$.data.firstName").isEqualTo("Ada")
                    .jsonPath("$.data.lastName").isEqualTo("Lovelace")
                    .jsonPath("$.data.classId").isEqualTo(cls.getId().toString())
                    .jsonPath("$.data.className").isEqualTo("Primary 1")
                    .jsonPath("$.data.parentCreated").isEqualTo(true);

            Map<String, Object> savedStudent = fetchOne("""
                    SELECT admission_number, first_name, last_name, gender, current_class_id
                    FROM school.students
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID));
            assertThat(savedStudent.get("admission_number").toString()).matches(admissionNumberPattern(1));
            assertThat(savedStudent)
                    .containsEntry("first_name", "Ada")
                    .containsEntry("last_name", "Lovelace")
                    .containsEntry("gender", "FEMALE")
                    .containsEntry("current_class_id", cls.getId());

            assertThat(fetchOne("""
                    SELECT phone
                    FROM school.student_guardians
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .containsEntry("phone", "2348031234567");
        }

        @Test
        @DisplayName("Should return bad request when class is full")
        void shouldReturnBadRequestWhenClassIsFull() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 1, true);
            seedStudent(SCHOOL_ID, cls.getId(), admissionNumber(1), "Existing", "Student");

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/students")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequestWithoutGuardians(cls.getId()))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("CLASS_FULL")
                    .jsonPath("$.errors[0].field").isEqualTo("classId");
        }

        @Test
        @DisplayName("Should return bad request when session is closed")
        void shouldReturnBadRequestWhenSessionIsClosed() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "COMPLETED");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/students")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequestWithoutGuardians(cls.getId()))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_ALREADY_CLOSED")
                    .jsonPath("$.errors[0].field").isEqualTo("academicSessionId");
        }

        @Test
        @DisplayName("Should return validation error for invalid gender")
        void shouldReturnValidationErrorForInvalidGender() {
            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/students")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new EnrollStudentRequest(
                            "Ada",
                            "Lovelace",
                            null,
                            "female",
                            LocalDate.of(2018, 1, 1),
                            UUID.randomUUID(),
                            List.of(),
                            null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                    .jsonPath("$.errors[0].field").isEqualTo("gender");
        }

        @Test
        @DisplayName("Should reject enrollment without authentication")
        void shouldRejectEnrollmentWithoutAuthentication() {
            webTestClient
                    .post()
                    .uri("/api/v1/students")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequestWithoutGuardians(UUID.randomUUID()))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject enrollment for accountant role")
        void shouldRejectEnrollmentForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .post()
                    .uri("/api/v1/students")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequestWithoutGuardians(UUID.randomUUID()))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/students")
    class StudentReadIntegrationTests {

        @Test
        @DisplayName("Should list students for school admin")
        void shouldListStudentsForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, cls.getId(), admissionNumber(50), "Ada", "Lovelace", "INACTIVE");
            UUID guardianId = seedGuardian(SCHOOL_ID, null, "Grace", "Hopper", "2348031234567");
            seedGuardianLink(SCHOOL_ID, guardianId, studentId, true, "MOTHER");

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/students")
                            .queryParam("classId", cls.getId())
                            .queryParam("status", "ALL")
                            .queryParam("search", "Ada")
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.totalElements").isEqualTo(1)
                    .jsonPath("$.data.content[0].studentId").isEqualTo(studentId.toString())
                    .jsonPath("$.data.content[0].status").isEqualTo("INACTIVE")
                    .jsonPath("$.data.content[0].currentClass.name").isEqualTo("Primary 1")
                    .jsonPath("$.data.content[0].parentName").isEqualTo("Grace Hopper")
                    .jsonPath("$.data.content[0].parentPhone").isEqualTo("2348031234567");
        }

        @Test
        @DisplayName("Should return bad request for invalid student status")
        void shouldReturnBadRequestForInvalidStudentStatus() {
            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/students?status=ARCHIVED&page=0&size=20")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_STATUS")
                    .jsonPath("$.errors[0].field").isEqualTo("status");
        }

        @Test
        @DisplayName("Should return bad request for invalid page")
        void shouldReturnBadRequestForInvalidPage() {
            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/students?page=-1&size=20")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_PAGE_REQUEST")
                    .jsonPath("$.errors[0].field").isEqualTo("page");
        }

        @Test
        @DisplayName("Should get student details for staff")
        void shouldGetStudentDetailsForStaff() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, cls.getId(), admissionNumber(60), "Ada", "Lovelace", "ACTIVE");
            UUID guardianId = seedGuardian(SCHOOL_ID, null, "Grace", "Hopper", "2348031234567");
            seedGuardianLink(SCHOOL_ID, guardianId, studentId, true, "MOTHER");

            authenticatedClient(SCHOOL_ID, "TEACHER", "TEACHER")
                    .get()
                    .uri("/api/v1/students/{studentId}", studentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.studentId").isEqualTo(studentId.toString())
                    .jsonPath("$.data.currentClass.name").isEqualTo("Primary 1")
                    .jsonPath("$.data.parents[0].name").isEqualTo("Grace Hopper")
                    .jsonPath("$.data.parents[0].isPrimaryContact").isEqualTo(true);
        }

        @Test
        @DisplayName("Should get linked parent's student details and children")
        void shouldGetLinkedParentsStudentDetailsAndChildren() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedParentUser(SCHOOL_ID, USER_ID);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, cls.getId(), admissionNumber(70), "Ada", "Lovelace", "ACTIVE");
            UUID guardianId = seedGuardian(SCHOOL_ID, USER_ID, "Grace", "Hopper", "2348031234567");
            seedGuardianLink(SCHOOL_ID, guardianId, studentId, true, "MOTHER");
            WebTestClient parentClient = authenticatedClient(SCHOOL_ID, "PARENT", "PARENT");

            parentClient
                    .get()
                    .uri("/api/v1/students/{studentId}", studentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.studentId").isEqualTo(studentId.toString())
                    .jsonPath("$.data.parents[0].userId").isEqualTo(USER_ID.toString());

            parentClient
                    .get()
                    .uri("/api/v1/students/my-children")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data[0].studentId").isEqualTo(studentId.toString())
                    .jsonPath("$.data[0].currentClass").isEqualTo("Primary 1")
                    .jsonPath("$.data[0].feeStatus.status").isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Should reject unlinked parent details request")
        void shouldRejectUnlinkedParentDetailsRequest() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedParentUser(SCHOOL_ID, USER_ID);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, cls.getId(), admissionNumber(80), "Ada", "Lovelace", "ACTIVE");

            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .get()
                    .uri("/api/v1/students/{studentId}", studentId)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("ACCESS_DENIED");
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/students/{studentId}")
    class UpdateStudentIntegrationTests {

        @Test
        @DisplayName("Should update student for school admin")
        void shouldUpdateStudentForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, session.getId(), "Primary 2", "PRIMARY_2", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, sourceClass.getId(), admissionNumber(90), "Ada", "Lovelace", "ACTIVE");

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/students/{studentId}", studentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateStudentRequest(
                            " Marie ",
                            " ",
                            " Curie ",
                            " female ",
                            LocalDate.of(2017, 2, 3),
                            targetClass.getId(),
                            " suspended ",
                            " Penicillin allergy "))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.studentId").isEqualTo(studentId.toString())
                    .jsonPath("$.data.firstName").isEqualTo("Marie")
                    .jsonPath("$.data.lastName").isEqualTo("Curie")
                    .jsonPath("$.data.currentClassId").isEqualTo(targetClass.getId().toString())
                    .jsonPath("$.data.className").isEqualTo("Primary 2")
                    .jsonPath("$.data.enrollmentStatus").isEqualTo("SUSPENDED");

            assertThat(fetchOne("""
                    SELECT first_name, middle_name, last_name, gender, current_class_id,
                           enrollment_status, medical_notes, updated_by
                    FROM school.students
                    WHERE id = :studentId
                    """, Map.of("studentId", studentId)))
                    .containsEntry("first_name", "Marie")
                    .containsEntry("middle_name", null)
                    .containsEntry("last_name", "Curie")
                    .containsEntry("gender", "FEMALE")
                    .containsEntry("current_class_id", targetClass.getId())
                    .containsEntry("enrollment_status", "SUSPENDED")
                    .containsEntry("medical_notes", "Penicillin allergy")
                    .containsEntry("updated_by", USER_ID);
        }

        @Test
        @DisplayName("Should return bad request for invalid update gender")
        void shouldReturnBadRequestForInvalidUpdateGender() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, cls.getId(), admissionNumber(91), "Ada", "Lovelace", "ACTIVE");

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/students/{studentId}", studentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateStudentRequest(
                            null, null, null, "unknown", null, null, null, null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_GENDER")
                    .jsonPath("$.errors[0].field").isEqualTo("gender");
        }

        @Test
        @DisplayName("Should return bad request when update target class is full")
        void shouldReturnBadRequestWhenUpdateTargetClassIsFull() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, session.getId(), "Primary 2", "PRIMARY_2", 1, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, sourceClass.getId(), admissionNumber(92), "Ada", "Lovelace", "ACTIVE");
            seedStudent(SCHOOL_ID, targetClass.getId(), admissionNumber(93), "Existing", "Student", "ACTIVE");

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/students/{studentId}", studentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateStudentRequest(
                            null, null, null, null, null, targetClass.getId(), null, null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("CLASS_FULL")
                    .jsonPath("$.errors[0].field").isEqualTo("classId");
        }

        @Test
        @DisplayName("Should reject update for accountant role")
        void shouldRejectUpdateForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .put()
                    .uri("/api/v1/students/{studentId}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateStudentRequest(
                            "Marie", null, null, null, null, null, null, null))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    private WebTestClient schoolAdminClient(UUID schoolId) {
        return authenticatedClient(schoolId, "SCHOOL_ADMIN", "SCHOOL_ADMIN");
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

    private EnrollStudentRequest validRequest(UUID classId) {
        return new EnrollStudentRequest(
                "Ada",
                "Lovelace",
                null,
                "FEMALE",
                LocalDate.of(2018, 1, 1),
                classId,
                List.of(new EnrollStudentRequest.GuardianInfo(
                        "Grace",
                        "Hopper",
                        "08031234567",
                        "grace@example.com",
                        "Mother",
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        1)),
                null);
    }

    private EnrollStudentRequest validRequestWithoutGuardians(UUID classId) {
        return new EnrollStudentRequest(
                "Ada",
                "Lovelace",
                null,
                "FEMALE",
                LocalDate.of(2018, 1, 1),
                classId,
                List.of(),
                null);
    }

    private String admissionNumber(long sequence) {
        String year = String.valueOf(LocalDate.now().getYear()).substring(2);
        return String.format("STU%s%04d", year, sequence);
    }

    private String admissionNumberPattern(long sequence) {
        return admissionNumber(sequence) + "[A-F0-9]{4}";
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

    private UUID seedStudent(
            UUID schoolId,
            UUID classId,
            String admissionNumber,
            String firstName,
            String lastName) {
        return seedStudent(schoolId, classId, admissionNumber, firstName, lastName, "ACTIVE");
    }

    private UUID seedStudent(
            UUID schoolId,
            UUID classId,
            String admissionNumber,
            String firstName,
            String lastName,
            String status) {
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
                    :status
                )
                """)
                .bind("studentId", studentId)
                .bind("schoolId", schoolId)
                .bind("admissionNumber", admissionNumber)
                .bind("firstName", firstName)
                .bind("lastName", lastName)
                .bind("classId", classId)
                .bind("enrollmentDate", LocalDate.now())
                .bind("status", status)
                .fetch()
                .rowsUpdated()
                .block();
        return studentId;
    }

    private void seedParentUser(UUID schoolId, UUID userId) {
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
                    'Grace',
                    'Hopper',
                    'PARENT',
                    true
                )
                """)
                .bind("userId", userId)
                .bind("keycloakId", userId)
                .bind("schoolId", schoolId)
                .bind("email", "parent-" + userId + "@gis.edu")
                .bind("phone", "2348031234567")
                .fetch()
                .rowsUpdated()
                .block();
    }

    private UUID seedGuardian(
            UUID schoolId,
            UUID userId,
            String firstName,
            String lastName,
            String phone) {
        UUID guardianId = UUID.randomUUID();
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                INSERT INTO school.student_guardians (
                    id,
                    school_id,
                    user_id,
                    first_name,
                    last_name,
                    phone,
                    is_active
                )
                VALUES (
                    :guardianId,
                    :schoolId,
                    :userId,
                    :firstName,
                    :lastName,
                    :phone,
                    true
                )
                """)
                .bind("guardianId", guardianId)
                .bind("schoolId", schoolId)
                .bind("firstName", firstName)
                .bind("lastName", lastName)
                .bind("phone", phone);
        if (userId == null) {
            spec = spec.bindNull("userId", UUID.class);
        } else {
            spec = spec.bind("userId", userId);
        }
        spec.fetch().rowsUpdated().block();
        return guardianId;
    }

    private UUID seedGuardianLink(
            UUID schoolId,
            UUID guardianId,
            UUID studentId,
            boolean primary,
            String relationship) {
        UUID linkId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO school.student_guardian_links (
                    id,
                    guardian_id,
                    student_id,
                    school_id,
                    relationship,
                    is_primary_contact,
                    contact_priority
                )
                VALUES (
                    :linkId,
                    :guardianId,
                    :studentId,
                    :schoolId,
                    :relationship,
                    :primary,
                    1
                )
                """)
                .bind("linkId", linkId)
                .bind("guardianId", guardianId)
                .bind("studentId", studentId)
                .bind("schoolId", schoolId)
                .bind("relationship", relationship)
                .bind("primary", primary)
                .fetch()
                .rowsUpdated()
                .block();
        return linkId;
    }

    private void cleanDatabase() {
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

    private Map<String, Object> fetchOne(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return spec.fetch().one().block();
    }
}
