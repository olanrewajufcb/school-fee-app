package com.fee.app.schoolfeeapp.result.service.impl;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.result.dto.request.CreateSubjectRequest;
import com.fee.app.schoolfeeapp.result.dto.response.SubjectResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SubjectServiceImplIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("school_fee_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format(
                "r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.url", () -> String.format(
                "jdbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private SubjectServiceImpl subjectService;
    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private JwtUtils jwtUtils;
    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;
    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID SCHOOL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        cleanDatabase();
        reset(jwtUtils, keycloakAdminService, reactiveJwtDecoder);
        seedSchool();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void shouldCreateListAndUpdateNormalizedSubjects() {
        SubjectResponse created = subjectService.createSubject(
                new CreateSubjectRequest("  English   Language ", " eng ", " languages "))
                .block();
        subjectService.createSubject(
                new CreateSubjectRequest("Agricultural Science", "AGR", "science"))
                .block();

        assertThat(created).isNotNull();
        assertThat(created.name()).isEqualTo("English Language");
        assertThat(created.code()).isEqualTo("ENG");
        assertThat(created.category()).isEqualTo("LANGUAGES");

        StepVerifier.create(subjectService.listSubjects())
                .assertNext(subjects -> assertThat(subjects)
                        .extracting(SubjectResponse::name)
                        .containsExactly("Agricultural Science", "English Language"))
                .verifyComplete();

        StepVerifier.create(subjectService.updateSubject(
                        created.subjectId(),
                        new CreateSubjectRequest(" English Studies ", " els ", " languages ")))
                .assertNext(updated -> {
                    assertThat(updated.subjectId()).isEqualTo(created.subjectId());
                    assertThat(updated.name()).isEqualTo("English Studies");
                    assertThat(updated.code()).isEqualTo("ELS");
                    assertThat(updated.category()).isEqualTo("LANGUAGES");
                })
                .verifyComplete();

        Map<String, Object> row = databaseClient.sql("""
                        SELECT name, code, category, version
                        FROM result.subjects
                        WHERE id = :id
                        """)
                .bind("id", created.subjectId())
                .fetch()
                .one()
                .block();
        assertThat(row).isNotNull();
        assertThat(row.get("name")).isEqualTo("English Studies");
        assertThat(row.get("code")).isEqualTo("ELS");
        assertThat(((Number) row.get("version")).intValue()).isEqualTo(1);
    }

    @Test
    void shouldAllowOnlyOneWinnerDuringConcurrentDuplicateCreation() {
        List<Mono<Signal<SubjectResponse>>> attempts = Flux.range(0, 8)
                .map(index -> subjectService.createSubject(new CreateSubjectRequest(
                                index % 2 == 0 ? "Mathematics" : " mathematics ",
                                "MTH-" + index,
                                "Science"))
                        .materialize())
                .collectList()
                .block();

        assertThat(attempts).isNotNull();
        List<Signal<SubjectResponse>> signals = Flux.merge(attempts).collectList().block();

        assertThat(signals).isNotNull();
        assertThat(signals.stream().filter(Signal::isOnNext)).hasSize(1);
        assertThat(signals.stream().filter(Signal::isOnError)).hasSize(7);
        assertThat(signals.stream()
                .filter(Signal::isOnError)
                .map(Signal::getThrowable))
                .allSatisfy(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode())
                            .isEqualTo("DUPLICATE_RESOURCE");
                });
        assertThat(countSubjects()).isEqualTo(1);
    }

    @Test
    void shouldRejectCaseInsensitiveDuplicateCodeOnUpdate() {
        SubjectResponse english = subjectService.createSubject(
                new CreateSubjectRequest("English", "ENG", "Languages")).block();
        SubjectResponse literature = subjectService.createSubject(
                new CreateSubjectRequest("Literature", "LIT", "Languages")).block();

        StepVerifier.create(subjectService.updateSubject(
                        literature.subjectId(),
                        new CreateSubjectRequest("Literature", " eng ", "Languages")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("code");
                })
                .verify();

        assertThat(english).isNotNull();
        assertThat(countSubjects()).isEqualTo(2);
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .schoolName("Subject Test School")
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }

    private void seedSchool() {
        databaseClient.sql("""
                        INSERT INTO school.schools (
                            id, name, code, email, phone, country,
                            payment_config, sms_config, term_config, is_active
                        )
                        VALUES (
                            :id, 'Subject Test School', 'STS', 'admin@sts.edu',
                            '+2348011111111', 'Nigeria',
                            '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, true
                        )
                        """)
                .bind("id", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private long countSubjects() {
        return ((Number) databaseClient.sql("SELECT COUNT(*) AS count FROM result.subjects")
                .fetch()
                .one()
                .block()
                .get("count")).longValue();
    }

    private void cleanDatabase() {
        databaseClient.sql("DELETE FROM result.class_subjects").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.subjects").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.schools").fetch().rowsUpdated().block();
    }
}
