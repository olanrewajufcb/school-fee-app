package com.fee.app.schoolfeeapp.school.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.school.dto.request.ConfigureGradeLevelsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.ConfigureGradeLevelsResponse;
import com.fee.app.schoolfeeapp.school.dto.response.GradeLevelResponse;
import com.fee.app.schoolfeeapp.school.service.GradeLevelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schools/current/grade-levels")
@RequiredArgsConstructor
public class GradeLevelController {

    private final GradeLevelService gradeLevelService;

    /**
     * GET /api/v1/schools/current/grade-levels
     * Get the current school's enabled grade levels.
     * If not configured, returns all available grade levels.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<GradeLevelResponse>>>> getGradeLevels() {
        return gradeLevelService.getSchoolGradeLevels()
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * GET /api/v1/schools/current/grade-levels/available
     * Get all grade levels available in the system (not per-school).
     */
    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<GradeLevelResponse>>>> getAvailableGradeLevels() {
        return gradeLevelService.getAvailableGradeLevels()
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * PUT /api/v1/schools/current/grade-levels
     * Configure which grade levels the school uses.
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<ConfigureGradeLevelsResponse>>> configureGradeLevels(
            @Valid @RequestBody ConfigureGradeLevelsRequest request) {
        return gradeLevelService.configureGradeLevels(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }
}