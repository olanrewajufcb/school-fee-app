package com.fee.app.schoolfeeapp.school.service;

import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.school.dto.request.*;
import com.fee.app.schoolfeeapp.school.dto.response.*;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * School management operations.
 * 
 * ISP: This interface exposes only school-related operations.
 * Session/term management is in AcademicSessionService.
 * Class management is in ClassService.
 */
public interface SchoolService {

    /**
     * Create a new school (Super Admin only).
     * Orchestrates: school record + academic session + terms + admin user + Keycloak setup.
     */
    Mono<CreateSchoolResponse> createSchool(CreateSchoolRequest request);

    /**
     * Get the current user's school profile.
     * Extracts school_id from JWT token.
     */
    Mono<SchoolResponse> getCurrentSchool();

    /**
     * Get a specific school by ID (Super Admin only).
     */
    Mono<SchoolResponse> getSchoolById(UUID schoolId);

    /**
     * Update the current user's school.
     */
    Mono<SchoolResponse> updateSchool(UpdateSchoolRequest request);

    /**
     * Deactivate a school (Super Admin only).
     */
    Mono<Void> deactivateSchool(UUID schoolId);


    // School listing (Super Admin)
    Mono<PageResponse<SchoolSummaryResponse>> listSchools(String status, Pageable pageable);

    // Academic sessions
    Mono<List<AcademicSessionResponse>> getCurrentSchoolSessions();

    Mono<AcademicSessionResponse> createSession(CreateAcademicSessionRequest request);

    Mono<AcademicSessionResponse> setCurrentSession(UUID sessionId);

    Mono<UpdateSessionResponse> updateSession(UUID sessionId, UpdateSessionRequest request);

    Mono<CloseSessionResponse> closeSession(UUID sessionId, CloseSessionRequest request);
    // Term management
    Mono<SetCurrentTermResponse> setCurrentTerm(UUID termId);

}