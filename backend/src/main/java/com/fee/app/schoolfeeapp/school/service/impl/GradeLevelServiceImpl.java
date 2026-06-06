package com.fee.app.schoolfeeapp.school.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.dto.request.ConfigureGradeLevelsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.ConfigureGradeLevelsResponse;
import com.fee.app.schoolfeeapp.school.dto.response.GradeLevelResponse;
import com.fee.app.schoolfeeapp.school.enums.GradeLevel;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.school.service.GradeLevelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;


import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
class GradeLevelServiceImpl implements GradeLevelService {

    private final SchoolRepository schoolRepository;
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;
    private final ObjectMapper objectMapper;

    private static final String GRADE_LEVELS_CONFIG_KEY = "gradeLevels";
    private static final String NAMING_CONVENTIONS_KEY = "namingConventions";

    /**
     * Get all grade levels available in the system.
     * This is a static list — not per-school.
     */
    @Override
    public Mono<List<GradeLevelResponse>> getAvailableGradeLevels() {
        return Mono.fromCallable(this::allGradeLevelResponses);
    }

    /**
     * Get the current school's enabled grade levels.
     * Reads from the school's term_config JSON.
     */
    @Override
    public Mono<List<GradeLevelResponse>> getSchoolGradeLevels() {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> transactionalOperator.transactional(
                        schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                .switchIfEmpty(Mono.error(new SchoolFeeException(
                                        "SCHOOL_NOT_FOUND", "School not found")))
                                .map(school -> {
                                    List<String> enabledCodes = getEnabledLevelsFromConfig(school);

                                    if (enabledCodes.isEmpty()) {
                                        return allGradeLevelResponses();
                                    }

                                    return GradeLevel.getAllOrdered().stream()
                                            .filter(gl -> enabledCodes.contains(gl.name()))
                                            .map(this::toGradeLevelResponse)
                                            .toList();
                                })));
    }

    /**
     * Configure which grade levels a school uses.
     * Updates the school's term_config JSON with the enabled levels
     * and naming conventions.
     */
    @Override
    public Mono<ConfigureGradeLevelsResponse> configureGradeLevels(ConfigureGradeLevelsRequest request) {
        return Mono.defer(() -> {
            List<String> enabledLevels = validateAndNormalizeEnabledLevels(request);

            return jwtUtils.getCurrentUser()
                    .flatMap(user -> transactionalOperator.transactional(
                            schoolRepository.findActiveByIdForUpdate(user.getSchoolId())
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "SCHOOL_NOT_FOUND", "School not found")))
                                    .flatMap(school -> {
                                        ObjectNode mutableConfig = mutableTermConfig(school);
                                        mutableConfig.set(
                                                GRADE_LEVELS_CONFIG_KEY,
                                                enabledLevelsArray(enabledLevels));
                                        applyNamingConventions(
                                                mutableConfig,
                                                request.namingConvention());

                                        school.setTermConfig(mutableConfig);
                                        school.setUpdatedAt(Instant.now());
                                        return schoolRepository.save(school)
                                                .onErrorMap(
                                                        OptimisticLockingFailureException.class,
                                                        this::staleGradeLevelConfigException);
                                    })
                                    .map(savedSchool -> new ConfigureGradeLevelsResponse(
                                            enabledLevels.size(),
                                            enabledLevels,
                                            "Grade levels configured. You can now create classes for these levels."
                                    ))));
        });
    }

    private List<GradeLevelResponse> allGradeLevelResponses() {
        return GradeLevel.getAllOrdered().stream()
                .map(this::toGradeLevelResponse)
                .toList();
    }

    private GradeLevelResponse toGradeLevelResponse(GradeLevel gradeLevel) {
        return new GradeLevelResponse(
                gradeLevel.name(),
                gradeLevel.getDisplayName(),
                gradeLevel.getCategory(),
                gradeLevel.getSortOrder());
    }

    private List<String> validateAndNormalizeEnabledLevels(ConfigureGradeLevelsRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_GRADE_LEVELS",
                    "Grade level configuration request is required");
        }
        if (request.enabledLevels() == null || request.enabledLevels().isEmpty()) {
            throw new SchoolFeeException(
                    "INVALID_GRADE_LEVELS",
                    "At least one grade level must be enabled",
                    "enabledLevels");
        }
        validateNamingConvention(request.namingConvention());

        List<String> invalidCodes = new ArrayList<>();
        Set<String> normalizedCodes = new LinkedHashSet<>();

        for (String code : request.enabledLevels()) {
            String normalized = normalizeGradeLevelCode(code);
            if (normalized == null || !GradeLevel.isValid(normalized)) {
                invalidCodes.add(code == null ? "<null>" : code);
            } else {
                normalizedCodes.add(normalized);
            }
        }

        if (!invalidCodes.isEmpty()) {
            throw new SchoolFeeException(
                    "INVALID_GRADE_LEVELS",
                    "Invalid grade level codes: " + String.join(", ", invalidCodes),
                    "enabledLevels");
        }
        if (normalizedCodes.size() != request.enabledLevels().size()) {
            throw new SchoolFeeException(
                    "DUPLICATE_GRADE_LEVELS",
                    "Grade level codes must be unique",
                    "enabledLevels");
        }

        return GradeLevel.getAllOrdered().stream()
                .map(GradeLevel::name)
                .filter(normalizedCodes::contains)
                .toList();
    }

    private void validateNamingConvention(ConfigureGradeLevelsRequest.NamingConvention namingConvention) {
        if (namingConvention == null) {
            return;
        }
        validateNamingConventionValue("nursery", namingConvention.nursery());
        validateNamingConventionValue("primary", namingConvention.primary());
        validateNamingConventionValue("juniorSecondary", namingConvention.juniorSecondary());
        validateNamingConventionValue("seniorSecondary", namingConvention.seniorSecondary());
    }

    private void validateNamingConventionValue(String field, String value) {
        if (value != null && value.isBlank()) {
            throw new SchoolFeeException(
                    "INVALID_NAMING_CONVENTION",
                    "Naming convention cannot be blank",
                    "namingConvention." + field);
        }
    }

    private String normalizeGradeLevelCode(String code) {
        return code == null || code.isBlank()
                ? null
                : code.trim().toUpperCase(Locale.ROOT);
    }

    private ObjectNode mutableTermConfig(School school) {
        JsonNode termConfig = school.getTermConfig();
        if (termConfig == null || termConfig.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!termConfig.isObject()) {
            throw new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "School term configuration must be a JSON object",
                    "termConfig");
        }
        return termConfig.deepCopy();
    }

    private ArrayNode enabledLevelsArray(List<String> enabledLevels) {
        ArrayNode levelsArray = objectMapper.createArrayNode();
        enabledLevels.forEach(levelsArray::add);
        return levelsArray;
    }

    private void applyNamingConventions(
            ObjectNode mutableConfig,
            ConfigureGradeLevelsRequest.NamingConvention namingConvention) {
        if (namingConvention == null) {
            return;
        }

        ObjectNode conventions = mutableNamingConventions(mutableConfig);
        putConventionIfPresent(conventions, "nursery", namingConvention.nursery());
        putConventionIfPresent(conventions, "primary", namingConvention.primary());
        putConventionIfPresent(conventions, "juniorSecondary", namingConvention.juniorSecondary());
        putConventionIfPresent(conventions, "seniorSecondary", namingConvention.seniorSecondary());
        if (conventions.size() > 0) {
            mutableConfig.set(NAMING_CONVENTIONS_KEY, conventions);
        }
    }

    private ObjectNode mutableNamingConventions(ObjectNode mutableConfig) {
        JsonNode namingConventions = mutableConfig.get(NAMING_CONVENTIONS_KEY);
        if (namingConventions != null && namingConventions.isObject()) {
            return namingConventions.deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private void putConventionIfPresent(ObjectNode conventions, String key, String value) {
        if (value == null) {
            return;
        }
        conventions.put(key, value.trim());
    }

    private SchoolFeeException staleGradeLevelConfigException(Throwable cause) {
        return new SchoolFeeException(
                "STALE_RESOURCE",
                "Grade level configuration was modified by another request. Please reload and try again.",
                "version",
                cause);
    }

    /**
     * Extract enabled grade level codes from the school's term_config.
     */
    private List<String> getEnabledLevelsFromConfig(School school) {
        JsonNode termConfig = school.getTermConfig();
        if (termConfig == null || termConfig.isNull()) {
            return List.of();
        }

        JsonNode gradeLevelsNode = termConfig.get(GRADE_LEVELS_CONFIG_KEY);
        if (gradeLevelsNode == null || !gradeLevelsNode.isArray()) {
            return List.of();
        }

        Set<String> codes = new LinkedHashSet<>();
        gradeLevelsNode.forEach(node -> {
            String normalized = normalizeGradeLevelCode(node.asText());
            if (normalized != null && GradeLevel.isValid(normalized)) {
                codes.add(normalized);
            }
        });
        return GradeLevel.getAllOrdered().stream()
                .map(GradeLevel::name)
                .filter(codes::contains)
                .toList();
    }
}
