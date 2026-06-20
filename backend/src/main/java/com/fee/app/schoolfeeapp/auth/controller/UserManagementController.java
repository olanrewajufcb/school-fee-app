package com.fee.app.schoolfeeapp.auth.controller;

import com.fee.app.schoolfeeapp.auth.dto.request.*;
import com.fee.app.schoolfeeapp.auth.dto.response.*;
import com.fee.app.schoolfeeapp.auth.service.UserManagementService;
import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class UserManagementController {

    private final UserManagementService userManagementService;

    private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of(
            "userId", "email", "firstName", "lastName", "userType", "lastLogin", "createdAt"
    );
    // ========================================================================
    // PARENT MANAGEMENT (Admin Only)
    // ========================================================================

    /**
     * POST /api/v1/auth/parents
     * Create a parent account.
     * Creates:
     * - Keycloak user with PARENT role
     * - Guardian record in school.student_guardians
     * - Guardian-student links for each child
     * - Sends SMS invitation to set password
     */
    @PostMapping("/parents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CreateParentResponse>>> createParent(
            @Valid @RequestBody CreateParentRequest request) {
        return userManagementService.createParent(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response)));
    }

    // ========================================================================
    // STAFF MANAGEMENT (Admin Only)
    // ========================================================================

    /**
     * POST /api/v1/auth/staff
     * Create a staff account (admin, accountant, or teacher).
     * Creates:
     * - Keycloak user with specified roles
     * - User record in auth.users
     * - User-school-role assignments
     * - Sends email/SMS with temporary password
     */
    @PostMapping("/staff")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CreateStaffResponse>>> createStaff(
            @Valid @RequestBody CreateStaffRequest request) {
        return userManagementService.createStaff(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response)));
    }

  // ========================================================================
  // USER LISTING (Admin/Accountant)
  // ========================================================================

  /**
   * GET /api/v1/auth/users
   * List users in the current school with optional filters.
   * Query params:
   * - userType: PARENT, TEACHER, SCHOOL_ADMIN, ACCOUNTANT
   * - status: ACTIVE, INACTIVE
   * - search: name, email, or phone
   * - page, size: pagination
   *
   */
  @GetMapping("/users")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
  public Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> listUsers(
          @RequestParam(required = false) String userType,
          @RequestParam(required = false, defaultValue = "ACTIVE") String status,
          @RequestParam(required = false) String search,
          @RequestParam(defaultValue = "0")
          @Min(value = 0, message = "Page number must be greater than or equal to 0")
          int page,
          @RequestParam(defaultValue = "10")
          @Min(value = 1, message = "Page size must be greater than or equal to 1")
          int size,
          @RequestParam(defaultValue = "userId")
          String sortBy
  ) {
      String requestId = UUID.randomUUID().toString();

      if (!ALLOWED_SORT_COLUMNS.contains(sortBy)) {
          return Mono.error(new SchoolFeeException("INVALID_SORT", "Invalid sort column: " + sortBy));
      }
      Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));

      return userManagementService
              .listUsers(userType, status, search, pageable, requestId)
              .map(response ->
                      ResponseEntity.ok(ApiResponse.success(response)));
  }

    // ========================================================================
    // PARENT SELF-ONBOARDING (Public)
    // ========================================================================

    @PostMapping("/check-account")
    public Mono<ResponseEntity<ApiResponse<CheckAccountResponse>>> checkAccount(
            @Valid @RequestBody CheckAccountRequest request) {
        return userManagementService.checkAccount(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @PostMapping("/send-otp")
    public Mono<ResponseEntity<ApiResponse<Map<String, String>>>> sendOtp(
            @Valid @RequestBody SendOtpRequest request) {
        return userManagementService.sendOtp(request)
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.success(Map.of("message", "Verification code sent to " + request.phoneNumber()))));
    }

    @PostMapping("/verify-otp")
    public Mono<ResponseEntity<ApiResponse<Map<String, String>>>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        return userManagementService.verifyOtpAndCreateAccount(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @PostMapping("/set-password")
    public Mono<ResponseEntity<ApiResponse<Map<String, String>>>> setPassword(
            @Valid @RequestBody SetPasswordRequest request) {
        return userManagementService.setPassword(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }





}
