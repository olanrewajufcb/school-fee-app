package com.fee.app.schoolfeeapp.auth.controller;


import com.fee.app.schoolfeeapp.auth.dto.response.*;
import com.fee.app.schoolfeeapp.auth.service.AuthService;
import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * GET /api/v1/auth/me
     * Get current authenticated user's profile.
     * Authentication is handled by Keycloak (frontend calls Keycloak directly).
     * This endpoint returns the enriched user profile with tenant context.
     * Requires: Valid JWT token (any authenticated user)
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> getCurrentUser() {
        return authService.getCurrentUserProfile()
                .map(profile -> ResponseEntity.ok(ApiResponse.success(profile)));
    }

    /**
     * NOTE: There is NO /login endpoint here.
     * Authentication flow:
     * 1. Frontend → Keycloak directly → gets JWT tokens
     * 2. Frontend stores tokens, attaches to API requests
     * 3. Backend validates JWT signature + claims on every request
     * The frontend Keycloak config is available at:
     * GET /api/v1/auth/keycloak-config
     */
    @GetMapping("/keycloak-config")
    public Mono<ResponseEntity<ApiResponse<KeycloakConfigResponse>>> getKeycloakConfig() {
        return Mono.just(ResponseEntity.ok(
                ApiResponse.success(KeycloakConfigResponse.create())));
    }
}