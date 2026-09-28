package com.fee.app.schoolfeeapp.auth.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.dto.request.BulkInvitationRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.BulkInvitationResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.GuardianInvitationResponse;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.notification.service.EmailService;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
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
    private EmailService emailService;

    @Mock
    private SchoolRepository schoolRepository;

    @InjectMocks
    private GuardianInvitationServiceImpl guardianInvitationService;

    private static final UUID GUARDIAN_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final String PHONE_NUMBER = "+2348012345678";
    private static final String EMAIL = "john.doe@example.com";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final String FULL_NAME = "John Doe";

    private StudentGuardian guardianWithoutAccount;
    private StudentGuardian guardianWithAccount;
    private StudentGuardian guardianWithoutEmail;
    private School testSchool;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                guardianInvitationService, "frontendUrl", "https://schoolfee.app");

        testSchool = School.builder()
                .id(SCHOOL_ID)
                .name("Apex International Academy")
                .isActive(true)
                .build();

        // Guardian without linked account (userId is null)
        guardianWithoutAccount = StudentGuardian.builder()
                .id(GUARDIAN_ID)
                .schoolId(SCHOOL_ID)
                .firstName(FIRST_NAME)
                .lastName(LAST_NAME)
                .phone(PHONE_NUMBER)
                .email(EMAIL)
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

        // Guardian without email
        guardianWithoutEmail = StudentGuardian.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .firstName("David")
                .lastName("Mark")
                .phone("+2348055555555")
                .email(null)
                .userId(null)
                .isActive(true)
                .build();
    }

    // ========================================================================
    // INVITE SINGLE GUARDIAN TESTS
    // ========================================================================

    @Nested
    @DisplayName("Invite Single Guardian via Email")
    class InviteSingleGuardianTests {

        @Test
        @DisplayName("Should send invitation email to guardian without account")
        void shouldSendInvitationEmailToGuardianWithoutAccount() {
            // Arrange
            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithoutAccount));
            when(schoolRepository.findById(SCHOOL_ID))
                    .thenReturn(Mono.just(testSchool));
            when(emailService.sendGuardianInvitationEmail(eq(EMAIL), eq(FULL_NAME), eq("Apex International Academy"), anyString()))
                    .thenReturn(Mono.empty());

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.guardianId()).isEqualTo(GUARDIAN_ID);
                        assertThat(response.guardianName()).isEqualTo(FULL_NAME);
                        assertThat(response.phoneNumber()).isEqualTo(PHONE_NUMBER);
                        assertThat(response.email()).isEqualTo(EMAIL);
                        assertThat(response.invitationSent()).isTrue();
                        assertThat(response.invitationToken()).isNotNull();
                        assertThat(response.message()).contains("Invitation email sent to " + EMAIL);
                    })
                    .verifyComplete();

            verify(guardianRepository, times(1)).findById(GUARDIAN_ID);
            verify(schoolRepository, times(1)).findById(SCHOOL_ID);
            verify(emailService, times(1)).sendGuardianInvitationEmail(
                    eq(EMAIL), eq(FULL_NAME), eq("Apex International Academy"), anyString());
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
            verify(emailService, never()).sendGuardianInvitationEmail(anyString(), anyString(), anyString(), anyString());
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
            verify(emailService, never()).sendGuardianInvitationEmail(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should return error if guardian does not have an email address")
        void shouldReturnErrorIfGuardianDoesNotHaveEmail() {
            // Arrange
            when(guardianRepository.findById(guardianWithoutEmail.getId()))
                    .thenReturn(Mono.just(guardianWithoutEmail));

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(guardianWithoutEmail.getId());

            // Assert
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof SchoolFeeException &&
                                    ((SchoolFeeException) error).getErrorCode().equals("GUARDIAN_EMAIL_REQUIRED") &&
                                    error.getMessage().contains("does not have an email address registered")
                    )
                    .verify();

            verify(guardianRepository, times(1)).findById(guardianWithoutEmail.getId());
            verify(emailService, never()).sendGuardianInvitationEmail(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle email sending failure")
        void shouldHandleEmailSendingFailure() {
            // Arrange
            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithoutAccount));
            when(schoolRepository.findById(SCHOOL_ID))
                    .thenReturn(Mono.just(testSchool));
            when(emailService.sendGuardianInvitationEmail(eq(EMAIL), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("SMTP gateway error")));

            // Act
            Mono<GuardianInvitationResponse> result = guardianInvitationService.inviteGuardian(GUARDIAN_ID);

            // Assert
            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();

            verify(guardianRepository, times(1)).findById(GUARDIAN_ID);
            verify(emailService, times(1)).sendGuardianInvitationEmail(eq(EMAIL), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should generate valid invitation token of length 12")
        void shouldGenerateValidInvitationToken() {
            // Arrange
            when(guardianRepository.findById(GUARDIAN_ID))
                    .thenReturn(Mono.just(guardianWithoutAccount));
            when(schoolRepository.findById(SCHOOL_ID))
                    .thenReturn(Mono.just(testSchool));
            when(emailService.sendGuardianInvitationEmail(eq(EMAIL), anyString(), anyString(), anyString()))
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
    }

    // ========================================================================
    // BULK INVITATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Bulk Invitation via Email")
    class BulkInvitationTests {

        @Test
        @DisplayName("Should send email invitations to multiple guardians successfully")
        void shouldSendInvitationsToMultipleGuardiansSuccessfully() {
            // Arrange
            UUID guardianId1 = UUID.randomUUID();
            UUID guardianId2 = UUID.randomUUID();
            UUID guardianId3 = UUID.randomUUID();

            StudentGuardian guardian1 = StudentGuardian.builder()
                    .id(guardianId1)
                    .schoolId(SCHOOL_ID)
                    .firstName("Alice")
                    .lastName("Johnson")
                    .phone("+2348011111111")
                    .email("alice@example.com")
                    .userId(null)
                    .build();

            StudentGuardian guardian2 = StudentGuardian.builder()
                    .id(guardianId2)
                    .schoolId(SCHOOL_ID)
                    .firstName("Bob")
                    .lastName("Williams")
                    .phone("+2348022222222")
                    .email("bob@example.com")
                    .userId(null)
                    .build();

            StudentGuardian guardian3 = StudentGuardian.builder()
                    .id(guardianId3)
                    .schoolId(SCHOOL_ID)
                    .firstName("Carol")
                    .lastName("Brown")
                    .phone("+2348033333333")
                    .email("carol@example.com")
                    .userId(null)
                    .build();

            BulkInvitationRequest request = new BulkInvitationRequest(List.of(guardianId1, guardianId2, guardianId3));

            when(guardianRepository.findById(guardianId1)).thenReturn(Mono.just(guardian1));
            when(guardianRepository.findById(guardianId2)).thenReturn(Mono.just(guardian2));
            when(guardianRepository.findById(guardianId3)).thenReturn(Mono.just(guardian3));
            when(schoolRepository.findById(SCHOOL_ID)).thenReturn(Mono.just(testSchool));
            when(emailService.sendGuardianInvitationEmail(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            // Act
            Mono<BulkInvitationResponse> result = guardianInvitationService.inviteGuardiansBulk(request);

            // Assert
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.totalRequested()).isEqualTo(3);
                        assertThat(response.invitationsSent()).isEqualTo(3);
                        assertThat(response.invitationsFailed()).isEqualTo(0);
                        assertThat(response.results()).hasSize(3);
                        assertThat(response.results()).allMatch(BulkInvitationResponse.InvitationResult::success);
                    })
                    .verifyComplete();

            verify(guardianRepository, times(3)).findById(any(UUID.class));
            verify(emailService, times(3)).sendGuardianInvitationEmail(anyString(), anyString(), anyString(), anyString());
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
                    .schoolId(SCHOOL_ID)
                    .firstName("Alice")
                    .lastName("Johnson")
                    .phone("+2348011111111")
                    .email("alice@example.com")
                    .userId(null)
                    .build();

            StudentGuardian guardianWithAcct = StudentGuardian.builder()
                    .id(guardianId2)
                    .schoolId(SCHOOL_ID)
                    .firstName("Bob")
                    .lastName("Williams")
                    .phone("+2348022222222")
                    .email("bob@example.com")
                    .userId(UUID.randomUUID()) // Has account
                    .build();

            BulkInvitationRequest request = new BulkInvitationRequest(List.of(guardianId1, guardianId2, guardianId3));

            when(guardianRepository.findById(guardianId1)).thenReturn(Mono.just(guardian1));
            when(guardianRepository.findById(guardianId2)).thenReturn(Mono.just(guardianWithAcct));
            when(guardianRepository.findById(guardianId3)).thenReturn(Mono.empty()); // Not found
            when(schoolRepository.findById(SCHOOL_ID)).thenReturn(Mono.just(testSchool));
            when(emailService.sendGuardianInvitationEmail(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

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
    }
}
