package com.fee.app.schoolfeeapp.school.service;

import com.fee.app.schoolfeeapp.school.dto.request.ConfigureGradeLevelsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.ConfigureGradeLevelsResponse;
import com.fee.app.schoolfeeapp.school.dto.response.GradeLevelResponse;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GradeLevelService {

    /**
     * Get all available grade levels in the system.
     * Returns the full list — schools filter which ones they've enabled.
     */
    Mono<List<GradeLevelResponse>> getAvailableGradeLevels();

    /**
     * Get the current school's enabled grade levels.
     */
    Mono<List<GradeLevelResponse>> getSchoolGradeLevels();

    /**
     * Configure which grade levels a school uses.
     */
    Mono<ConfigureGradeLevelsResponse> configureGradeLevels(ConfigureGradeLevelsRequest request);
}