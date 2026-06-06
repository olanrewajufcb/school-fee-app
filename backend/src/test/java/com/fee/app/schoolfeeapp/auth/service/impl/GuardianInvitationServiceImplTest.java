package com.fee.app.schoolfeeapp.auth.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.dto.request.BulkInvitationRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.BulkInvitationResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.GuardianInvitationResponse;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.notification.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuardianInvitationServiceImplTest {

    @Mock
    private StudentGuardianRepository guardianRepository;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private GuardianInvitationServiceImpl guardianInvitationService;

    private static final UUID GUARDIAN_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final String PHONE_NUMBER = "+2348012345678";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final String FULL_NAME = "John Doe";

    private StudentGuardian guardianWithoutAccount;
    private StudentGuardian guardianWithAccount;

    @BeforeEach
    void setUp() {
        // Guardian without linked account (userId is null)
        guardianWithoutAccount = StudentGuardian.builder()
                .id(GUARDIAN_ID)
                .schoolId(SCHOOL_ID)
                .firstName(FIRST_NAME)
                .lastName(LAST_NAME)
                .phone(PHONE_NUMBER)
                .email("john.doe@example.com")
                .userId(null) // No linked account
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        // Guardian with linked account (userId is not null)
        UUID userId = UUID.randomUUID();
        guardianWithAccount = StudentGuardian.builder()
                .id(userId)
                .schoolId(SCHOOL_ID)
                .firstName("Jane")
                .lastName("Smith")
                .phone("+2348098765432")
                .email("jane.smith@example.com")
                .userId(userId) // Has linked account
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }

    // ========================================================================
    // INVITE SINGLE GUARDIAN TESTS
    // ========================================================================

    @Nested
    @DisplayName("Invite Single Guardian")
    class InviteSingleGuardianTests {

        @Test
        @DisplayName("Should send invitation SMS to guardian without account")
        void shouldSendInvitationSmsToGuardianWithoutAccount() {
            // Arrange
            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithoutAccount));
            when(smsService.send(eq(PHONE_NUMBER), anyString()))
                    .thenReturn(Mono.empty());

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.guardianId()).isEqualTo(GUARDIAN_ID);
                        assertThat(response.guardianName()).isEqualTo(FULL_NAME);
                        assertThat(response.phoneNumber()).isEqualTo(PHONE_NUMBER);
                        assertThat(response.invitationSent()).isTrue();
                        assertThat(response.invitationToken()).isNotNull();
                        assertThat(response.message()).contains("Invitation SMS sent");
                    })
                    .verifyComplete();

            verify(guardianRepository, times(1)).findById(GUARDIAN_ID);
            verify(smsService, times(1)).send(eq(PHONE_NUMBER), anyString());
        }

        @Test
        @DisplayName("Should return error message if guardian already has account")
        void shouldReturnErrorMessageIfGuardianAlreadyHasAccount() {
            // Arrange
            when(guardianRepository.findById(guardianWithAccount.getId()))
                    .thenReturn(Mono.just(guardianWithAccount));

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(guardianWithAccount.getId());

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.guardianId()).isEqualTo(guardianWithAccount.getId());
                        assertThat(response.guardianName()).isEqualTo("Jane Smith");
                        assertThat(response.phoneNumber()).isEqualTo("+2348098765432");
                        assertThat(response.invitationSent()).isFalse();
                        assertThat(response.invitationToken()).isNull();
                        assertThat(response.message()).isEqualTo("Guardian already has an account linked");
                    })
                    .verifyComplete();

            verify(guardianRepository, times(1)).findById(guardianWithAccount.getId());
            verify(smsService, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return error if guardian not found")
        void shouldReturnErrorIfGuardianNotFound() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(guardianRepository.findById(nonExistentId))
                    .thenReturn(Mono.empty());

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(nonExistentId);

            // Assert
            StepVerifier.create(result)
                    .expectErrorMatches(error -> 
                        error instanceof SchoolFeeException &&
                        ((SchoolFeeException) error).getErrorCode().equals("GUARDIAN_NOT_FOUND")
                    )
                    .verify();

            verify(guardianRepository, times(1)).findById(nonExistentId);
            verify(smsService, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle SMS sending failure")
        void shouldHandleSmsSendingFailure() {
            // Arrange
            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithoutAccount));
            when(smsService.send(eq(PHONE_NUMBER), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("SMS gateway error")));

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();

            verify(guardianRepository, times(1)).findById(GUARDIAN_ID);
            verify(smsService, times(1)).send(eq(PHONE_NUMBER), anyString());
        }

        @Test
        @DisplayName("Should generate valid invitation token")
        void shouldGenerateValidInvitationToken() {
            // Arrange
            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithoutAccount));
            when(smsService.send(eq(PHONE_NUMBER), anyString()))
                    .thenReturn(Mono.empty());

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.invitationToken()).isNotNull();
                        assertThat(response.invitationToken()).hasSize(12);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should build correct invitation message")
        void shouldBuildCorrectInvitationMessage() {
            // Arrange
            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithoutAccount));
            when(smsService.send(eq(PHONE_NUMBER), anyString()))
                    .thenAnswer(invocation -> {
                        String message = invocation.getArgument(1);
                        assertThat(message).contains(FIRST_NAME);
                        assertThat(message).contains("https://schoolfee.app/join/");
                        return Mono.empty();
                    });

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.invitationSent()).isTrue();
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // BULK INVITATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Bulk Invitation")
    class BulkInvitationTests {

        @Test
        @DisplayName("Should send invitations to multiple guardians successfully")
        void shouldSendInvitationsToMultipleGuardiansSuccessfully() {
            // Arrange
            UUID guardianId1 = UUID.randomUUID();
            UUID guardianId2 = UUID.randomUUID();
            UUID guardianId3 = UUID.randomUUID();

            StudentGuardian guardian1 = StudentGuardian.builder()
                    .id(guardianId1)
                    .firstName("Alice")
                    .lastName("Johnson")
                    .phone("+2348011111111")
                    .userId(null)
                    .build();

            StudentGuardian guardian2 = StudentGuardian.builder()
                    .id(guardianId2)
                    .firstName("Bob")
                    .lastName("Williams")
                    .phone("+2348022222222")
                    .userId(null)
                    .build();

            StudentGuardian guardian3 = StudentGuardian.builder()
                    .id(guardianId3)
                    .firstName("Carol")
                    .lastName("Brown")
                    .phone("+2348033333333")
                    .userId(null)
                    .build();

            BulkInvitationRequest request = new BulkInvitationRequest(List.of(guardianId1, guardianId2, guardianId3));

            when(guardianRepository.findById(guardianId1)).thenReturn(Mono.just(guardian1));
            when(guardianRepository.findById(guardianId2)).thenReturn(Mono.just(guardian2));
            when(guardianRepository.findById(guardianId3)).thenReturn(Mono.just(guardian3));
            when(smsService.send(anyString(), anyString())).thenReturn(Mono.empty());

            // Act
            Mono<BulkInvitationResponse> result = guardianInvitationService.inviteGuardiansBulk(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.totalRequested()).isEqualTo(3);
                        assertThat(response.invitationsSent()).isEqualTo(3);
                        assertThat(response.invitationsFailed()).isEqualTo(0);
                        assertThat(response.results()).hasSize(3);
                        assertThat(response.results()).allMatch(r -> r.success());
                    })
                    .verifyComplete();

            verify(guardianRepository, times(3)).findById(any(UUID.class));
            verify(smsService, times(3)).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle mixed success and failure in bulk invitation")
        void shouldHandleMixedSuccessAndFailureInBulkInvitation() {
            // Arrange
            UUID guardianId1 = UUID.randomUUID();
            UUID guardianId2 = UUID.randomUUID();
            UUID guardianId3 = UUID.randomUUID();

            StudentGuardian guardian1 = StudentGuardian.builder()
                    .id(guardianId1)
                    .firstName("Alice")
                    .lastName("Johnson")
                    .phone("+2348011111111")
                    .userId(null)
                    .build();

            StudentGuardian guardianWithAcct = StudentGuardian.builder()
                    .id(guardianId2)
                    .firstName("Bob")
                    .lastName("Williams")
                    .phone("+2348022222222")
                    .userId(UUID.randomUUID()) // Has account
                    .build();

            BulkInvitationRequest request = new BulkInvitationRequest(List.of(guardianId1, guardianId2, guardianId3));

            when(guardianRepository.findById(guardianId1)).thenReturn(Mono.just(guardian1));
            when(guardianRepository.findById(guardianId2)).thenReturn(Mono.just(guardianWithAcct));
            when(guardianRepository.findById(guardianId3)).thenReturn(Mono.empty()); // Not found
            when(smsService.send(anyString(), anyString())).thenReturn(Mono.empty());

            // Act
            Mono<BulkInvitationResponse> result = guardianInvitationService.inviteGuardiansBulk(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.totalRequested()).isEqualTo(3);
                        assertThat(response.invitationsSent()).isEqualTo(1);
                        assertThat(response.invitationsFailed()).isEqualTo(2);
                        assertThat(response.results()).hasSize(3);
                        
                        // First guardian succeeded
                        assertThat(response.results().get(0).success()).isTrue();
                        
                        // Second guardian failed (already has account)
                        assertThat(response.results().get(1).success()).isFalse();
                        assertThat(response.results().get(1).message()).contains("already has an account");
                        
                        // Third guardian failed (not found)
                        assertThat(response.results().get(2).success()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle all failures in bulk invitation")
        void shouldHandleAllFailuresInBulkInvitation() {
            // Arrange
            UUID guardianId1 = UUID.randomUUID();
            UUID guardianId2 = UUID.randomUUID();

            BulkInvitationRequest request = new BulkInvitationRequest(List.of(guardianId1, guardianId2));

            when(guardianRepository.findById(guardianId1)).thenReturn(Mono.empty());
            when(guardianRepository.findById(guardianId2)).thenReturn(Mono.empty());

            // Act
            Mono<BulkInvitationResponse> result = guardianInvitationService.inviteGuardiansBulk(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.totalRequested()).isEqualTo(2);
                        assertThat(response.invitationsSent()).isEqualTo(0);
                        assertThat(response.invitationsFailed()).isEqualTo(2);
                        assertThat(response.results()).hasSize(2);
                        assertThat(response.results()).allMatch(r -> !r.success());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle empty guardian list")
        void shouldHandleEmptyGuardianList() {
            // Arrange
            BulkInvitationRequest request = new BulkInvitationRequest(List.of());

            // Act
            Mono<BulkInvitationResponse> result = guardianInvitationService.inviteGuardiansBulk(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.totalRequested()).isEqualTo(0);
                        assertThat(response.invitationsSent()).isEqualTo(0);
                        assertThat(response.invitationsFailed()).isEqualTo(0);
                        assertThat(response.results()).isEmpty();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should continue processing other guardians when one fails")
        void shouldContinueProcessingOtherGuardiansWhenOneFails() {
            // Arrange
            UUID guardianId1 = UUID.randomUUID();
            UUID guardianId2 = UUID.randomUUID();
            UUID guardianId3 = UUID.randomUUID();

            StudentGuardian guardian1 = StudentGuardian.builder()
                    .id(guardianId1)
                    .firstName("Alice")
                    .lastName("Johnson")
                    .phone("+2348011111111")
                    .userId(null)
                    .build();

            StudentGuardian guardian3 = StudentGuardian.builder()
                    .id(guardianId3)
                    .firstName("Carol")
                    .lastName("Brown")
                    .phone("+2348033333333")
                    .userId(null)
                    .build();

            BulkInvitationRequest request = new BulkInvitationRequest(List.of(guardianId1, guardianId2, guardianId3));

            when(guardianRepository.findById(guardianId1)).thenReturn(Mono.just(guardian1));
            when(guardianRepository.findById(guardianId2)).thenReturn(Mono.error(new RuntimeException("Database error")));
            when(guardianRepository.findById(guardianId3)).thenReturn(Mono.just(guardian3));
            when(smsService.send(anyString(), anyString())).thenReturn(Mono.empty());

            // Act
            Mono<BulkInvitationResponse> result = guardianInvitationService.inviteGuardiansBulk(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.totalRequested()).isEqualTo(3);
                        assertThat(response.invitationsSent()).isEqualTo(2);
                        assertThat(response.invitationsFailed()).isEqualTo(1);
                        
                        // First and third succeeded, second failed
                        assertThat(response.results().get(0).success()).isTrue();
                        assertThat(response.results().get(1).success()).isFalse();
                        assertThat(response.results().get(2).success()).isTrue();
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // EDGE CASES AND ERROR HANDLING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandlingTests {

        @Test
        @DisplayName("Should handle guardian with null phone number")
        void shouldHandleGuardianWithNullPhoneNumber() {
            // Arrange
            StudentGuardian guardianWithNullPhone = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .firstName(FIRST_NAME)
                    .lastName(LAST_NAME)
                    .phone(null)
                    .userId(null)
                    .build();

            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithNullPhone));
            when(smsService.send(eq(null), anyString()))
                    .thenReturn(Mono.error(new IllegalArgumentException("Phone number cannot be null")));

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should handle guardian with special characters in name")
        void shouldHandleGuardianWithSpecialCharactersInName() {
            // Arrange
            StudentGuardian guardianWithSpecialChars = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .firstName("O'Brien")
                    .lastName("O'Connor-Smith")
                    .phone(PHONE_NUMBER)
                    .userId(null)
                    .build();

            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithSpecialChars));
            when(smsService.send(eq(PHONE_NUMBER), anyString()))
                    .thenReturn(Mono.empty());

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.guardianName()).isEqualTo("O'Brien O'Connor-Smith");
                        assertThat(response.invitationSent()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle very long guardian names")
        void shouldHandleVeryLongGuardianNames() {
            // Arrange
            String longFirstName = "A".repeat(100);
            String longLastName = "B".repeat(100);
            
            StudentGuardian guardianWithLongName = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .firstName(longFirstName)
                    .lastName(longLastName)
                    .phone(PHONE_NUMBER)
                    .userId(null)
                    .build();

            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithLongName));
            when(smsService.send(eq(PHONE_NUMBER), anyString()))
                    .thenReturn(Mono.empty());

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.guardianName()).hasSize(201); // 100 + space + 100
                        assertThat(response.invitationSent()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle international phone numbers")
        void shouldHandleInternationalPhoneNumbers() {
            // Arrange
            String internationalPhone = "+44 20 7946 0958";
            StudentGuardian guardianWithIntlPhone = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .firstName(FIRST_NAME)
                    .lastName(LAST_NAME)
                    .phone(internationalPhone)
                    .userId(null)
                    .build();

            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithIntlPhone));
            when(smsService.send(eq(internationalPhone), anyString()))
                    .thenReturn(Mono.empty());

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.phoneNumber()).isEqualTo(internationalPhone);
                        assertThat(response.invitationSent()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle concurrent invitation requests")
        void shouldHandleConcurrentInvitationRequests() {
            // Arrange
            UUID guardianId1 = UUID.randomUUID();
            UUID guardianId2 = UUID.randomUUID();

            StudentGuardian guardian1 = StudentGuardian.builder()
                    .id(guardianId1)
                    .firstName("Alice")
                    .lastName("Johnson")
                    .phone("+2348011111111")
                    .userId(null)
                    .build();

            StudentGuardian guardian2 = StudentGuardian.builder()
                    .id(guardianId2)
                    .firstName("Bob")
                    .lastName("Williams")
                    .phone("+2348022222222")
                    .userId(null)
                    .build();

            when(guardianRepository.findById(guardianId1)).thenReturn(Mono.just(guardian1));
            when(guardianRepository.findById(guardianId2)).thenReturn(Mono.just(guardian2));
            when(smsService.send(anyString(), anyString())).thenReturn(Mono.empty());

            // Act - Send both invitations concurrently
            Mono<GuardianInvitationResponse> result1 = guardianInvitationService.inviteGuardian(guardianId1);
            Mono<GuardianInvitationResponse> result2 = guardianInvitationService.inviteGuardian(guardianId2);

            // Assert
            StepVerifier.create(result1.zipWith(result2))
                    .assertNext(tuple -> {
                        assertThat(tuple.getT1().invitationSent()).isTrue();
                        assertThat(tuple.getT2().invitationSent()).isTrue();
                    })
                    .verifyComplete();
        }
    }
}
