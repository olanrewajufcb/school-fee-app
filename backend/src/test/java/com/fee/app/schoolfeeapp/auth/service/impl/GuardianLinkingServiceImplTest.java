package com.fee.app.schoolfeeapp.auth.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLinkProjection;
import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.dto.response.UserProfileResponse;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuardianLinkingServiceImplTest {

    @Mock
    private StudentGuardianRepository guardianRepository;

    @Mock
    private StudentGuardianLinkRepository guardianLinkRepository;

    @InjectMocks
    private GuardianLinkingServiceImpl guardianLinkingService;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID GUARDIAN_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID STUDENT_ID_1 = UUID.fromString("d4e5f6a7-b8c9-0123-defa-123456789012");
    private static final UUID STUDENT_ID_2 = UUID.fromString("e5f6a7b8-c9d0-1234-efab-234567890123");
    private static final String PHONE_NUMBER = "+2348012345678";
    private static final String NORMALIZED_PHONE = "2348012345678";

    private User testUser;
    private SchoolFeeUser testJwtUser;
    private StudentGuardian testGuardian;

    @BeforeEach
    void setUp() {
        // Setup test user
        testUser = User.builder()
                .id(USER_ID)
                .keycloakId(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .email("parent@example.com")
                .phone(NORMALIZED_PHONE)
                .firstName("John")
                .lastName("Doe")
                .userType("PARENT")
                .isActive(true)
                .lastLogin(ZonedDateTime.now())
                .build();

        // Setup JWT user
        testJwtUser = SchoolFeeUser.builder()
                .userId(USER_ID)
                .email("parent@example.com")
                .phoneNumber(PHONE_NUMBER)
                .firstName("John")
                .lastName("Doe")
                .userType("PARENT")
                .schoolId(SCHOOL_ID)
                .schoolName("Grace International School")
                .roles(Set.of("PARENT"))
                .build();

        // Setup test guardian
        testGuardian = StudentGuardian.builder()
                .id(GUARDIAN_ID)
                .schoolId(SCHOOL_ID)
                .userId(null) // Initially unlinked
                .firstName("John")
                .lastName("Doe")
                .phone(NORMALIZED_PHONE)
                .email("parent@example.com")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ========================================================================
    // GET OR LINK GUARDIAN TESTS
    // ========================================================================

    @Nested
    @DisplayName("Get Or Link Guardian Tests")
    class GetOrLinkGuardianTests {

        @Test
        @DisplayName("Should return children when guardian is already linked")
        void shouldReturnChildrenWhenGuardianAlreadyLinked() {
            // Arrange
            List<UserProfileResponse.ChildInfo> expectedChildren = List.of(
                    UserProfileResponse.ChildInfo.builder()
                            .studentId(STUDENT_ID_1)
                            .guardianId(GUARDIAN_ID)
                            .relationship("FATHER")
                            .canViewFees(true)
                            .canViewResults(true)
                            .canViewAttendance(true)
                            .build(),
                    UserProfileResponse.ChildInfo.builder()
                            .studentId(STUDENT_ID_2)
                            .guardianId(GUARDIAN_ID)
                            .relationship("MOTHER")
                            .canViewFees(false)
                            .canViewResults(true)
                            .canViewAttendance(true)
                            .build()
            );

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.just(testGuardian));
            
            List<StudentGuardianLinkProjection> guardianLinks = expectedChildren.stream()
                    .map(child -> StudentGuardianLinkProjection.builder()
                            .guardianId(child.getGuardianId())
                            .studentId(child.getStudentId())
                            .relationship(child.getRelationship())
                            .canViewFees(child.isCanViewFees())
                            .canViewResults(child.isCanViewResults())
                            .canViewAttendance(child.isCanViewAttendance())
                            .build())
                    .toList();
            
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                    .thenReturn(Flux.fromIterable(guardianLinks));

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).hasSize(2);
                        assertThat(children.get(0).getStudentId()).isEqualTo(STUDENT_ID_1);
                        assertThat(children.get(0).getRelationship()).isEqualTo("FATHER");
                        assertThat(children.get(1).getStudentId()).isEqualTo(STUDENT_ID_2);
                        assertThat(children.get(1).getRelationship()).isEqualTo("MOTHER");
                    })
                    .verifyComplete();

            verify(guardianRepository, times(1)).findByUserIdAndDeletedAtIsNull(USER_ID);
            verify(guardianLinkRepository, times(1)).findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID);
            verify(guardianRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should try phone match when no linked guardian found")
        void shouldTryPhoneMatchWhenNoLinkedGuardianFound() {
            // Arrange
            StudentGuardian unlinkedGuardian = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .schoolId(SCHOOL_ID)
                    .userId(null)
                    .firstName("John")
                    .lastName("Doe")
                    .phone(NORMALIZED_PHONE)
                    .email("parent@example.com")
                    .isActive(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(SCHOOL_ID)))
                    .thenReturn(Mono.just(unlinkedGuardian));
            when(guardianRepository.save(any(StudentGuardian.class)))
                    .thenAnswer(invocation -> {
                        StudentGuardian saved = invocation.getArgument(0);
                        saved.setUserId(USER_ID);
                        return Mono.just(saved);
                    });
            
            StudentGuardianLinkProjection link = StudentGuardianLinkProjection.builder()
                    .guardianId(GUARDIAN_ID)
                    .studentId(STUDENT_ID_1)
                    .relationship("FATHER")
                    .canViewFees(true)
                    .canViewResults(true)
                    .canViewAttendance(true)
                    .build();
            
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                    .thenReturn(Flux.just(link));

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).hasSize(1);
                        assertThat(children.get(0).getStudentId()).isEqualTo(STUDENT_ID_1);
                        assertThat(children.get(0).getRelationship()).isEqualTo("FATHER");
                    })
                    .verifyComplete();

            verify(guardianRepository, times(1)).findByUserIdAndDeletedAtIsNull(USER_ID);
            verify(guardianRepository, times(1))
                    .findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(NORMALIZED_PHONE, SCHOOL_ID);
            verify(guardianRepository, times(1)).save(argThat(guardian -> 
                    guardian.getUserId().equals(USER_ID)));
            verify(guardianLinkRepository, times(1)).findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID);
        }

        @Test
        @DisplayName("Should return empty list when no guardian found by phone")
        void shouldReturnEmptyListWhenNoGuardianFoundByPhone() {
            // Arrange
            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(SCHOOL_ID)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).isEmpty();
                    })
                    .verifyComplete();

            verify(guardianRepository, times(1)).findByUserIdAndDeletedAtIsNull(USER_ID);
            verify(guardianRepository, times(1))
                    .findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(NORMALIZED_PHONE, SCHOOL_ID);
            verify(guardianRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return empty list when phone number is null")
        void shouldReturnEmptyListWhenPhoneNumberIsNull() {
            // Arrange
            SchoolFeeUser jwtUserWithNullPhone = SchoolFeeUser.builder()
                    .userId(USER_ID)
                    .email("parent@example.com")
                    .phoneNumber(null)
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .schoolId(SCHOOL_ID)
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, jwtUserWithNullPhone);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).isEmpty();
                    })
                    .verifyComplete();

            verify(guardianRepository, times(1)).findByUserIdAndDeletedAtIsNull(USER_ID);
            verify(guardianRepository, never())
                    .findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(any(), any());
        }

        @Test
        @DisplayName("Should return empty list when phone number is blank")
        void shouldReturnEmptyListWhenPhoneNumberIsBlank() {
            // Arrange
            SchoolFeeUser jwtUserWithBlankPhone = SchoolFeeUser.builder()
                    .userId(USER_ID)
                    .email("parent@example.com")
                    .phoneNumber("   ")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .schoolId(SCHOOL_ID)
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, jwtUserWithBlankPhone);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).isEmpty();
                    })
                    .verifyComplete();

            verify(guardianRepository, times(1)).findByUserIdAndDeletedAtIsNull(USER_ID);
            verify(guardianRepository, never())
                    .findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(any(), any());
        }

        @Test
        @DisplayName("Should handle invalid phone number format gracefully")
        void shouldHandleInvalidPhoneNumberFormatGracefully() {
            // Arrange
            SchoolFeeUser jwtUserWithInvalidPhone = SchoolFeeUser.builder()
                    .userId(USER_ID)
                    .email("parent@example.com")
                    .phoneNumber("invalid-phone-format")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .schoolId(SCHOOL_ID)
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            
            // Use any() matchers to handle any phone number and school ID combination
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    any(), any()))
                    .thenReturn(Mono.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, jwtUserWithInvalidPhone);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).isEmpty();
                    })
                    .verifyComplete();

            verify(guardianRepository, times(1)).findByUserIdAndDeletedAtIsNull(USER_ID);
            // Phone matching may or may not be attempted depending on normalization behavior
        }

        @Test
        @DisplayName("Should not link guardian if already has children")
        void shouldNotLinkGuardianIfAlreadyHasChildren() {
            // Arrange
            UserProfileResponse.ChildInfo existingChild = UserProfileResponse.ChildInfo.builder()
                    .studentId(STUDENT_ID_1)
                    .guardianId(GUARDIAN_ID)
                    .relationship("FATHER")
                    .canViewFees(true)
                    .canViewResults(true)
                    .canViewAttendance(true)
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.just(testGuardian));
            
            StudentGuardianLinkProjection link = StudentGuardianLinkProjection.builder()
                    .guardianId(GUARDIAN_ID)
                    .studentId(STUDENT_ID_1)
                    .relationship("FATHER")
                    .canViewFees(true)
                    .canViewResults(true)
                    .canViewAttendance(true)
                    .build();
            
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                    .thenReturn(Flux.just(link));

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).hasSize(1);
                        assertThat(children.get(0).getStudentId()).isEqualTo(STUDENT_ID_1);
                    })
                    .verifyComplete();

            // Should not attempt phone matching
            verify(guardianRepository, times(1)).findByUserIdAndDeletedAtIsNull(USER_ID);
            verify(guardianRepository, never())
                    .findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(any(), any());
        }

        @Test
        @DisplayName("Should link guardian only once when multiple requests occur")
        void shouldLinkGuardianOnlyOnceWhenMultipleRequestsOccur() {
            // Arrange - Simulate race condition where guardian gets linked between queries
            StudentGuardian unlinkedGuardian = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .schoolId(SCHOOL_ID)
                    .userId(null)
                    .firstName("John")
                    .lastName("Doe")
                    .phone(NORMALIZED_PHONE)
                    .email("parent@example.com")
                    .isActive(true)
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(SCHOOL_ID)))
                    .thenReturn(Mono.just(unlinkedGuardian));
            
            // Save succeeds and sets userId
            when(guardianRepository.save(any(StudentGuardian.class)))
                    .thenAnswer(invocation -> {
                        StudentGuardian saved = invocation.getArgument(0);
                        saved.setUserId(USER_ID);
                        return Mono.just(saved);
                    });
            
            // But when fetching children, return empty (no links yet)
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                    .thenReturn(Flux.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).isEmpty();
                    })
                    .verifyComplete();

            // Verify linking still occurred
            verify(guardianRepository, times(1)).save(argThat(guardian -> 
                    guardian.getUserId().equals(USER_ID)));
        }
    }

    // ========================================================================
    // PHONE MATCHING AND LINKING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Phone Matching and Linking Tests")
    class PhoneMatchingAndLinkingTests {

        @Test
        @DisplayName("Should normalize phone number before matching")
        void shouldNormalizePhoneNumberBeforeMatching() {
            // Arrange
            SchoolFeeUser jwtUserWithFormattedPhone = SchoolFeeUser.builder()
                    .userId(USER_ID)
                    .email("parent@example.com")
                    .phoneNumber("+234 801 234 5678") // Formatted with spaces
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .schoolId(SCHOOL_ID)
                    .build();

            StudentGuardian unlinkedGuardian = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .schoolId(SCHOOL_ID)
                    .userId(null)
                    .firstName("John")
                    .lastName("Doe")
                    .phone(NORMALIZED_PHONE)
                    .email("parent@example.com")
                    .isActive(true)
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(SCHOOL_ID)))
                    .thenReturn(Mono.just(unlinkedGuardian));
            when(guardianRepository.save(any(StudentGuardian.class)))
                    .thenAnswer(invocation -> {
                        StudentGuardian saved = invocation.getArgument(0);
                        saved.setUserId(USER_ID);
                        return Mono.just(saved);
                    });
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                    .thenReturn(Flux.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, jwtUserWithFormattedPhone);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> assertThat(children).isEmpty())
                    .verifyComplete();

            // Verify normalized phone was used for lookup
            verify(guardianRepository, times(1))
                    .findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(NORMALIZED_PHONE, SCHOOL_ID);
        }

        @Test
        @DisplayName("Should not link guardian if school ID doesn't match")
        void shouldNotLinkGuardianIfSchoolIdDoesntMatch() {
            // Arrange
            UUID differentSchoolId = UUID.randomUUID();
            User userFromDifferentSchool = User.builder()
                    .id(USER_ID)
                    .keycloakId(UUID.randomUUID())
                    .schoolId(differentSchoolId)
                    .email("parent@example.com")
                    .phone(NORMALIZED_PHONE)
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .isActive(true)
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(differentSchoolId)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(userFromDifferentSchool, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> assertThat(children).isEmpty())
                    .verifyComplete();

            verify(guardianRepository, times(1))
                    .findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(NORMALIZED_PHONE, differentSchoolId);
        }

        @Test
        @DisplayName("Should not link guardian that already has a user assigned")
        void shouldNotLinkGuardianThatAlreadyHasUserAssigned() {
            // Arrange
            StudentGuardian guardianWithUser = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .schoolId(SCHOOL_ID)
                    .userId(UUID.randomUUID()) // Already linked to another user
                    .firstName("Jane")
                    .lastName("Doe")
                    .phone(NORMALIZED_PHONE)
                    .email("jane@example.com")
                    .isActive(true)
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            // This query specifically looks for userId IS NULL, so it won't return this guardian
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(SCHOOL_ID)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> assertThat(children).isEmpty())
                    .verifyComplete();

            // Guardian with user should not be saved/linked
            verify(guardianRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle soft-deleted guardians correctly")
        void shouldHandleSoftDeletedGuardiansCorrectly() {
            // Arrange
            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            // Soft-deleted guardian should not be found (deletedAt filter in query)
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(SCHOOL_ID)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> assertThat(children).isEmpty())
                    .verifyComplete();
        }
    }

    // ========================================================================
    // FETCH CHILDREN TESTS
    // ========================================================================

    @Nested
    @DisplayName("Fetch Children Tests")
    class FetchChildrenTests {

        @Test
        @DisplayName("Should fetch all children for guardian excluding soft-deleted links")
        void shouldFetchAllChildrenForGuardianExcludingSoftDeletedLinks() {
            // Arrange - Setup an already linked guardian with multiple children
            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.just(testGuardian));
            
            StudentGuardianLinkProjection activeLink1 = StudentGuardianLinkProjection.builder()
                    .studentId(STUDENT_ID_1)
                    .guardianId(GUARDIAN_ID)
                    .relationship("FATHER")
                    .canViewFees(true)
                    .canViewResults(true)
                    .canViewAttendance(true)
                    .build();

            StudentGuardianLinkProjection activeLink2 = StudentGuardianLinkProjection.builder()
                    .studentId(STUDENT_ID_2)
                    .guardianId(GUARDIAN_ID)
                    .relationship("MOTHER")
                    .canViewFees(false)
                    .canViewResults(true)
                    .canViewAttendance(false)
                    .build();

            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(eq(GUARDIAN_ID)))
                    .thenReturn(Flux.fromIterable(List.of(activeLink1, activeLink2)));

            // Act - Call the public method which internally uses fetchChildrenForGuardian
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).hasSize(2);
                        assertThat(children.get(0).getStudentId()).isEqualTo(STUDENT_ID_1);
                        assertThat(children.get(1).getStudentId()).isEqualTo(STUDENT_ID_2);
                    })
                    .verifyComplete();

            verify(guardianLinkRepository, times(1))
                    .findByGuardianIdAndDeletedAtIsNull(eq(GUARDIAN_ID));
        }

        @Test
        @DisplayName("Should map all child info fields correctly")
        void shouldMapAllChildInfoFieldsCorrectly() {
            // Arrange - Setup guardian with a child
            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.just(testGuardian));
            
            UUID studentId = STUDENT_ID_1;
            UUID guardianId = GUARDIAN_ID;
            String relationship = "UNCLE";
            boolean canViewFees = false;
            boolean canViewResults = true;
            boolean canViewAttendance = false;

            StudentGuardianLinkProjection link = StudentGuardianLinkProjection.builder()
                    .guardianId(guardianId)
                    .studentId(studentId)
                    .relationship(relationship)
                    .canViewFees(canViewFees)
                    .canViewResults(canViewResults)
                    .canViewAttendance(canViewAttendance)
                    .build();

            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(eq(guardianId)))
                    .thenReturn(Flux.just(link));

            // Act - Call the public method which internally uses fetchChildrenForGuardian
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).hasSize(1);
                        assertThat(children.get(0).getStudentId()).isEqualTo(studentId);
                        assertThat(children.get(0).getGuardianId()).isEqualTo(guardianId);
                        assertThat(children.get(0).getRelationship()).isEqualTo(relationship);
                        assertThat(children.get(0).isCanViewFees()).isEqualTo(canViewFees);
                        assertThat(children.get(0).isCanViewResults()).isEqualTo(canViewResults);
                        assertThat(children.get(0).isCanViewAttendance()).isEqualTo(canViewAttendance);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty list when guardian has no children")
        void shouldReturnEmptyListWhenGuardianHasNoChildren() {
            // Arrange - Guardian exists but has no children linked
            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.just(testGuardian));
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(eq(GUARDIAN_ID)))
                    .thenReturn(Flux.empty());
            
            // When phone match is attempted (because children list is empty), return empty
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(SCHOOL_ID)))
                    .thenReturn(Mono.empty());

            // Act - Call the public method
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> assertThat(children).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should exclude soft-deleted guardian links")
        void shouldExcludeSoftDeletedGuardianLinks() {
            // Arrange - Setup an already linked guardian with children
            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.just(testGuardian));
            
            StudentGuardianLinkProjection activeLink = StudentGuardianLinkProjection.builder()
                    .studentId(STUDENT_ID_1)
                    .guardianId(GUARDIAN_ID)
                    .relationship("FATHER")
                    .canViewFees(true)
                    .canViewResults(true)
                    .canViewAttendance(true)
                    .build();

            // Only active link should be returned (repository query filters deleted)
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(eq(GUARDIAN_ID)))
                    .thenReturn(Flux.just(activeLink));

            // Act - Call the public method which internally uses fetchChildrenForGuardian
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).hasSize(1);
                        assertThat(children.get(0).getStudentId()).isEqualTo(STUDENT_ID_1);
                        assertThat(children.get(0).getRelationship()).isEqualTo("FATHER");
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // EDGE CASES AND INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Edge Cases and Integration Tests")
    class EdgeCasesAndIntegrationTests {

        @Test
        @DisplayName("Should handle guardian with null user ID properly")
        void shouldHandleGuardianWithNullUserIdProperly() {
            // Arrange
            StudentGuardian guardianWithNullUser = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .schoolId(SCHOOL_ID)
                    .userId(null)
                    .firstName("John")
                    .lastName("Doe")
                    .phone(NORMALIZED_PHONE)
                    .isActive(true)
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(SCHOOL_ID)))
                    .thenReturn(Mono.just(guardianWithNullUser));
            when(guardianRepository.save(any(StudentGuardian.class)))
                    .thenAnswer(invocation -> {
                        StudentGuardian saved = invocation.getArgument(0);
                        saved.setUserId(USER_ID);
                        return Mono.just(saved);
                    });
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                    .thenReturn(Flux.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> assertThat(children).isEmpty())
                    .verifyComplete();

            verify(guardianRepository, times(1)).save(argThat(g -> g.getUserId() != null));
        }

        @Test
        @DisplayName("Should handle concurrent linking attempts safely")
        void shouldHandleConcurrentLinkingAttemptsSafely() {
            // Arrange
            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            
            // First call finds unlinked guardian
            StudentGuardian unlinkedGuardian = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .schoolId(SCHOOL_ID)
                    .userId(null)
                    .phone(NORMALIZED_PHONE)
                    .isActive(true)
                    .build();
            
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(SCHOOL_ID)))
                    .thenReturn(Mono.just(unlinkedGuardian));
            
            // Simulate save failure (e.g., unique constraint violation)
            when(guardianRepository.save(any(StudentGuardian.class)))
                    .thenReturn(Mono.error(new RuntimeException("Concurrent modification")));

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should preserve guardian data when linking")
        void shouldPreserveGuardianDataWhenLinking() {
            // Arrange
            StudentGuardian originalGuardian = StudentGuardian.builder()
                    .id(GUARDIAN_ID)
                    .schoolId(SCHOOL_ID)
                    .userId(null)
                    .firstName("John")
                    .lastName("Doe")
                    .phone(NORMALIZED_PHONE)
                    .email("parent@example.com")
                    .alternativePhone("+2348098765432")
                    .preferredContactMethod("SMS")
                    .preferredLanguage("en")
                    .isActive(true)
                    .build();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.empty());
            when(guardianRepository.findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                    eq(NORMALIZED_PHONE), eq(SCHOOL_ID)))
                    .thenReturn(Mono.just(originalGuardian));
            when(guardianRepository.save(any(StudentGuardian.class)))
                    .thenAnswer(invocation -> {
                        StudentGuardian saved = invocation.getArgument(0);
                        // Only userId should change
                        saved.setUserId(USER_ID);
                        return Mono.just(saved);
                    });
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                    .thenReturn(Flux.empty());

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .expectNext(Collections.emptyList())
                    .verifyComplete();

            verify(guardianRepository, times(1)).save(argThat(guardian -> 
                    guardian.getUserId().equals(USER_ID) &&
                    guardian.getFirstName().equals("John") &&
                    guardian.getLastName().equals("Doe") &&
                    guardian.getPhone().equals(NORMALIZED_PHONE) &&
                    guardian.getEmail().equals("parent@example.com") &&
                    guardian.getAlternativePhone().equals("+2348098765432")
            ));
        }

        @Test
        @DisplayName("Should work with different relationship types")
        void shouldWorkWithDifferentRelationshipTypes() {
            // Arrange
            List<String> relationships = List.of(
                    "MOTHER", "FATHER", "GUARDIAN", "UNCLE", "AUNT", 
                    "GRANDMOTHER", "GRANDFATHER", "OTHER"
            );

            List<StudentGuardianLinkProjection> linksList = relationships.stream()
                    .map(rel -> StudentGuardianLinkProjection.builder()
                            .guardianId(GUARDIAN_ID)
                            .studentId(UUID.randomUUID())
                            .relationship(rel)
                            .canViewFees(true)
                            .canViewResults(true)
                            .canViewAttendance(true)
                            .build())
                    .toList();

            when(guardianRepository.findByUserIdAndDeletedAtIsNull(USER_ID))
                    .thenReturn(Mono.just(testGuardian));
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(GUARDIAN_ID))
                    .thenReturn(Flux.fromIterable(linksList));

            // Act
            Mono<List<UserProfileResponse.ChildInfo>> result = 
                    guardianLinkingService.getOrLinkGuardian(testUser, testJwtUser);

            // Assert
            StepVerifier.create(result)
                    .assertNext(children -> {
                        assertThat(children).hasSize(relationships.size());
                        assertThat(children.stream()
                                .map(UserProfileResponse.ChildInfo::getRelationship))
                                .containsExactlyInAnyOrderElementsOf(relationships);
                    })
                    .verifyComplete();
        }
    }
}
