package com.fee.app.schoolfeeapp.school.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.dto.request.ConfigureGradeLevelsRequest;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import java.time.Instant;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeLevelServiceImplTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TransactionalOperator transactionalOperator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GradeLevelServiceImpl gradeLevelService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    @BeforeEach
    void setUp() {
        gradeLevelService = new GradeLevelServiceImpl(
                schoolRepository,
                jwtUtils,
                transactionalOperator,
                objectMapper);
    }

    @Test
    @DisplayName("Should return all available grade levels in enum order")
    void shouldReturnAllAvailableGradeLevelsInEnumOrder() {
        StepVerifier.create(gradeLevelService.getAvailableGradeLevels())
                .assertNext(response -> {
                    assertThat(response).hasSize(15);
                    assertThat(response.get(0).code()).isEqualTo("NURSERY_1");
                    assertThat(response.get(0).name()).isEqualTo("Nursery 1");
                    assertThat(response.get(0).category()).isEqualTo("NURSERY");
                    assertThat(response.get(0).sortOrder()).isEqualTo(1);
                    assertThat(response.get(14).code()).isEqualTo("SSS_3");
                    assertThat(response.get(14).sortOrder()).isEqualTo(15);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return all school grade levels when school has no configuration")
    void shouldReturnAllSchoolGradeLevelsWhenSchoolHasNoConfiguration() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool(null)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(gradeLevelService.getSchoolGradeLevels())
                .assertNext(response -> {
                    assertThat(response).hasSize(15);
                    assertThat(response.get(0).code()).isEqualTo("NURSERY_1");
                    assertThat(response.get(14).code()).isEqualTo("SSS_3");
                })
                .verifyComplete();

        verify(schoolRepository).findActiveByIdForUpdate(SCHOOL_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should return configured school grade levels in canonical order")
    void shouldReturnConfiguredSchoolGradeLevelsInCanonicalOrder() {
        ObjectNode termConfig = objectMapper.createObjectNode();
        termConfig.putArray("gradeLevels")
                .add("primary_2")
                .add("BAD_CODE")
                .add(" PRIMARY_1 ");

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool(termConfig)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(gradeLevelService.getSchoolGradeLevels())
                .assertNext(response -> {
                    assertThat(response).hasSize(2);
                    assertThat(response.get(0).code()).isEqualTo("PRIMARY_1");
                    assertThat(response.get(1).code()).isEqualTo("PRIMARY_2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject school grade levels for inactive or missing school")
    void shouldRejectSchoolGradeLevelsForInactiveOrMissingSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(gradeLevelService.getSchoolGradeLevels())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should configure grade levels with canonical codes and preserve existing term config")
    void shouldConfigureGradeLevelsWithCanonicalCodesAndPreserveExistingTermConfig() {
        ObjectNode termConfig = objectMapper.createObjectNode();
        termConfig.put("termsPerYear", 3);
        School school = existingSchool(termConfig);
        ConfigureGradeLevelsRequest request = new ConfigureGradeLevelsRequest(
                List.of(" primary_2 ", "PRIMARY_1"),
                new ConfigureGradeLevelsRequest.NamingConvention(
                        " Nursery {level} ",
                        " Primary {level} ",
                        null,
                        "SSS {level}"));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(school));
        when(schoolRepository.save(any(School.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(gradeLevelService.configureGradeLevels(request))
                .assertNext(response -> {
                    assertThat(response.enabledLevels()).isEqualTo(2);
                    assertThat(response.enabledLevelCodes()).containsExactly("PRIMARY_1", "PRIMARY_2");
                    assertThat(response.message()).contains("Grade levels configured");
                })
                .verifyComplete();

        ArgumentCaptor<School> schoolCaptor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).save(schoolCaptor.capture());
        School savedSchool = schoolCaptor.getValue();
        JsonNode savedConfig = savedSchool.getTermConfig();
        assertThat(savedConfig.get("termsPerYear").asInt()).isEqualTo(3);
        assertThat(savedConfig.get("gradeLevels"))
                .extracting(JsonNode::asText)
                .containsExactly("PRIMARY_1", "PRIMARY_2");
        assertThat(savedConfig.get("namingConventions").get("nursery").asText()).isEqualTo("Nursery {level}");
        assertThat(savedConfig.get("namingConventions").get("primary").asText()).isEqualTo("Primary {level}");
        assertThat(savedConfig.get("namingConventions").get("seniorSecondary").asText()).isEqualTo("SSS {level}");
        assertThat(savedConfig.get("namingConventions").has("juniorSecondary")).isFalse();
        assertThat(savedSchool.getUpdatedAt()).isNotNull();
        verify(schoolRepository).findActiveByIdForUpdate(SCHOOL_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    @DisplayName("Should merge partial naming convention updates with existing conventions")
    void shouldMergePartialNamingConventionUpdatesWithExistingConventions() {
        ObjectNode termConfig = objectMapper.createObjectNode();
        ObjectNode namingConventions = objectMapper.createObjectNode();
        namingConventions.put("nursery", "Nursery {level}");
        namingConventions.put("primary", "Primary {level}");
        namingConventions.put("seniorSecondary", "SSS {level}");
        termConfig.set("namingConventions", namingConventions);
        School school = existingSchool(termConfig);
        ConfigureGradeLevelsRequest request = new ConfigureGradeLevelsRequest(
                List.of("PRIMARY_1"),
                new ConfigureGradeLevelsRequest.NamingConvention(
                        null,
                        " Lower Primary {level} ",
                        "JSS {level}",
                        null));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(school));
        when(schoolRepository.save(any(School.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(gradeLevelService.configureGradeLevels(request))
                .assertNext(response -> assertThat(response.enabledLevelCodes()).containsExactly("PRIMARY_1"))
                .verifyComplete();

        ArgumentCaptor<School> schoolCaptor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).save(schoolCaptor.capture());
        JsonNode savedConventions = schoolCaptor.getValue().getTermConfig().get("namingConventions");
        assertThat(savedConventions.get("nursery").asText()).isEqualTo("Nursery {level}");
        assertThat(savedConventions.get("primary").asText()).isEqualTo("Lower Primary {level}");
        assertThat(savedConventions.get("juniorSecondary").asText()).isEqualTo("JSS {level}");
        assertThat(savedConventions.get("seniorSecondary").asText()).isEqualTo("SSS {level}");
    }

    @Test
    @DisplayName("Should reject null configure request before auth lookup")
    void shouldRejectNullConfigureRequestBeforeAuthLookup() {
        StepVerifier.create(gradeLevelService.configureGradeLevels(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_GRADE_LEVELS");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject empty enabled levels before auth lookup")
    void shouldRejectEmptyEnabledLevelsBeforeAuthLookup() {
        StepVerifier.create(gradeLevelService.configureGradeLevels(
                        new ConfigureGradeLevelsRequest(List.of(), null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_GRADE_LEVELS");
                    assertThat(exception.getField()).isEqualTo("enabledLevels");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject invalid grade level codes before auth lookup")
    void shouldRejectInvalidGradeLevelCodesBeforeAuthLookup() {
        ConfigureGradeLevelsRequest request = new ConfigureGradeLevelsRequest(
                java.util.Arrays.asList("PRIMARY_1", null, " ", "BAD_CODE"),
                null);

        StepVerifier.create(gradeLevelService.configureGradeLevels(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_GRADE_LEVELS");
                    assertThat(exception.getField()).isEqualTo("enabledLevels");
                    assertThat(exception.getMessage()).contains("<null>", "BAD_CODE");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject duplicate grade level codes before auth lookup")
    void shouldRejectDuplicateGradeLevelCodesBeforeAuthLookup() {
        ConfigureGradeLevelsRequest request = new ConfigureGradeLevelsRequest(
                List.of("PRIMARY_1", " primary_1 "),
                null);

        StepVerifier.create(gradeLevelService.configureGradeLevels(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_GRADE_LEVELS");
                    assertThat(exception.getField()).isEqualTo("enabledLevels");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject blank naming convention")
    void shouldRejectBlankNamingConvention() {
        ConfigureGradeLevelsRequest request = new ConfigureGradeLevelsRequest(
                List.of("PRIMARY_1"),
                new ConfigureGradeLevelsRequest.NamingConvention(null, " ", null, null));

        StepVerifier.create(gradeLevelService.configureGradeLevels(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_NAMING_CONVENTION");
                    assertThat(exception.getField()).isEqualTo("namingConvention.primary");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject configure grade levels for inactive or missing school")
    void shouldRejectConfigureGradeLevelsForInactiveOrMissingSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(gradeLevelService.configureGradeLevels(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                })
                .verify();

        verify(schoolRepository, never()).save(any(School.class));
    }

    @Test
    @DisplayName("Should reject non-object term config")
    void shouldRejectNonObjectTermConfig() {
        School school = existingSchool(objectMapper.createArrayNode());

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(school));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(gradeLevelService.configureGradeLevels(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_TERM_CONFIG");
                    assertThat(exception.getField()).isEqualTo("termConfig");
                })
                .verify();

        verify(schoolRepository, never()).save(any(School.class));
    }

    @Test
    @DisplayName("Should map stale grade level configuration save to conflict error")
    void shouldMapStaleGradeLevelConfigurationSaveToConflictError() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(existingSchool(null)));
        when(schoolRepository.save(any(School.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale school row")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(gradeLevelService.configureGradeLevels(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("STALE_RESOURCE");
                    assertThat(exception.getField()).isEqualTo("version");
                    assertThat(exception.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                })
                .verify();
    }

    private ConfigureGradeLevelsRequest validRequest() {
        return new ConfigureGradeLevelsRequest(
                List.of("PRIMARY_1", "PRIMARY_2"),
                null);
    }

    private School existingSchool(JsonNode termConfig) {
        return School.builder()
                .id(SCHOOL_ID)
                .name("Grace International School")
                .code("GIS")
                .termConfig(termConfig)
                .isActive(true)
                .updatedAt(Instant.parse("2026-01-10T10:15:30Z"))
                .version(0)
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
