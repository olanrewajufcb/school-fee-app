package com.fee.app.schoolfeeapp.student.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.dto.request.EnrollStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.response.UpdateStudentResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StudentServiceImplIntegrationTest {

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
    private StudentServiceImpl studentService;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private AcademicSessionRepository sessionRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID PARENT_USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    @BeforeEach
    void setUp() {
        cleanDatabase();
        reset(jwtUtils, keycloakAdminService, reactiveJwtDecoder);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Nested
    @DisplayName("Enroll Student - Service Integration Tests")
    class EnrollStudentIntegrationTests {

        @Test
        @DisplayName("Should enroll student and create guardian records")
        void shouldEnrollStudentAndCreateGuardianRecords() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);

            StepVerifier.create(studentService.enrollStudent(validRequest(cls.getId(), " Ada ", " Lovelace ")))
                    .assertNext(response -> {
                        assertThat(response.studentId()).isNotNull();
                        assertThat(response.admissionNumber()).matches(admissionNumberPattern(1));
                        assertThat(response.firstName()).isEqualTo("Ada");
                        assertThat(response.lastName()).isEqualTo("Lovelace");
                        assertThat(response.classId()).isEqualTo(cls.getId());
                        assertThat(response.className()).isEqualTo("Primary 1");
                        assertThat(response.parentCreated()).isTrue();
                    })
                    .verifyComplete();

            Map<String, Object> savedStudent = fetchOne("""
                    SELECT admission_number, first_name, last_name, gender, current_class_id,
                           enrollment_status, medical_notes, updated_by
                    FROM school.students
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID));
            assertThat(savedStudent.get("admission_number").toString()).matches(admissionNumberPattern(1));
            assertThat(savedStudent)
                    .containsEntry("first_name", "Ada")
                    .containsEntry("last_name", "Lovelace")
                    .containsEntry("gender", "FEMALE")
                    .containsEntry("current_class_id", cls.getId())
                    .containsEntry("enrollment_status", "ACTIVE")
                    .containsEntry("medical_notes", "Asthma")
                    .containsEntry("updated_by", USER_ID);

            Map<String, Object> guardian = fetchOne("""
                    SELECT id, phone, first_name, last_name, created_by, updated_by
                    FROM school.student_guardians
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID));
            assertThat(guardian)
                    .containsEntry("phone", "2348031234567")
                    .containsEntry("first_name", "Grace")
                    .containsEntry("last_name", "Hopper")
                    .containsEntry("created_by", USER_ID)
                    .containsEntry("updated_by", USER_ID);

            assertThat(fetchOne("""
                    SELECT relationship, is_primary_contact, can_pick_up_child, contact_priority
                    FROM school.student_guardian_links
                    WHERE school_id = :schoolId AND guardian_id = :guardianId
                    """, Map.of("schoolId", SCHOOL_ID, "guardianId", guardian.get("id"))))
                    .containsEntry("relationship", "MOTHER")
                    .containsEntry("is_primary_contact", true)
                    .containsEntry("can_pick_up_child", true)
                    .containsEntry("contact_priority", 1);
        }

        @Test
        @DisplayName("Should not reuse admission number from soft-deleted student")
        void shouldNotReuseAdmissionNumberFromSoftDeletedStudent() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            String deletedAdmissionNumber = admissionNumber(1);
            seedStudent(SCHOOL_ID, cls.getId(), deletedAdmissionNumber, "Deleted", "Student", "ACTIVE", Instant.now());

            StepVerifier.create(studentService.enrollStudent(validRequestWithoutGuardians(cls.getId())))
                    .assertNext(response -> {
                        assertThat(response.admissionNumber()).matches(admissionNumberPattern(1));
                        assertThat(response.admissionNumber()).isNotEqualTo(deletedAdmissionNumber);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reject enrollment into closed session")
        void shouldRejectEnrollmentIntoClosedSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "COMPLETED");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);

            StepVerifier.create(studentService.enrollStudent(validRequestWithoutGuardians(cls.getId())))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                        assertThat(exception.getField()).isEqualTo("academicSessionId");
                    })
                    .verify();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.students
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isZero();
        }

        @Test
        @DisplayName("Should serialize concurrent enrollments and protect class capacity")
        void shouldSerializeConcurrentEnrollmentsAndProtectClassCapacity() throws Exception {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 1, true);

            CompletableFuture<Object> firstEnrollment = enrollAsync(validRequestWithoutGuardians(
                    cls.getId(),
                    "Ada",
                    "Lovelace"));
            CompletableFuture<Object> secondEnrollment = enrollAsync(validRequestWithoutGuardians(
                    cls.getId(),
                    "Katherine",
                    "Johnson"));

            Object firstResult = firstEnrollment.get(10, TimeUnit.SECONDS);
            Object secondResult = secondEnrollment.get(10, TimeUnit.SECONDS);

            List<Object> results = List.of(firstResult, secondResult);
            assertThat(results.stream()
                    .filter(result -> result instanceof SchoolFeeException error
                            && "CLASS_FULL".equals(error.getErrorCode()))
                    .count())
                    .isEqualTo(1);
            assertThat(results.stream()
                    .filter(result -> !(result instanceof Throwable))
                    .count())
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.students
                    WHERE school_id = :schoolId AND current_class_id = :classId
                    """, Map.of("schoolId", SCHOOL_ID, "classId", cls.getId())))
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Student Reads - Service Integration Tests")
    class StudentReadsIntegrationTests {

        @Test
        @DisplayName("Should list all statuses with class and guardian enrichment")
        void shouldListAllStatusesWithClassAndGuardianEnrichment() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, cls.getId(), admissionNumber(50), "Ada", "Lovelace", "INACTIVE", null);
            UUID guardianId = seedGuardian(SCHOOL_ID, null, "Grace", "Hopper", "2348031234567");
            seedGuardianLink(SCHOOL_ID, guardianId, studentId, true, "MOTHER");
            seedStudent(SCHOOL_ID, cls.getId(), admissionNumber(51), "Katherine", "Johnson", "ACTIVE", null);

            StepVerifier.create(studentService.listStudents(
                            cls.getId(), "ALL", "Ada", PageRequest.of(0, 10)))
                    .assertNext(response -> {
                        assertThat(response.totalElements()).isEqualTo(1);
                        assertThat(response.totalPages()).isEqualTo(1);
                        assertThat(response.content()).hasSize(1);
                        assertThat(response.content().getFirst().studentId()).isEqualTo(studentId);
                        assertThat(response.content().getFirst().status()).isEqualTo("INACTIVE");
                        assertThat(response.content().getFirst().currentClass().name()).isEqualTo("Primary 1");
                        assertThat(response.content().getFirst().parentName()).isEqualTo("Grace Hopper");
                        assertThat(response.content().getFirst().parentPhone()).isEqualTo("2348031234567");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reject invalid list status")
        void shouldRejectInvalidListStatus() {
            StepVerifier.create(studentService.listStudents(
                            null, "ARCHIVED", null, PageRequest.of(0, 10)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("INVALID_STATUS");
                        assertThat(exception.getField()).isEqualTo("status");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should get student details for staff")
        void shouldGetStudentDetailsForStaff() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, cls.getId(), admissionNumber(60), "Ada", "Lovelace", "ACTIVE", null);
            UUID guardianId = seedGuardian(SCHOOL_ID, null, "Grace", "Hopper", "2348031234567");
            seedGuardianLink(SCHOOL_ID, guardianId, studentId, true, "MOTHER");

            StepVerifier.create(studentService.getStudentDetails(studentId))
                    .assertNext(response -> {
                        assertThat(response.studentId()).isEqualTo(studentId);
                        assertThat(response.currentClass()).isNotNull();
                        assertThat(response.currentClass().classId()).isEqualTo(cls.getId());
                        assertThat(response.parents()).hasSize(1);
                        assertThat(response.parents().getFirst().name()).isEqualTo("Grace Hopper");
                        assertThat(response.parents().getFirst().isPrimaryContact()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should get details and children for linked parent")
        void shouldGetDetailsAndChildrenForLinkedParent() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedParentUser(SCHOOL_ID, PARENT_USER_ID);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, cls.getId(), admissionNumber(70), "Ada", "Lovelace", "ACTIVE", null);
            UUID guardianId = seedGuardian(SCHOOL_ID, PARENT_USER_ID, "Grace", "Hopper", "2348031234567");
            seedGuardianLink(SCHOOL_ID, guardianId, studentId, true, "MOTHER");
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));

            StepVerifier.create(studentService.getStudentDetails(studentId))
                    .assertNext(response -> {
                        assertThat(response.studentId()).isEqualTo(studentId);
                        assertThat(response.parents()).hasSize(1);
                        assertThat(response.parents().getFirst().userId()).isEqualTo(PARENT_USER_ID);
                    })
                    .verifyComplete();

            StepVerifier.create(studentService.getMyChildren())
                    .assertNext(children -> {
                        assertThat(children).hasSize(1);
                        assertThat(children.getFirst().studentId()).isEqualTo(studentId);
                        assertThat(children.getFirst().currentClass()).isEqualTo("Primary 1");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reject unlinked parent details request")
        void shouldRejectUnlinkedParentDetailsRequest() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedParentUser(SCHOOL_ID, PARENT_USER_ID);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, cls.getId(), admissionNumber(80), "Ada", "Lovelace", "ACTIVE", null);
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));

            StepVerifier.create(studentService.getStudentDetails(studentId))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("Update Student - Service Integration Tests")
    class UpdateStudentIntegrationTests {

        @Test
        @DisplayName("Should update student profile and move to active target class")
        void shouldUpdateStudentProfileAndMoveToActiveTargetClass() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, session.getId(), "Primary 2", "PRIMARY_2", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, sourceClass.getId(), admissionNumber(90), "Ada", "Lovelace", "ACTIVE", null);

            UpdateStudentRequest request = new UpdateStudentRequest(
                    " Marie ",
                    " ",
                    " Curie ",
                    " male ",
                    LocalDate.of(2017, 2, 3),
                    targetClass.getId(),
                    " suspended ",
                    " Penicillin allergy ");

            StepVerifier.create(studentService.updateStudent(studentId, request))
                    .assertNext(response -> {
                        assertThat(response.studentId()).isEqualTo(studentId);
                        assertThat(response.firstName()).isEqualTo("Marie");
                        assertThat(response.lastName()).isEqualTo("Curie");
                        assertThat(response.currentClassId()).isEqualTo(targetClass.getId());
                        assertThat(response.className()).isEqualTo("Primary 2");
                        assertThat(response.enrollmentStatus()).isEqualTo("SUSPENDED");
                        assertThat(response.updatedAt()).isNotNull();
                    })
                    .verifyComplete();

            Map<String, Object> savedStudent = fetchOne("""
                    SELECT first_name, middle_name, last_name, gender, date_of_birth,
                           current_class_id, enrollment_status, medical_notes, updated_by
                    FROM school.students
                    WHERE id = :studentId
                    """, Map.of("studentId", studentId));
            assertThat(savedStudent)
                    .containsEntry("first_name", "Marie")
                    .containsEntry("middle_name", null)
                    .containsEntry("last_name", "Curie")
                    .containsEntry("gender", "MALE")
                    .containsEntry("current_class_id", targetClass.getId())
                    .containsEntry("enrollment_status", "SUSPENDED")
                    .containsEntry("medical_notes", "Penicillin allergy")
                    .containsEntry("updated_by", USER_ID);
        }

        @Test
        @DisplayName("Should serialize concurrent class moves and protect target capacity")
        void shouldSerializeConcurrentClassMovesAndProtectTargetCapacity() throws Exception {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", 30, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, session.getId(), "Primary 2", "PRIMARY_2", 1, true);
            UUID firstStudentId = seedStudent(
                    SCHOOL_ID, sourceClass.getId(), admissionNumber(91), "Ada", "Lovelace", "ACTIVE", null);
            UUID secondStudentId = seedStudent(
                    SCHOOL_ID, sourceClass.getId(), admissionNumber(92), "Katherine", "Johnson", "ACTIVE", null);
            UpdateStudentRequest request = new UpdateStudentRequest(
                    null, null, null, null, null, targetClass.getId(), null, null);

            CompletableFuture<Object> firstUpdate = updateAsync(firstStudentId, request);
            CompletableFuture<Object> secondUpdate = updateAsync(secondStudentId, request);

            Object firstResult = firstUpdate.get(10, TimeUnit.SECONDS);
            Object secondResult = secondUpdate.get(10, TimeUnit.SECONDS);

            List<Object> results = List.of(firstResult, secondResult);
            assertThat(results.stream()
                    .filter(result -> result instanceof SchoolFeeException error
                            && "CLASS_FULL".equals(error.getErrorCode()))
                    .count())
                    .isEqualTo(1);
            assertThat(results.stream()
                    .filter(result -> result instanceof UpdateStudentResponse)
                    .count())
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.students
                    WHERE school_id = :schoolId AND current_class_id = :classId
                    """, Map.of("schoolId", SCHOOL_ID, "classId", targetClass.getId())))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject update into closed target session")
        void shouldRejectUpdateIntoClosedTargetSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession activeSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            AcademicSession closedSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, activeSession.getId(), "Primary 1", "PRIMARY_1", 30, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, closedSession.getId(), "Primary 2", "PRIMARY_2", 30, true);
            UUID studentId = seedStudent(
                    SCHOOL_ID, sourceClass.getId(), admissionNumber(93), "Ada", "Lovelace", "ACTIVE", null);
            UpdateStudentRequest request = new UpdateStudentRequest(
                    null, null, null, null, null, targetClass.getId(), null, null);

            StepVerifier.create(studentService.updateStudent(studentId, request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                    })
                    .verify();
        }
    }

    private CompletableFuture<Object> enrollAsync(EnrollStudentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return studentService.enrollStudent(request).block(Duration.ofSeconds(8));
            } catch (Throwable error) {
                return error;
            }
        });
    }

    private CompletableFuture<Object> updateAsync(UUID studentId, UpdateStudentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return studentService.updateStudent(studentId, request).block(Duration.ofSeconds(8));
            } catch (Throwable error) {
                return error;
            }
        });
    }

    private EnrollStudentRequest validRequest(UUID classId, String firstName, String lastName) {
        return new EnrollStudentRequest(
                firstName,
                lastName,
                null,
                "female",
                LocalDate.of(2018, 1, 1),
                classId,
                List.of(new EnrollStudentRequest.GuardianInfo(
                        " Grace ",
                        " Hopper ",
                        "08031234567",
                        "grace@example.com",
                        "mother",
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        1)),
                " Asthma ");
    }

    private EnrollStudentRequest validRequestWithoutGuardians(UUID classId) {
        return validRequestWithoutGuardians(classId, "Ada", "Lovelace");
    }

    private EnrollStudentRequest validRequestWithoutGuardians(UUID classId, String firstName, String lastName) {
        return new EnrollStudentRequest(
                firstName,
                lastName,
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
            String lastName,
            String status,
            Instant deletedAt) {
        UUID studentId = UUID.randomUUID();
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                INSERT INTO school.students (
                    id,
                    school_id,
                    admission_number,
                    first_name,
                    last_name,
                    current_class_id,
                    enrollment_date,
                    enrollment_status,
                    deleted_at
                )
                VALUES (
                    :studentId,
                    :schoolId,
                    :admissionNumber,
                    :firstName,
                    :lastName,
                    :classId,
                    :enrollmentDate,
                    :status,
                    :deletedAt
                )
                """)
                .bind("studentId", studentId)
                .bind("schoolId", schoolId)
                .bind("admissionNumber", admissionNumber)
                .bind("firstName", firstName)
                .bind("lastName", lastName)
                .bind("classId", classId)
                .bind("enrollmentDate", LocalDate.now())
                .bind("status", status);
        if (deletedAt == null) {
            spec = spec.bindNull("deletedAt", Instant.class);
        } else {
            spec = spec.bind("deletedAt", deletedAt);
        }
        spec.fetch().rowsUpdated().block();
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

    private long countRows(String sql, Map<String, ?> bindings) {
        return ((Number) fetchOne(sql, bindings).get("count")).longValue();
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

    private SchoolFeeUser parentUser() {
        return SchoolFeeUser.builder()
                .userId(PARENT_USER_ID)
                .schoolId(SCHOOL_ID)
                .email("parent@gis.edu")
                .userType("PARENT")
                .roles(Set.of("PARENT"))
                .build();
    }
}
