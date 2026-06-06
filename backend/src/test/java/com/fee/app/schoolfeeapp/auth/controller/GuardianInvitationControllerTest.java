package com.fee.app.schoolfeeapp.auth.controller;

import com.fee.app.schoolfeeapp.auth.dto.request.BulkInvitationRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.BulkInvitationResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.GuardianInvitationResponse;
import com.fee.app.schoolfeeapp.auth.service.GuardianInvitationService;
import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuardianInvitationControllerTest {

    @Mock
    private GuardianInvitationService guardianInvitationService;

    @InjectMocks
    private GuardianInvitationController guardianInvitationController;

    private static final UUID GUARDIAN_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID GUARDIAN_ID_2 = UUID.fromString("d4e5f6a7-b8c9-0123-def0-234567890123");
    private static final UUID GUARDIAN_ID_3 = UUID.fromString("e5f6a7b8-c9d0-1234-ef01-345678901234");

    // ========================================================================
    // INVITE GUARDIAN TESTS
    // ========================================================================

    @Nested
    @DisplayName("Invite Guardian")
    class InviteGuardianTests {

        @Test
        @DisplayName("Should invite guardian successfully")
        void shouldInviteGuardianSuccessfully() {
            // Arrange
            GuardianInvitationResponse expectedResponse = new GuardianInvitationResponse(
                    GUARDIAN_ID,
                    "John Doe",
                    "+2348012345678",
                    true,
                    "invitation-token-123",
                    "Invitation sent successfully"
            );

            when(guardianInvitationService.inviteGuardian(eq(GUARDIAN_ID)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act
            Mono<ResponseEntity<ApiResponse<GuardianInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        
                        GuardianInvitationResponse data = responseEntity.getBody().getData();
                        assertThat(data).isNotNull();
                        assertThat(data.guardianId()).isEqualTo(GUARDIAN_ID);
                        assertThat(data.guardianName()).isEqualTo("John Doe");
                        assertThat(data.phoneNumber()).isEqualTo("+2348012345678");
                        assertThat(data.invitationSent()).isTrue();
                        assertThat(data.invitationToken()).isEqualTo("invitation-token-123");
                        assertThat(data.message()).isEqualTo("Invitation sent successfully");
                    })
                    .verifyComplete();

            verify(guardianInvitationService, times(1)).inviteGuardian(GUARDIAN_ID);
        }

        @Test
        @DisplayName("Should handle guardian not found error")
        void shouldHandleGuardianNotFoundError() {
            // Arrange
            SchoolFeeException expectedError = new SchoolFeeException(
                    "GUARDIAN_NOT_FOUND",
                    "Guardian not found with ID: " + GUARDIAN_ID
            );

            when(guardianInvitationService.inviteGuardian(eq(GUARDIAN_ID)))
                    .thenReturn(Mono.error(expectedError));

            // Act
            Mono<ResponseEntity<ApiResponse<GuardianInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(guardianInvitationService, times(1)).inviteGuardian(GUARDIAN_ID);
        }

        @Test
        @DisplayName("Should handle guardian without phone number error")
        void shouldHandleGuardianWithoutPhoneNumberError() {
            // Arrange
            SchoolFeeException expectedError = new SchoolFeeException(
                    "INVALID_GUARDIAN_DATA",
                    "Guardian does not have a valid phone number"
            );

            when(guardianInvitationService.inviteGuardian(eq(GUARDIAN_ID)))
                    .thenReturn(Mono.error(expectedError));

            // Act
            Mono<ResponseEntity<ApiResponse<GuardianInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(guardianInvitationService, times(1)).inviteGuardian(GUARDIAN_ID);
        }

        @Test
        @DisplayName("Should handle SMS service failure")
        void shouldHandleSmsServiceFailure() {
            // Arrange
            SchoolFeeException expectedError = new SchoolFeeException(
                    "SMS_SEND_FAILED",
                    "Failed to send invitation SMS"
            );

            when(guardianInvitationService.inviteGuardian(eq(GUARDIAN_ID)))
                    .thenReturn(Mono.error(expectedError));

            // Act
            Mono<ResponseEntity<ApiResponse<GuardianInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(guardianInvitationService, times(1)).inviteGuardian(GUARDIAN_ID);
        }

        @Test
        @DisplayName("Should handle already invited guardian")
        void shouldHandleAlreadyInvitedGuardian() {
            // Arrange
            GuardianInvitationResponse expectedResponse = new GuardianInvitationResponse(
                    GUARDIAN_ID,
                    "John Doe",
                    "+2348012345678",
                    false,
                    null,
                    "Guardian has already been invited"
            );

            when(guardianInvitationService.inviteGuardian(eq(GUARDIAN_ID)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act
            Mono<ResponseEntity<ApiResponse<GuardianInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        
                        GuardianInvitationResponse data = responseEntity.getBody().getData();
                        assertThat(data.invitationSent()).isFalse();
                        assertThat(data.message()).isEqualTo("Guardian has already been invited");
                    })
                    .verifyComplete();

            verify(guardianInvitationService, times(1)).inviteGuardian(GUARDIAN_ID);
        }

        @Test
        @DisplayName("Should handle database timeout error")
        void shouldHandleDatabaseTimeoutError() {
            // Arrange
            SchoolFeeException expectedError = new SchoolFeeException(
                    "DATABASE_ERROR",
                    "Operation timed out while processing invitation"
            );

            when(guardianInvitationService.inviteGuardian(eq(GUARDIAN_ID)))
                    .thenReturn(Mono.error(expectedError));

            // Act
            Mono<ResponseEntity<ApiResponse<GuardianInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(guardianInvitationService, times(1)).inviteGuardian(GUARDIAN_ID);
        }
    }

    // ========================================================================
    // BULK INVITE GUARDIANS TESTS
    // ========================================================================

    @Nested
    @DisplayName("Bulk Invite Guardians")
    class BulkInviteGuardiansTests {

        private BulkInvitationRequest validRequest;

        @BeforeEach
        void setup() {
            validRequest = new BulkInvitationRequest(
                    List.of(GUARDIAN_ID, GUARDIAN_ID_2, GUARDIAN_ID_3)
            );
        }

        @Test
        @DisplayName("Should invite multiple guardians successfully")
        void shouldInviteMultipleGuardiansSuccessfully() {
            // Arrange
            List<BulkInvitationResponse.InvitationResult> results = List.of(
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID, "+2348012345678", true, "Invitation sent"
                    ),
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID_2, "+2348098765432", true, "Invitation sent"
                    ),
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID_3, "+2348011111111", true, "Invitation sent"
                    )
            );

            BulkInvitationResponse expectedResponse = new BulkInvitationResponse(
                    3,  // totalRequested
                    3,  // invitationsSent
                    0,  // invitationsFailed
                    results
            );

            when(guardianInvitationService.inviteGuardiansBulk(any(BulkInvitationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act
            Mono<ResponseEntity<ApiResponse<BulkInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardiansBulk(validRequest);

            // Assert
            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();

                        BulkInvitationResponse data = responseEntity.getBody().getData();
                        assertThat(data).isNotNull();
                        assertThat(data.totalRequested()).isEqualTo(3);
                        assertThat(data.invitationsSent()).isEqualTo(3);
                        assertThat(data.invitationsFailed()).isEqualTo(0);
                        assertThat(data.results()).hasSize(3);

                        // Verify first result
                        BulkInvitationResponse.InvitationResult firstResult = data.results().get(0);
                        assertThat(firstResult.guardianId()).isEqualTo(GUARDIAN_ID);
                        assertThat(firstResult.success()).isTrue();
                        assertThat(firstResult.message()).isEqualTo("Invitation sent");
                    })
                    .verifyComplete();

            verify(guardianInvitationService, times(1)).inviteGuardiansBulk(validRequest);
        }

        @Test
        @DisplayName("Should handle partial success in bulk invitation")
        void shouldHandlePartialSuccessInBulkInvitation() {
            // Arrange
            List<BulkInvitationResponse.InvitationResult> results = List.of(
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID, "+2348012345678", true, "Invitation sent"
                    ),
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID_2, "+2348098765432", false, "Guardian not found"
                    ),
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID_3, "+2348011111111", true, "Invitation sent"
                    )
            );

            BulkInvitationResponse expectedResponse = new BulkInvitationResponse(
                    3,  // totalRequested
                    2,  // invitationsSent
                    1,  // invitationsFailed
                    results
            );

            when(guardianInvitationService.inviteGuardiansBulk(any(BulkInvitationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act
            Mono<ResponseEntity<ApiResponse<BulkInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardiansBulk(validRequest);

            // Assert
            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        
                        BulkInvitationResponse data = responseEntity.getBody().getData();
                        assertThat(data.totalRequested()).isEqualTo(3);
                        assertThat(data.invitationsSent()).isEqualTo(2);
                        assertThat(data.invitationsFailed()).isEqualTo(1);

                        // Verify failed result
                        BulkInvitationResponse.InvitationResult failedResult = data.results().get(1);
                        assertThat(failedResult.guardianId()).isEqualTo(GUARDIAN_ID_2);
                        assertThat(failedResult.success()).isFalse();
                        assertThat(failedResult.message()).isEqualTo("Guardian not found");
                    })
                    .verifyComplete();

            verify(guardianInvitationService, times(1)).inviteGuardiansBulk(validRequest);
        }

        @Test
        @DisplayName("Should handle all failures in bulk invitation")
        void shouldHandleAllFailuresInBulkInvitation() {
            // Arrange
            List<BulkInvitationResponse.InvitationResult> results = List.of(
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID, "+2348012345678", false, "Invalid phone number"
                    ),
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID_2, "+2348098765432", false, "SMS service unavailable"
                    )
            );

            BulkInvitationResponse expectedResponse = new BulkInvitationResponse(
                    2,  // totalRequested
                    0,  // invitationsSent
                    2,  // invitationsFailed
                    results
            );

            when(guardianInvitationService.inviteGuardiansBulk(any(BulkInvitationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act
            Mono<ResponseEntity<ApiResponse<BulkInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardiansBulk(validRequest);

            // Assert
            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        
                        BulkInvitationResponse data = responseEntity.getBody().getData();
                        assertThat(data.totalRequested()).isEqualTo(2);
                        assertThat(data.invitationsSent()).isEqualTo(0);
                        assertThat(data.invitationsFailed()).isEqualTo(2);
                        assertThat(data.results()).allMatch(r -> !r.success());
                    })
                    .verifyComplete();

            verify(guardianInvitationService, times(1)).inviteGuardiansBulk(validRequest);
        }

        @Test
        @DisplayName("Should handle empty guardian list validation error")
        void shouldHandleEmptyGuardianListValidationError() {
            // Arrange
            BulkInvitationRequest invalidRequest = new BulkInvitationRequest(List.of());

            SchoolFeeException expectedError = new SchoolFeeException(
                    "VALIDATION_ERROR",
                    "At least one guardian ID is required"
            );

            when(guardianInvitationService.inviteGuardiansBulk(any(BulkInvitationRequest.class)))
                    .thenReturn(Mono.error(expectedError));

            // Act
            Mono<ResponseEntity<ApiResponse<BulkInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardiansBulk(invalidRequest);

            // Assert
            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(guardianInvitationService, times(1)).inviteGuardiansBulk(invalidRequest);
        }

        @Test
        @DisplayName("Should handle single guardian in bulk request")
        void shouldHandleSingleGuardianInBulkRequest() {
            // Arrange
            BulkInvitationRequest singleRequest = new BulkInvitationRequest(List.of(GUARDIAN_ID));

            List<BulkInvitationResponse.InvitationResult> results = List.of(
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID, "+2348012345678", true, "Invitation sent"
                    )
            );

            BulkInvitationResponse expectedResponse = new BulkInvitationResponse(
                    1,  // totalRequested
                    1,  // invitationsSent
                    0,  // invitationsFailed
                    results
            );

            when(guardianInvitationService.inviteGuardiansBulk(any(BulkInvitationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act
            Mono<ResponseEntity<ApiResponse<BulkInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardiansBulk(singleRequest);

            // Assert
            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        
                        BulkInvitationResponse data = responseEntity.getBody().getData();
                        assertThat(data.totalRequested()).isEqualTo(1);
                        assertThat(data.invitationsSent()).isEqualTo(1);
                        assertThat(data.invitationsFailed()).isEqualTo(0);
                        assertThat(data.results()).hasSize(1);
                    })
                    .verifyComplete();

            verify(guardianInvitationService, times(1)).inviteGuardiansBulk(singleRequest);
        }

        @Test
        @DisplayName("Should handle large bulk request")
        void shouldHandleLargeBulkRequest() {
            // Arrange
            List<UUID> guardianIds = List.of(
                    GUARDIAN_ID, GUARDIAN_ID_2, GUARDIAN_ID_3,
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID()
            );

            BulkInvitationRequest largeRequest = new BulkInvitationRequest(guardianIds);

            // Create results for all 10 guardians
            List<BulkInvitationResponse.InvitationResult> results = guardianIds.stream()
                    .map(id -> new BulkInvitationResponse.InvitationResult(
                            id, "+2348000000000", true, "Invitation sent"
                    ))
                    .toList();

            BulkInvitationResponse expectedResponse = new BulkInvitationResponse(
                    10,  // totalRequested
                    10,  // invitationsSent
                    0,   // invitationsFailed
                    results
            );

            when(guardianInvitationService.inviteGuardiansBulk(any(BulkInvitationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act
            Mono<ResponseEntity<ApiResponse<BulkInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardiansBulk(largeRequest);

            // Assert
            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        
                        BulkInvitationResponse data = responseEntity.getBody().getData();
                        assertThat(data.totalRequested()).isEqualTo(10);
                        assertThat(data.invitationsSent()).isEqualTo(10);
                        assertThat(data.invitationsFailed()).isEqualTo(0);
                        assertThat(data.results()).hasSize(10);
                    })
                    .verifyComplete();

            verify(guardianInvitationService, times(1)).inviteGuardiansBulk(largeRequest);
        }

        @Test
        @DisplayName("Should handle service error in bulk invitation")
        void shouldHandleServiceErrorInBulkInvitation() {
            // Arrange
            SchoolFeeException expectedError = new SchoolFeeException(
                    "BULK_INVITATION_FAILED",
                    "Failed to process bulk invitation request"
            );

            when(guardianInvitationService.inviteGuardiansBulk(any(BulkInvitationRequest.class)))
                    .thenReturn(Mono.error(expectedError));

            // Act
            Mono<ResponseEntity<ApiResponse<BulkInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardiansBulk(validRequest);

            // Assert
            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(guardianInvitationService, times(1)).inviteGuardiansBulk(validRequest);
        }

        @Test
        @DisplayName("Should handle duplicate guardian IDs in bulk request")
        void shouldHandleDuplicateGuardianIdsInBulkRequest() {
            // Arrange
            BulkInvitationRequest duplicateRequest = new BulkInvitationRequest(
                    List.of(GUARDIAN_ID, GUARDIAN_ID, GUARDIAN_ID_2)
            );

            List<BulkInvitationResponse.InvitationResult> results = List.of(
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID, "+2348012345678", true, "Invitation sent"
                    ),
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID, "+2348012345678", false, "Duplicate guardian ID"
                    ),
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID_2, "+2348098765432", true, "Invitation sent"
                    )
            );

            BulkInvitationResponse expectedResponse = new BulkInvitationResponse(
                    3,  // totalRequested
                    2,  // invitationsSent
                    1,  // invitationsFailed
                    results
            );

            when(guardianInvitationService.inviteGuardiansBulk(any(BulkInvitationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act
            Mono<ResponseEntity<ApiResponse<BulkInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardiansBulk(duplicateRequest);

            // Assert
            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        
                        BulkInvitationResponse data = responseEntity.getBody().getData();
                        assertThat(data.totalRequested()).isEqualTo(3);
                        assertThat(data.invitationsSent()).isEqualTo(2);
                        assertThat(data.invitationsFailed()).isEqualTo(1);
                    })
                    .verifyComplete();

            verify(guardianInvitationService, times(1)).inviteGuardiansBulk(duplicateRequest);
        }
    }

    // ========================================================================
    // EDGE CASES AND ERROR HANDLING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandlingTests {

        @Test
        @DisplayName("Should handle null guardian ID gracefully")
        void shouldHandleNullGuardianIdGracefully() {
            // This test verifies that the controller properly handles null path variable
            // In practice, Spring would reject this before reaching the controller
            // but we test the service interaction
            
            SchoolFeeException expectedError = new SchoolFeeException(
                    "INVALID_REQUEST",
                    "Guardian ID cannot be null"
            );

            when(guardianInvitationService.inviteGuardian(any()))
                    .thenReturn(Mono.error(expectedError));

            // Act
            Mono<ResponseEntity<ApiResponse<GuardianInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardian(null);

            // Assert
            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should handle concurrent invitation requests")
        void shouldHandleConcurrentInvitationRequests() {
            // Arrange - simulate race condition where guardian is invited twice
            GuardianInvitationResponse firstResponse = new GuardianInvitationResponse(
                    GUARDIAN_ID,
                    "John Doe",
                    "+2348012345678",
                    true,
                    "token-1",
                    "Invitation sent successfully"
            );

            GuardianInvitationResponse secondResponse = new GuardianInvitationResponse(
                    GUARDIAN_ID,
                    "John Doe",
                    "+2348012345678",
                    false,
                    null,
                    "Guardian has already been invited"
            );

            when(guardianInvitationService.inviteGuardian(eq(GUARDIAN_ID)))
                    .thenReturn(Mono.just(firstResponse))
                    .thenReturn(Mono.just(secondResponse));

            // Act & Assert - First request
            StepVerifier.create(guardianInvitationController.inviteGuardian(GUARDIAN_ID))
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getBody().getData().invitationSent()).isTrue();
                    })
                    .verifyComplete();

            // Act & Assert - Second request (concurrent)
            StepVerifier.create(guardianInvitationController.inviteGuardian(GUARDIAN_ID))
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getBody().getData().invitationSent()).isFalse();
                    })
                    .verifyComplete();

            verify(guardianInvitationService, times(2)).inviteGuardian(GUARDIAN_ID);
        }

        @Test
        @DisplayName("Should handle mixed success and failure in bulk with detailed results")
        void shouldHandleMixedSuccessAndFailureInBulkWithDetailedResults() {
            // Arrange
            BulkInvitationRequest request = new BulkInvitationRequest(
                    List.of(GUARDIAN_ID, GUARDIAN_ID_2, GUARDIAN_ID_3)
            );

            List<BulkInvitationResponse.InvitationResult> results = List.of(
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID, "+2348012345678", true, "Invitation sent"
                    ),
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID_2, null, false, "Guardian has no phone number"
                    ),
                    new BulkInvitationResponse.InvitationResult(
                            GUARDIAN_ID_3, "+2348011111111", false, "SMS delivery failed"
                    )
            );

            BulkInvitationResponse expectedResponse = new BulkInvitationResponse(
                    3,  // totalRequested
                    1,  // invitationsSent
                    2,  // invitationsFailed
                    results
            );

            when(guardianInvitationService.inviteGuardiansBulk(any(BulkInvitationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act
            Mono<ResponseEntity<ApiResponse<BulkInvitationResponse>>> result =
                    guardianInvitationController.inviteGuardiansBulk(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        BulkInvitationResponse data = responseEntity.getBody().getData();
                        
                        // Verify statistics
                        assertThat(data.totalRequested()).isEqualTo(3);
                        assertThat(data.invitationsSent()).isEqualTo(1);
                        assertThat(data.invitationsFailed()).isEqualTo(2);

                        // Verify individual results
                        assertThat(data.results())
                                .extracting(BulkInvitationResponse.InvitationResult::success)
                                .containsExactly(true, false, false);

                        // Verify error messages are descriptive
                        assertThat(data.results().get(1).message())
                                .isEqualTo("Guardian has no phone number");
                        assertThat(data.results().get(2).message())
                                .isEqualTo("SMS delivery failed");
                    })
                    .verifyComplete();

            verify(guardianInvitationService, times(1)).inviteGuardiansBulk(request);
        }
    }
}
