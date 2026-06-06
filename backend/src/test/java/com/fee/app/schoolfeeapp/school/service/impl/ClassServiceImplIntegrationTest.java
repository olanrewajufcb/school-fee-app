package com.fee.app.schoolfeeapp.school.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.dto.request.CreateClassRequest;
import com.fee.app.schoolfeeapp.school.dto.request.PromoteStudentsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.ClassDetailResponse;
import com.fee.app.schoolfeeapp.school.dto.response.ClassResponse;
import com.fee.app.schoolfeeapp.school.dto.response.UpdateClassRequest;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
class ClassServiceImplIntegrationTest {

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
    private ClassServiceImpl classService;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private AcademicSessionRepository sessionRepository;

    @Autowired
    private SchoolRepository schoolRepository;

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
    private static final UUID OTHER_SCHOOL_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID TEACHER_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");

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
    @DisplayName("Create Class - Service Integration Tests")
    class CreateClassIntegrationTests {

        @Test
        @DisplayName("Should create class in active school session")
        void shouldCreateClassInActiveSchoolSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");

            CreateClassRequest request = new CreateClassRequest(
                    "  Primary 1  ",
                    "  Grade 1  ",
                    "  A  ",
                    session.getId(),
                    TEACHER_ID,
                    35);

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
                        assertThat(response.availableSpots()).isEqualTo(35);
                        assertThat(response.status()).isEqualTo("ACTIVE");
                    })
                    .verifyComplete();

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
        @DisplayName("Should reject duplicate class in same session")
        void shouldRejectDuplicateClassInSameSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            CreateClassRequest request = validRequest(session.getId(), "Primary 1");

            StepVerifier.create(classService.createClass(request))
                    .expectNextCount(1)
                    .verifyComplete();

            StepVerifier.create(classService.createClass(request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_CLASS");
                        assertThat(exception.getField()).isEqualTo("name");
                    })
                    .verify();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.classes
                    WHERE school_id = :schoolId AND academic_session_id = :sessionId
                    """, Map.of("schoolId", SCHOOL_ID, "sessionId", session.getId())))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should allow same class name in different sessions")
        void shouldAllowSameClassNameInDifferentSessions() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession currentSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            AcademicSession nextSession = seedSession(SCHOOL_ID, "2026/2027 Academic Year", false, "ACTIVE");

            StepVerifier.create(classService.createClass(validRequest(currentSession.getId(), "Primary 1")))
                    .expectNextCount(1)
                    .verifyComplete();
            StepVerifier.create(classService.createClass(validRequest(nextSession.getId(), "Primary 1")))
                    .expectNextCount(1)
                    .verifyComplete();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.classes
                    WHERE school_id = :schoolId AND name = 'Primary 1'
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Should reject class creation for inactive school")
        void shouldRejectClassCreationForInactiveSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");

            StepVerifier.create(classService.createClass(validRequest(session.getId(), "Primary 1")))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    })
                    .verify();

            assertThat(countRows("SELECT COUNT(*) AS count FROM school.classes")).isZero();
        }

        @Test
        @DisplayName("Should reject class creation for session in another school")
        void shouldRejectClassCreationForSessionInAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSession = seedSession(OTHER_SCHOOL_ID, "Other Academic Year", true, "ACTIVE");

            StepVerifier.create(classService.createClass(validRequest(otherSession.getId(), "Primary 1")))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_IN_SCHOOL");
                    })
                    .verify();

            assertThat(countRows("SELECT COUNT(*) AS count FROM school.classes")).isZero();
        }

        @Test
        @DisplayName("Should reject class creation in closed session")
        void shouldRejectClassCreationInClosedSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession closedSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");

            StepVerifier.create(classService.createClass(validRequest(closedSession.getId(), "Primary 1")))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                        assertThat(exception.getField()).isEqualTo("academicSessionId");
                    })
                    .verify();

            assertThat(countRows("SELECT COUNT(*) AS count FROM school.classes")).isZero();
        }

        @Test
        @DisplayName("Should not create class after concurrent school deactivation commits")
        void shouldNotCreateClassAfterConcurrentSchoolDeactivationCommits() throws Exception {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");

            CompletableFuture<Throwable> createFuture = null;
            try (Connection lockConnection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {
                lockConnection.setAutoCommit(false);
                lockActiveSchoolRow(lockConnection, SCHOOL_ID);

                createFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        classService.createClass(validRequest(session.getId(), "Primary 1"))
                                .block(Duration.ofSeconds(5));
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                });

                Thread.sleep(300);
                assertThat(createFuture).isNotDone();

                deactivateLockedSchool(lockConnection, SCHOOL_ID);
                lockConnection.commit();

                Throwable error = createFuture.get(5, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(SchoolFeeException.class);
                SchoolFeeException exception = (SchoolFeeException) error;
                assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
            } finally {
                if (createFuture != null && !createFuture.isDone()) {
                    createFuture.cancel(true);
                }
            }

            assertThat(countRows("SELECT COUNT(*) AS count FROM school.classes")).isZero();
        }
    }

    @Nested
    @DisplayName("List Classes - Service Integration Tests")
    class ListClassesIntegrationTests {

        @Test
        @DisplayName("Should list active classes for current session with active enrollment counts")
        void shouldListActiveClassesForCurrentSessionWithActiveEnrollmentCounts() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession currentSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            AcademicSession previousSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            ClassEntity primaryTwo = seedClass(SCHOOL_ID, currentSession.getId(), "Primary 2", "Grade 2", "A", null, 40, true);
            ClassEntity primaryOne = seedClass(SCHOOL_ID, currentSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            seedClass(SCHOOL_ID, previousSession.getId(), "Old Primary 1", "Grade 1", "A", null, 35, true);
            seedClass(SCHOOL_ID, currentSession.getId(), "Inactive Primary", "Grade 1", "B", null, 35, false);
            UUID activeStudent = seedStudent(SCHOOL_ID, primaryOne.getId(), "ADM-001", "Ada", "Lovelace");
            seedStudent(SCHOOL_ID, primaryOne.getId(), "ADM-002", "Grace", "Hopper");
            UUID deletedStudent = seedStudent(SCHOOL_ID, primaryOne.getId(), "ADM-003", "Deleted", "Student");
            markStudentDeleted(deletedStudent);
            seedStudent(SCHOOL_ID, primaryTwo.getId(), "ADM-004", "Katherine", "Johnson");

            StepVerifier.create(classService.listClasses("current", null, "ACTIVE"))
                    .assertNext(responses -> {
                        assertThat(responses).hasSize(2);

                        ClassResponse first = responses.get(0);
                        assertThat(first.classId()).isEqualTo(primaryOne.getId());
                        assertThat(first.name()).isEqualTo("Primary 1");
                        assertThat(first.gradeLevel()).isEqualTo("Grade 1");
                        assertThat(first.sessionName()).isEqualTo("2025/2026 Academic Year");
                        assertThat(first.classTeacher()).isNotNull();
                        assertThat(first.classTeacher().userId()).isEqualTo(TEACHER_ID);
                        assertThat(first.capacity()).isEqualTo(35);
                        assertThat(first.currentEnrollment()).isEqualTo(2);
                        assertThat(first.availableSpots()).isEqualTo(33);
                        assertThat(first.studentIds()).isEmpty();
                        assertThat(first.status()).isEqualTo("ACTIVE");

                        ClassResponse second = responses.get(1);
                        assertThat(second.classId()).isEqualTo(primaryTwo.getId());
                        assertThat(second.currentEnrollment()).isEqualTo(1);
                        assertThat(second.availableSpots()).isEqualTo(39);
                    })
                    .verifyComplete();

            assertThat(fetchOne("""
                    SELECT current_class_id
                    FROM school.students
                    WHERE id = :studentId
                    """, Map.of("studentId", activeStudent)))
                    .containsEntry("current_class_id", primaryOne.getId());
        }

        @Test
        @DisplayName("Should list inactive classes by explicit session and grade")
        void shouldListInactiveClassesByExplicitSessionAndGrade() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity inactiveClass = seedClass(SCHOOL_ID, session.getId(), "Primary 1 Archive", "Grade 1", "B", null, 35, false);

            StepVerifier.create(classService.listClasses(session.getId().toString(), " Grade 1 ", "inactive"))
                    .assertNext(responses -> {
                        assertThat(responses).hasSize(1);
                        assertThat(responses.get(0).classId()).isEqualTo(inactiveClass.getId());
                        assertThat(responses.get(0).status()).isEqualTo("INACTIVE");
                        assertThat(responses.get(0).sessionName()).isEqualTo("2025/2026 Academic Year");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty list when current session is missing")
        void shouldReturnEmptyListWhenCurrentSessionIsMissing() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", false, "ACTIVE");
            seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            StepVerifier.create(classService.listClasses("current", null, null))
                    .assertNext(responses -> assertThat(responses).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reject invalid list status")
        void shouldRejectInvalidListStatus() {
            StepVerifier.create(classService.listClasses(null, null, "ARCHIVED"))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("INVALID_CLASS_FILTER");
                        assertThat(exception.getField()).isEqualTo("status");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject list classes for inactive school")
        void shouldRejectListClassesForInactiveSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false);

            StepVerifier.create(classService.listClasses(null, null, "ACTIVE"))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject explicit session in another school")
        void shouldRejectExplicitSessionInAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSession = seedSession(OTHER_SCHOOL_ID, "Other Academic Year", true, "ACTIVE");

            StepVerifier.create(classService.listClasses(otherSession.getId().toString(), null, "ACTIVE"))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_IN_SCHOOL");
                        assertThat(exception.getField()).isEqualTo("sessionId");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("Get Class Details - Service Integration Tests")
    class GetClassDetailsIntegrationTests {

        @Test
        @DisplayName("Should get class details with active non-deleted students")
        void shouldGetClassDetailsWithActiveNonDeletedStudents() {
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

            StepVerifier.create(classService.getClassDetails(cls.getId()))
                    .assertNext(response -> {
                        assertThat(response.classId()).isEqualTo(cls.getId());
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
                                .containsExactly(femaleStudentId, maleStudentId);
                        assertThat(response.statistics().maleCount()).isEqualTo(1);
                        assertThat(response.statistics().femaleCount()).isEqualTo(1);
                        assertThat(response.statistics().pendingFees()).isEqualTo(2);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should get inactive class details for active school")
        void shouldGetInactiveClassDetailsForActiveSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1 Archive", "Grade 1", "A", TEACHER_ID, 35, false);

            StepVerifier.create(classService.getClassDetails(cls.getId()))
                    .assertNext(response -> {
                        assertThat(response.classId()).isEqualTo(cls.getId());
                        assertThat(response.name()).isEqualTo("Primary 1 Archive");
                        assertThat(response.currentEnrollment()).isZero();
                        assertThat(response.students()).isEmpty();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reject class details for inactive school")
        void shouldRejectClassDetailsForInactiveSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            StepVerifier.create(classService.getClassDetails(cls.getId()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject class details for class in another school")
        void shouldRejectClassDetailsForClassInAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSession = seedSession(OTHER_SCHOOL_ID, "Other Academic Year", true, "ACTIVE");
            ClassEntity otherClass = seedClass(OTHER_SCHOOL_ID, otherSession.getId(), "Primary 1", "Grade 1", "A", null, 35, true);

            StepVerifier.create(classService.getClassDetails(otherClass.getId()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("CLASS_NOT_FOUND");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject class details when class session belongs to another school")
        void shouldRejectClassDetailsWhenClassSessionBelongsToAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSession = seedSession(OTHER_SCHOOL_ID, "Other Academic Year", true, "ACTIVE");
            ClassEntity classWithOtherSchoolSession = seedClass(
                    SCHOOL_ID,
                    otherSession.getId(),
                    "Cross Session Class",
                    "Grade 1",
                    "A",
                    TEACHER_ID,
                    35,
                    true);

            StepVerifier.create(classService.getClassDetails(classWithOtherSchoolSession.getId()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_IN_SCHOOL");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should not return details after concurrent school deactivation commits")
        void shouldNotReturnDetailsAfterConcurrentSchoolDeactivationCommits() throws Exception {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            CompletableFuture<Throwable> detailsFuture = null;
            try (Connection lockConnection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {
                lockConnection.setAutoCommit(false);
                lockActiveSchoolRow(lockConnection, SCHOOL_ID);

                detailsFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        classService.getClassDetails(cls.getId()).block(Duration.ofSeconds(5));
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                });

                Thread.sleep(300);
                assertThat(detailsFuture).isNotDone();

                deactivateLockedSchool(lockConnection, SCHOOL_ID);
                lockConnection.commit();

                Throwable error = detailsFuture.get(5, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(SchoolFeeException.class);
                SchoolFeeException exception = (SchoolFeeException) error;
                assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
            } finally {
                if (detailsFuture != null && !detailsFuture.isDone()) {
                    detailsFuture.cancel(true);
                }
            }
        }
    }

    @Nested
    @DisplayName("Update Class - Service Integration Tests")
    class UpdateClassIntegrationTests {

        @Test
        @DisplayName("Should update class fields in active school session")
        void shouldUpdateClassFieldsInActiveSchoolSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            UUID newTeacherId = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890124");

            StepVerifier.create(classService.updateClass(
                            cls.getId(),
                            new UpdateClassRequest("  Primary 1 Gold  ", "  Grade 1  ", newTeacherId, 45)))
                    .assertNext(response -> {
                        assertThat(response.classId()).isEqualTo(cls.getId());
                        assertThat(response.name()).isEqualTo("Primary 1 Gold");
                        assertThat(response.classTeacher()).isEqualTo(newTeacherId.toString());
                        assertThat(response.capacity()).isEqualTo(45);
                        assertThat(response.updatedAt()).isNotNull();
                    })
                    .verifyComplete();

            Map<String, Object> updatedClass = fetchOne("""
                    SELECT name, grade_level, class_teacher_id, capacity, updated_by, updated_at, version
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId()));
            assertThat(updatedClass)
                    .containsEntry("name", "Primary 1 Gold")
                    .containsEntry("grade_level", "Grade 1")
                    .containsEntry("class_teacher_id", newTeacherId)
                    .containsEntry("capacity", 45)
                    .containsEntry("updated_by", USER_ID);
            assertThat(updatedClass.get("updated_at")).isNotNull();
            assertThat(((Number) updatedClass.get("version")).intValue()).isGreaterThan(cls.getVersion());
        }

        @Test
        @DisplayName("Should update class name without changing capacity")
        void shouldUpdateClassNameWithoutChangingCapacity() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            StepVerifier.create(classService.updateClass(
                            cls.getId(),
                            new UpdateClassRequest("Primary 1 Blue", null, null, null)))
                    .assertNext(response -> {
                        assertThat(response.name()).isEqualTo("Primary 1 Blue");
                        assertThat(response.capacity()).isEqualTo(35);
                    })
                    .verifyComplete();

            assertThat(fetchOne("""
                    SELECT name, grade_level, class_teacher_id, capacity
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("name", "Primary 1 Blue")
                    .containsEntry("grade_level", "Grade 1")
                    .containsEntry("class_teacher_id", TEACHER_ID)
                    .containsEntry("capacity", 35);
        }

        @Test
        @DisplayName("Should reject class update when capacity is below current enrollment")
        void shouldRejectClassUpdateWhenCapacityIsBelowCurrentEnrollment() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            seedStudent(SCHOOL_ID, cls.getId(), "ADM-001", "Ada", "Lovelace");
            seedStudent(SCHOOL_ID, cls.getId(), "ADM-002", "Grace", "Hopper");

            StepVerifier.create(classService.updateClass(
                            cls.getId(),
                            new UpdateClassRequest(null, null, null, 1)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("CAPACITY_TOO_LOW");
                        assertThat(exception.getField()).isEqualTo("capacity");
                    })
                    .verify();

            assertThat(fetchOne("""
                    SELECT capacity
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("capacity", 35);
        }

        @Test
        @DisplayName("Should reject duplicate class name update in same session")
        void shouldRejectDuplicateClassNameUpdateInSameSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, session.getId(), "Primary 2", "Grade 2", "B", null, 35, true);

            StepVerifier.create(classService.updateClass(
                            targetClass.getId(),
                            new UpdateClassRequest("Primary 1", null, null, null)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_CLASS");
                        assertThat(exception.getField()).isEqualTo("name");
                    })
                    .verify();

            assertThat(fetchOne("""
                    SELECT name
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", targetClass.getId())))
                    .containsEntry("name", "Primary 2");
        }

        @Test
        @DisplayName("Should reject class update for inactive school")
        void shouldRejectClassUpdateForInactiveSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            StepVerifier.create(classService.updateClass(
                            cls.getId(),
                            new UpdateClassRequest("Primary 1 Gold", null, null, null)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject class update for inactive class")
        void shouldRejectClassUpdateForInactiveClass() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, false);

            StepVerifier.create(classService.updateClass(
                            cls.getId(),
                            new UpdateClassRequest("Primary 1 Gold", null, null, null)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("CLASS_NOT_ACTIVE");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject class update in closed session")
        void shouldRejectClassUpdateInClosedSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            StepVerifier.create(classService.updateClass(
                            cls.getId(),
                            new UpdateClassRequest("Primary 1 Gold", null, null, null)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                        assertThat(exception.getField()).isEqualTo("academicSessionId");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should not update class after concurrent school deactivation commits")
        void shouldNotUpdateClassAfterConcurrentSchoolDeactivationCommits() throws Exception {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            CompletableFuture<Throwable> updateFuture = null;
            try (Connection lockConnection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {
                lockConnection.setAutoCommit(false);
                lockActiveSchoolRow(lockConnection, SCHOOL_ID);

                updateFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        classService.updateClass(
                                        cls.getId(),
                                        new UpdateClassRequest("Should Not Persist", null, null, null))
                                .block(Duration.ofSeconds(5));
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                });

                Thread.sleep(300);
                assertThat(updateFuture).isNotDone();

                deactivateLockedSchool(lockConnection, SCHOOL_ID);
                lockConnection.commit();

                Throwable error = updateFuture.get(5, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(SchoolFeeException.class);
                SchoolFeeException exception = (SchoolFeeException) error;
                assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
            } finally {
                if (updateFuture != null && !updateFuture.isDone()) {
                    updateFuture.cancel(true);
                }
            }

            assertThat(fetchOne("""
                    SELECT name
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("name", "Primary 1");
        }
    }

    @Nested
    @DisplayName("Deactivate Class - Service Integration Tests")
    class DeactivateClassIntegrationTests {

        @Test
        @DisplayName("Should deactivate empty class in active school session")
        void shouldDeactivateEmptyClassInActiveSchoolSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            StepVerifier.create(classService.deactivateClass(cls.getId()))
                    .verifyComplete();

            Map<String, Object> deactivatedClass = fetchOne("""
                    SELECT is_active, updated_by, updated_at, version
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId()));
            assertThat(deactivatedClass)
                    .containsEntry("is_active", false)
                    .containsEntry("updated_by", USER_ID);
            assertThat(deactivatedClass.get("updated_at")).isNotNull();
            assertThat(((Number) deactivatedClass.get("version")).intValue()).isGreaterThan(cls.getVersion());
        }

        @Test
        @DisplayName("Should reject deactivation when active students are enrolled")
        void shouldRejectDeactivationWhenActiveStudentsAreEnrolled() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            seedStudent(SCHOOL_ID, cls.getId(), "ADM-001", "Ada", "Lovelace");

            StepVerifier.create(classService.deactivateClass(cls.getId()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("CLASS_HAS_STUDENTS");
                        assertThat(exception.getField()).isEqualTo("classId");
                    })
                    .verify();

            assertThat(fetchOne("""
                    SELECT is_active
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("is_active", true);
        }

        @Test
        @DisplayName("Should reject deactivation for inactive school")
        void shouldRejectDeactivationForInactiveSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            StepVerifier.create(classService.deactivateClass(cls.getId()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    })
                    .verify();

            assertThat(fetchOne("""
                    SELECT is_active
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("is_active", true);
        }

        @Test
        @DisplayName("Should reject deactivation for inactive class")
        void shouldRejectDeactivationForInactiveClass() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, false);

            StepVerifier.create(classService.deactivateClass(cls.getId()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("CLASS_NOT_ACTIVE");
                        assertThat(exception.getField()).isEqualTo("classId");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject deactivation in closed session")
        void shouldRejectDeactivationInClosedSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            StepVerifier.create(classService.deactivateClass(cls.getId()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                        assertThat(exception.getField()).isEqualTo("academicSessionId");
                    })
                    .verify();

            assertThat(fetchOne("""
                    SELECT is_active
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("is_active", true);
        }

        @Test
        @DisplayName("Should not deactivate after concurrent school deactivation commits")
        void shouldNotDeactivateAfterConcurrentSchoolDeactivationCommits() throws Exception {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            CompletableFuture<Throwable> deactivateFuture = null;
            try (Connection lockConnection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {
                lockConnection.setAutoCommit(false);
                lockActiveSchoolRow(lockConnection, SCHOOL_ID);

                deactivateFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        classService.deactivateClass(cls.getId()).block(Duration.ofSeconds(5));
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                });

                Thread.sleep(300);
                assertThat(deactivateFuture).isNotDone();

                deactivateLockedSchool(lockConnection, SCHOOL_ID);
                lockConnection.commit();

                Throwable error = deactivateFuture.get(5, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(SchoolFeeException.class);
                SchoolFeeException exception = (SchoolFeeException) error;
                assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
            } finally {
                if (deactivateFuture != null && !deactivateFuture.isDone()) {
                    deactivateFuture.cancel(true);
                }
            }

            assertThat(fetchOne("""
                    SELECT is_active
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("is_active", true);
        }

        @Test
        @DisplayName("Should see concurrent student enrollment before deactivating")
        void shouldSeeConcurrentStudentEnrollmentBeforeDeactivating() throws Exception {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);

            CompletableFuture<Throwable> deactivateFuture = null;
            try (Connection enrollmentConnection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {
                enrollmentConnection.setAutoCommit(false);
                insertStudent(
                        enrollmentConnection,
                        SCHOOL_ID,
                        cls.getId(),
                        "ADM-LOCKED",
                        "Concurrent",
                        "Student");

                deactivateFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        classService.deactivateClass(cls.getId()).block(Duration.ofSeconds(5));
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                });

                Thread.sleep(300);
                assertThat(deactivateFuture).isNotDone();

                enrollmentConnection.commit();

                Throwable error = deactivateFuture.get(5, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(SchoolFeeException.class);
                SchoolFeeException exception = (SchoolFeeException) error;
                assertThat(exception.getErrorCode()).isEqualTo("CLASS_HAS_STUDENTS");
                assertThat(exception.getField()).isEqualTo("classId");
            } finally {
                if (deactivateFuture != null && !deactivateFuture.isDone()) {
                    deactivateFuture.cancel(true);
                }
            }

            assertThat(fetchOne("""
                    SELECT is_active
                    FROM school.classes
                    WHERE id = :classId
                    """, Map.of("classId", cls.getId())))
                    .containsEntry("is_active", true);
        }
    }

    @Nested
    @DisplayName("Promote Students - Service Integration Tests")
    class PromoteStudentsIntegrationTests {

        @Test
        @DisplayName("Should promote students to target class in active target session")
        void shouldPromoteStudentsToTargetClassInActiveTargetSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession sourceSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            AcademicSession targetSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, sourceSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, targetSession.getId(), "Primary 2", "Grade 2", "A", null, 35, true);
            UUID firstStudentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-001", "Ada", "Lovelace");
            UUID secondStudentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-002", "Grace", "Hopper");

            StepVerifier.create(classService.promoteStudents(new PromoteStudentsRequest(
                            sourceClass.getId(),
                            targetClass.getId(),
                            List.of(firstStudentId, secondStudentId),
                            targetSession.getId())))
                    .assertNext(response -> {
                        assertThat(response.promotionId()).isNotNull();
                        assertThat(response.fromClass()).isEqualTo("Primary 1");
                        assertThat(response.toClass()).isEqualTo("Primary 2");
                        assertThat(response.studentsPromoted()).isEqualTo(2);
                        assertThat(response.failedPromotions()).isEmpty();
                    })
                    .verifyComplete();

            assertThat(countRows("""
                    SELECT COUNT(*)
                    FROM school.students
                    WHERE current_class_id = :classId
                      AND updated_by = :updatedBy
                      AND version > 0
                    """, Map.of("classId", targetClass.getId(), "updatedBy", USER_ID)))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Should promote valid students and return failures for invalid selected students")
        void shouldPromoteValidStudentsAndReturnFailuresForInvalidSelectedStudents() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession sourceSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            AcademicSession targetSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, sourceSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, targetSession.getId(), "Primary 2", "Grade 2", "A", null, 35, true);
            UUID validStudentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-001", "Ada", "Lovelace");
            UUID wrongClassStudentId = seedStudent(SCHOOL_ID, targetClass.getId(), "ADM-002", "Grace", "Hopper");
            UUID missingStudentId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

            StepVerifier.create(classService.promoteStudents(new PromoteStudentsRequest(
                            sourceClass.getId(),
                            targetClass.getId(),
                            List.of(validStudentId, missingStudentId, wrongClassStudentId),
                            targetSession.getId())))
                    .assertNext(response -> {
                        assertThat(response.studentsPromoted()).isEqualTo(1);
                        assertThat(response.failedPromotions()).hasSize(2);
                        assertThat(response.failedPromotions())
                                .extracting(failure -> failure.studentId())
                                .containsExactly(missingStudentId, wrongClassStudentId);
                    })
                    .verifyComplete();

            assertThat(fetchOne("""
                    SELECT current_class_id, updated_by
                    FROM school.students
                    WHERE id = :studentId
                    """, Map.of("studentId", validStudentId)))
                    .containsEntry("current_class_id", targetClass.getId())
                    .containsEntry("updated_by", USER_ID);
            assertThat(fetchOne("""
                    SELECT current_class_id
                    FROM school.students
                    WHERE id = :studentId
                    """, Map.of("studentId", wrongClassStudentId)))
                    .containsEntry("current_class_id", targetClass.getId());
        }

        @Test
        @DisplayName("Should reject promotion when target class capacity is insufficient")
        void shouldRejectPromotionWhenTargetClassCapacityIsInsufficient() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession sourceSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            AcademicSession targetSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, sourceSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, targetSession.getId(), "Primary 2", "Grade 2", "A", null, 1, true);
            UUID firstStudentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-001", "Ada", "Lovelace");
            UUID secondStudentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-002", "Grace", "Hopper");
            seedStudent(SCHOOL_ID, targetClass.getId(), "ADM-003", "Katherine", "Johnson");

            StepVerifier.create(classService.promoteStudents(new PromoteStudentsRequest(
                            sourceClass.getId(),
                            targetClass.getId(),
                            List.of(firstStudentId, secondStudentId),
                            targetSession.getId())))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("INSUFFICIENT_CAPACITY");
                        assertThat(exception.getField()).isEqualTo("studentIds");
                    })
                    .verify();

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
        @DisplayName("Should reject promotion when target class is outside new session")
        void shouldRejectPromotionWhenTargetClassIsOutsideNewSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession sourceSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            AcademicSession targetSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            AcademicSession otherSession = seedSession(SCHOOL_ID, "2026/2027 Academic Year", false, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, sourceSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, otherSession.getId(), "Primary 2", "Grade 2", "A", null, 35, true);
            UUID studentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-001", "Ada", "Lovelace");

            StepVerifier.create(classService.promoteStudents(new PromoteStudentsRequest(
                            sourceClass.getId(),
                            targetClass.getId(),
                            List.of(studentId),
                            targetSession.getId())))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("TARGET_CLASS_SESSION_MISMATCH");
                        assertThat(exception.getField()).isEqualTo("newSessionId");
                    })
                    .verify();

            assertThat(fetchOne("""
                    SELECT current_class_id
                    FROM school.students
                    WHERE id = :studentId
                    """, Map.of("studentId", studentId)))
                    .containsEntry("current_class_id", sourceClass.getId());
        }

        @Test
        @DisplayName("Should not promote students after concurrent school deactivation commits")
        void shouldNotPromoteStudentsAfterConcurrentSchoolDeactivationCommits() throws Exception {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession sourceSession = seedSession(SCHOOL_ID, "2024/2025 Academic Year", false, "COMPLETED");
            AcademicSession targetSession = seedSession(SCHOOL_ID, "2025/2026 Academic Year", true, "ACTIVE");
            ClassEntity sourceClass = seedClass(SCHOOL_ID, sourceSession.getId(), "Primary 1", "Grade 1", "A", TEACHER_ID, 35, true);
            ClassEntity targetClass = seedClass(SCHOOL_ID, targetSession.getId(), "Primary 2", "Grade 2", "A", null, 35, true);
            UUID studentId = seedStudent(SCHOOL_ID, sourceClass.getId(), "ADM-001", "Ada", "Lovelace");

            CompletableFuture<Throwable> promoteFuture = null;
            try (Connection lockConnection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {
                lockConnection.setAutoCommit(false);
                lockActiveSchoolRow(lockConnection, SCHOOL_ID);

                promoteFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        classService.promoteStudents(new PromoteStudentsRequest(
                                        sourceClass.getId(),
                                        targetClass.getId(),
                                        List.of(studentId),
                                        targetSession.getId()))
                                .block(Duration.ofSeconds(5));
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                });

                Thread.sleep(300);
                assertThat(promoteFuture).isNotDone();

                deactivateLockedSchool(lockConnection, SCHOOL_ID);
                lockConnection.commit();

                Throwable error = promoteFuture.get(5, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(SchoolFeeException.class);
                SchoolFeeException exception = (SchoolFeeException) error;
                assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
            } finally {
                if (promoteFuture != null && !promoteFuture.isDone()) {
                    promoteFuture.cancel(true);
                }
            }

            assertThat(fetchOne("""
                    SELECT current_class_id
                    FROM school.students
                    WHERE id = :studentId
                    """, Map.of("studentId", studentId)))
                    .containsEntry("current_class_id", sourceClass.getId());
        }
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

    private long countRows(String sql) {
        return ((Number) databaseClient.sql(sql)
                .map((row, metadata) -> row.get("count"))
                .one()
                .block()).longValue();
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

    private void lockActiveSchoolRow(Connection connection, UUID schoolId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id
                FROM school.schools
                WHERE id = ? AND is_active = true
                FOR UPDATE
                """)) {
            statement.setObject(1, schoolId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
            }
        }
    }

    private void deactivateLockedSchool(Connection connection, UUID schoolId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE school.schools
                SET is_active = false
                WHERE id = ?
                """)) {
            statement.setObject(1, schoolId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void insertStudent(
            Connection connection,
            UUID schoolId,
            UUID classId,
            String admissionNumber,
            String firstName,
            String lastName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
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
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, schoolId);
            statement.setString(3, admissionNumber);
            statement.setString(4, firstName);
            statement.setString(5, lastName);
            statement.setObject(6, classId);
            statement.setObject(7, LocalDate.of(2025, 9, 8));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
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
