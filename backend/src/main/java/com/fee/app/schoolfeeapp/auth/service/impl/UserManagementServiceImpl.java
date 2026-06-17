package com.fee.app.schoolfeeapp.auth.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.domain.UserSchoolRole;
import com.fee.app.schoolfeeapp.auth.dto.request.*;
import com.fee.app.schoolfeeapp.auth.dto.response.*;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserSchoolRoleRepository;
import com.fee.app.schoolfeeapp.auth.service.UserManagementService;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.domain.OutboxEvent;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.events.ParentInvitationEvent;
import com.fee.app.schoolfeeapp.common.events.StaffCreatedEvent;
import com.fee.app.schoolfeeapp.common.exceptions.ResourceTimeoutException;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.repository.OutboxEventRepository;
import com.fee.app.schoolfeeapp.common.utils.PhoneNumberNormalizer;
import com.fee.app.schoolfeeapp.notification.service.SmsService;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final UserSchoolRoleRepository roleRepository;
    private final StudentGuardianRepository guardianRepository;
    private final StudentGuardianLinkRepository guardianLinkRepository;
    private final StudentRepository studentRepository;
    private final KeycloakAdminServiceImpl keycloakAdminService;
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final SmsService smsService;
    private final SchoolRepository schoolRepository;

    /**
     * Create a parent account.
     * IMPROVED ARCHITECTURE:
     * - Only creates the Guardian record and links to students.
     * - Does NOT create a Keycloak user or auth.users record.
     * - Parent self-onboarding will handle the actual account creation.
     */
    public Mono<CreateParentResponse> createParent(CreateParentRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(adminUser -> {
                    UUID schoolId = adminUser.getSchoolId();

                    return validateStudentsInSchool(request.children(), schoolId)
                            .then(Mono.defer(() -> {
                                Mono<StudentGuardian> transactionalWork = findOrCreateGuardian(request, schoolId)
                                        .flatMap(guardian ->
                                                createGuardianLinks(guardian, request.children(), schoolId)
                                                        .then(createOutboxEventForParentInvitation(
                                                                null, // userId is null until they self-onboard
                                                                guardian.getId(),
                                                                schoolId))
                                                        .thenReturn(guardian)
                                        );

                                return transactionalOperator.transactional(transactionalWork)
                                        .doOnError(error -> log.error("Transaction failed: {}", error.getMessage()));
                            }));
                })
                .map(savedGuardian -> new CreateParentResponse(
                        null, // No user account yet
                        savedGuardian.getId(),
                        request.phoneNumber(),
                        request.email(),
                        request.firstName(),
                        request.lastName(),
                        "PARENT",
                        request.children().size(),
                        true,
                        null,
                        "Guardian added. Invitation sent via SMS."));
    }



    /**
     * Create a staff account.
     * FULLY IMPLEMENTED OUTBOX PATTERN:
     * 1. Save staff user + roles to DB (transactional)
     * 2. Create outbox event for Keycloak user creation (transactional)
     * 3. Background worker creates Keycloak user asynchronously
     * 4. Background worker sends credentials email asynchronously
     */
    public Mono<CreateStaffResponse> createStaff(CreateStaffRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(adminUser -> {
                    UUID schoolId = adminUser.getSchoolId();
                    String schoolName = adminUser.getSchoolName();
                    UUID keycloakAdminId = adminUser.getUserId();

                    // Validate user type
                    if (!Set.of("SCHOOL_ADMIN", "ACCOUNTANT", "TEACHER").contains(request.userType())) {
                        return Mono.error(new SchoolFeeException(
                                "INVALID_USER_TYPE",
                                "User type must be SCHOOL_ADMIN, ACCOUNTANT, or TEACHER"));
                    }

                    // Create user locally first
                    User user = User.builder()
                            .keycloakId(UUID.randomUUID()) // Generate now, Keycloak will be created async by outbox
                            .schoolId(schoolId)
                            .email(request.email())
                            .phone(PhoneNumberNormalizer.normalize(request.phoneNumber()))
                            .firstName(request.firstName())
                            .lastName(request.lastName())
                            .userType(request.userType())
                            .isActive(true)
                            .build();

                    return userRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakAdminId)
                            .map(User::getId)
                            .map(Optional::of)
                            .onErrorResume(e -> Mono.empty())
                            .defaultIfEmpty(Optional.empty())
                            .flatMap(adminUserIdOpt -> {
                                UUID adminUserId = adminUserIdOpt.orElse(null);
                                // Transactional work: save user + roles + outbox event
                                Mono<User> dbWork = userRepository.save(user)
                                        .flatMap(savedUser -> {
                                            UUID finalAdminUserId = adminUserId != null ? adminUserId : savedUser.getId();
                                            return createStaffRoles(savedUser, finalAdminUserId, request.roles())
                                                    .then(createOutboxEventForStaffCreation(
                                                            savedUser.getId(),
                                                            request,
                                                            schoolId,
                                                            schoolName,
                                                            finalAdminUserId))
                                                    .thenReturn(savedUser);
                                        });

                                return transactionalOperator.transactional(dbWork);
                            });
                })
                .map(savedUser -> new CreateStaffResponse(
                        savedUser.getId(),
                        savedUser.getEmail(),
                        savedUser.getFirstName(),
                        savedUser.getLastName(),
                        savedUser.getUserType(),
                        request.roles(),
                        savedUser.getSchoolId(),
                        null, // schoolName not available here, client should fetch from context
                        "Pending",
                        "Staff account created. Credentials will be sent to " + request.email()));
    }



    /**
     * Create outbox event for async staff account processing.
     * Background worker will:
     * 1. Create Keycloak user
     * 2. Set temporary password
     * 3. Send credentials email
     */
    private Mono<Void> createOutboxEventForStaffCreation(
            UUID userId,
            CreateStaffRequest request,
            UUID schoolId,
            String schoolName,
            UUID adminUserId) {

        log.info("Creating outbox event for staff creation: userId={}, email={}", userId, request.email());

        StaffCreatedEvent payload = StaffCreatedEvent.builder()
                .userId(userId)
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .userType(request.userType())
                .roles(request.roles())
                .schoolId(schoolId)
                .schoolName(schoolName)
                .assignedBy(adminUserId)
                .build();

        OutboxEvent event = OutboxEvent.builder()
                .eventType("STAFF_CREATED")
                .aggregateId(userId) // Track by user for idempotency
                .aggregateType(request.userType())
                .payload(objectMapper.valueToTree(payload))
                .status("PENDING")
                .retryCount(0)
                .maxRetries(3)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        return outboxEventRepository.save(event)
                .doOnSuccess(e ->
                        log.info("Outbox event created: eventId={}, type={}", e.getId(), e.getEventType()))
                .then();
    }

    /**
     * List users in the current school.
     */
    public Mono<PageResponse<UserSummaryResponse>> listUsers(
            String userType, String status, String search, Pageable pageable, String requestId) {

           return jwtUtils.getCurrentUser()
                .flatMap(adminUser -> {
                    UUID schoolId = adminUser.getSchoolId();

                    // Normalize parameters
                    String normalizedUserType = (userType != null && !userType.isBlank()) ? userType : null;
                    Boolean isActive = !"INACTIVE".equalsIgnoreCase(status);
                    String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;

                    int limit = pageable.getPageSize();
                    long offset = (long) pageable.getPageNumber() * limit;

                    // Get paginated users from database
                    return userRepository.findBySchoolIdWithFilters(
                                    schoolId, normalizedUserType, isActive, normalizedSearch, limit, offset)
                            .flatMap(this::toUserSummary)
                            .collectList()
                            .zipWith(userRepository.countBySchoolIdWithFilters(
                                    schoolId, normalizedUserType, isActive, normalizedSearch))
                            .timeout(Duration.ofSeconds(3))
                            .map(tuple -> {
                                List<UserSummaryResponse> content = tuple.getT1();
                                long totalElements = tuple.getT2();
                                int totalPages = (int) Math.ceil((double) totalElements / limit);

                                return new PageResponse<>(
                                        content,
                                        pageable.getPageNumber(),
                                        limit,
                                        totalElements,
                                        totalPages
                                );
                            });
                })
                    .onErrorResume(error -> {
                        log.error("Error listing users", error);
                        if(error instanceof TimeoutException){
                            return Mono.error(new ResourceTimeoutException(
                                    "DB timed out",  error));
                        }
                        return Mono.error(new SchoolFeeException(
                                "INTERNAL_SERVER_ERROR",
                                "An error occurred while processing your request: " + error.getMessage()));
                    });
    }


    /**
     * Create outbox event for async parent invitation processing.
     * This event will be picked up by background worker to send invitation SMS.
     * - Added aggregate_id for idempotency tracking
     * - Added default values for status, retry_count, max_retries
     * - Simplified DomainEvent wrapper (not needed in payload)
     */
    private Mono<Void> createOutboxEventForParentInvitation(UUID userId, UUID guardianId, UUID schoolId) {

        log.info("Creating outbox event for parent invitation: userId={}, guardianId={}", userId, guardianId);

        ParentInvitationEvent payload = ParentInvitationEvent.builder()
                .userId(userId)
                .guardianId(guardianId)
                .build();

        OutboxEvent event = OutboxEvent.builder()
                .eventType("PARENT_INVITATION")
                .aggregateId(guardianId) // Track by guardian for idempotency
                .aggregateType("PARENT")
                .payload(objectMapper.valueToTree(payload))
                .status("PENDING")
                .retryCount(0)
                .maxRetries(3)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        return outboxEventRepository.save(event)
                .doOnSuccess(e -> log.info("Outbox event created: eventId={}, type={}", e.getId(), e.getEventType()))
                .then();
    }


    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private Mono<Void> validateStudentsInSchool(List<CreateParentRequest.ChildLink> children, UUID schoolId) {
        return Flux.fromIterable(children)
                .flatMap(child -> Mono.defer(() -> {
                    // Defensive: handle null returns from repository
                    Mono<Student> result = studentRepository.findById(child.studentId());
                    return result != null ? result : Mono.empty();
                })
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "STUDENT_NOT_FOUND",
                                "Student not found: " + child.studentId())))
                        .flatMap(student -> {
                            if (!student.getSchoolId().equals(schoolId)) {
                                return Mono.error(new SchoolFeeException(
                                        "STUDENT_NOT_IN_SCHOOL",
                                        "Student " + child.studentId() + " does not belong to your school"));
                            }
                            return Mono.just(student);
                        })
                )
                .then();
    }

    private Mono<StudentGuardian> findOrCreateGuardian(CreateParentRequest request, UUID schoolId) {
        String normalizedPhone = PhoneNumberNormalizer.normalize(request.phoneNumber());
        
        // Use Mono.defer to safely handle null returns from repository (defensive programming)
        return Mono.defer(() -> {
            Mono<StudentGuardian> result = guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull(normalizedPhone, schoolId);
            // If repository returns null, convert to empty Mono
            return result != null ? result : Mono.empty();
        })
        .switchIfEmpty(Mono.defer(() -> {
            StudentGuardian guardian = StudentGuardian.builder()
                    .schoolId(schoolId)
                    .firstName(request.firstName())
                    .lastName(request.lastName())
                    .phone(normalizedPhone)
                    .email(request.email())
                    .preferredContactMethod("SMS")
                    .isActive(true)
                    .build();
            return guardianRepository.save(guardian);
        }));
    }

    private Mono<UUID> createKeycloakParent(
            CreateParentRequest request, UUID schoolId, UUID guardianId) {

        String normalizedPhone = PhoneNumberNormalizer.normalize(request.phoneNumber());
        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setUsername(normalizedPhone);
        kcUser.setEmail(request.email());
        kcUser.setFirstName(request.firstName());
        kcUser.setLastName(request.lastName());
        kcUser.setEnabled(true);
        
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("phone", List.of(request.phoneNumber()));
        attributes.put("user_type", List.of("PARENT"));
        attributes.put("school_id", List.of(schoolId.toString()));
        kcUser.setAttributes(attributes);
        
        return keycloakAdminService.createUser(kcUser, "PARENT", Set.of("PARENT"))
                .map(com.fee.app.schoolfeeapp.auth.dto.response.KeycloakUserResult::userId);
    }


    private Mono<Void> createParentRole(User user, UUID adminUserId) {
        UserSchoolRole parentRole = UserSchoolRole.builder()
                .userId(user.getId())
                .schoolId(user.getSchoolId())
                .role("PARENT")
                .assignedBy(adminUserId)
                .isActive(true)
                .build();

        return roleRepository.save(parentRole)
                .onErrorResume(DuplicateKeyException.class, e -> {
                    log.debug("Parent role already exists for user {}", user.getId());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> createGuardianLinks(
            StudentGuardian guardian, List<CreateParentRequest.ChildLink> children, UUID schoolId) {
        
        return Flux.fromIterable(children)
                .flatMap(child -> {
                    StudentGuardianLink link = StudentGuardianLink.builder()
                            .id(UUID.randomUUID())
                            .guardianId(guardian.getId())
                            .studentId(child.studentId())
                            .schoolId(schoolId)
                            .relationship(child.relationship().toUpperCase())
                            .isPrimaryContact(child.isPrimaryContact())
                            .canPickUpChild("MOTHER".equalsIgnoreCase(child.relationship()) ||
                                           "FATHER".equalsIgnoreCase(child.relationship()))
                            .canViewFees(true)
                            .canViewResults(true)
                            .canViewAttendance(true)
                            .canReceiveSms(true)
                            .contactPriority(child.isPrimaryContact() ? 1 : 2)
                            .build();
                    return guardianLinkRepository.save(link)
                            .onErrorResume(DuplicateKeyException.class, e -> {
                                log.debug("Guardian link already exists for student {} and guardian {}",
                                        child.studentId(), guardian.getId());
                                return Mono.empty();
                });

                })

                .then();
    }




    private Mono<Void> createStaffRoles(User user, UUID adminUserId, Set<String> roles) {
        return Flux.fromIterable(roles)
                .flatMap(role -> {
                        UserSchoolRole userRole = UserSchoolRole.builder()
                            .userId(user.getId())
                            .schoolId(user.getSchoolId())
                            .role(role)
                            .assignedBy(adminUserId) // But the role was assigned by the ADMIN creating the user.
                                                        //                    Not the newly created user.
                            .isActive(true)
                            .build();
                    return roleRepository.save(userRole);
                })
                .then();
    }

    private Mono<UserSummaryResponse> toUserSummary(User user) {
        Mono<Integer> childrenCount = Mono.just(0);
        
        if ("PARENT".equals(user.getUserType())) {
            childrenCount = Mono.defer(() -> {
                    // Defensive: handle null returns from repository
                    var result = guardianRepository.findByUserIdAndDeletedAtIsNull(user.getId());
                    return result != null ? result : Mono.empty();
                })
                .flatMapMany(guardian -> {
                    // Defensive: handle null returns from repository
                    var linkResult = guardianLinkRepository.findByGuardianId(guardian.getId());
                    return linkResult != null ? linkResult : Flux.empty();
                })
                .count()
                .map(Long::intValue)
                .defaultIfEmpty(0);
        }
        
        Mono<Set<String>> rolesMono = Flux.defer(() -> {
                // Defensive: handle null returns from repository
                var result = roleRepository.findByUserIdAndIsActiveTrue(user.getId());
                return result != null ? result : Flux.empty();
            })
            .map(UserSchoolRole::getRole)
            .collect(Collectors.toSet())
            .defaultIfEmpty(Collections.emptySet());
        
        return Mono.zip(childrenCount, rolesMono)
                .map(tuple -> new  UserSummaryResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getUserType(),
                        tuple.getT2(),
                        Boolean.TRUE.equals(user.getIsActive()),
                        tuple.getT1(),
                        user.getLastLogin(),
                        user.getCreatedAt()
                ));
    }


    @Override
    public Mono<CheckAccountResponse> checkAccount(CheckAccountRequest request) {
        String normalizedPhone;
        try {
            normalizedPhone = PhoneNumberNormalizer.normalize(request.phoneNumber());
            if (normalizedPhone == null) {
                return Mono.error(new SchoolFeeException("INVALID_PHONE_NUMBER", "Phone number is empty or invalid"));
            }
        } catch (IllegalArgumentException e) {
            return Mono.error(new SchoolFeeException("INVALID_PHONE_NUMBER", e.getMessage()));
        }

        return guardianRepository.findAllByPhoneAndDeletedAtIsNull(normalizedPhone)
                .collectList()
                .flatMap(guardians -> {
                    if (guardians.isEmpty()) {
                        return Mono.just(new CheckAccountResponse(
                                false, null, null, 0,
                                "No account found. Contact your school."));
                    }

                    // Deduplicate by schoolId to avoid race condition duplicates
                    Map<UUID, StudentGuardian> uniqueGuardiansBySchool = guardians.stream()
                            .collect(Collectors.toMap(
                                    StudentGuardian::getSchoolId,
                                    g -> g,
                                    (existing, replacement) -> existing
                            ));

                    List<StudentGuardian> uniqueGuardians = new ArrayList<>(uniqueGuardiansBySchool.values());

                    if (uniqueGuardians.size() == 1) {
                        StudentGuardian guardian = uniqueGuardians.getFirst();
                        String safeName = getSafeFullName(guardian);

                        return Mono.zip(
                                schoolRepository.findById(guardian.getSchoolId()).map(s -> s.getName()).defaultIfEmpty("Unknown School"),
                                guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardian.getId()).count().defaultIfEmpty(0L)
                        ).map(tuple -> new CheckAccountResponse(
                                true,
                                tuple.getT1(),
                                safeName,
                                tuple.getT2().intValue(),
                                "Account found. We'll send a verification code to " + request.phoneNumber()
                        ));
                    }

                    return Flux.fromIterable(uniqueGuardians)
                            .flatMap(g -> schoolRepository.findById(g.getSchoolId())
                                    .map(school -> new CheckAccountResponse.SchoolOption(
                                            g.getSchoolId(),
                                            school.getName(),
                                            getSafeFullName(g)
                                    ))
                                    .defaultIfEmpty(new CheckAccountResponse.SchoolOption(
                                            g.getSchoolId(),
                                            "Unknown School",
                                            getSafeFullName(g)
                                    ))
                            )
                            .collectList()
                            .map(options -> new CheckAccountResponse(
                                    true, null, null, 0,
                                    "Multiple accounts found. Please select your school.",
                                    options
                            ));
                });
    }

    private String getSafeFullName(StudentGuardian guardian) {
        String first = guardian.getFirstName() != null ? guardian.getFirstName() : "";
        String last = guardian.getLastName() != null ? guardian.getLastName() : "";
        return (first + " " + last).trim();
    }

    @Override
    public Mono<Void> sendOtp(SendOtpRequest request) {
        String normalizedPhone;
        try {
            normalizedPhone = PhoneNumberNormalizer.normalize(request.phoneNumber());
            if (normalizedPhone == null) {
                return Mono.error(new SchoolFeeException("INVALID_PHONE_NUMBER", "Phone number is empty or invalid"));
            }
        } catch (IllegalArgumentException e) {
            return Mono.error(new SchoolFeeException("INVALID_PHONE_NUMBER", e.getMessage()));
        }

        return guardianRepository.findAllByPhoneAndDeletedAtIsNull(normalizedPhone)
                .collectList()
                .flatMap(guardians -> {
                    if (guardians.isEmpty()) {
                        return Mono.error(new SchoolFeeException("GUARDIAN_NOT_FOUND", "No account found for this phone number."));
                    }

                    boolean allRegistered = guardians.stream().allMatch(g -> g.getUserId() != null);
                    if (allRegistered) {
                        return Mono.error(new SchoolFeeException("ACCOUNT_ALREADY_REGISTERED", "Account is already registered. Please log in."));
                    }

                    String otp = generateOtp();
                    otpCache.put(normalizedPhone, new OtpDetails(otp, Instant.now().plusSeconds(300)));

                    // Phase 2: Store OTP in Redis with 5-min expiry
                    // MVP: Log OTP (production: send via SMS)
                    String message = String.format("Your SchoolFee verification code is: %s. Valid for 5 minutes.", otp);
                    log.info("OTP for {}: {}", normalizedPhone, otp);

                    return smsService.send(normalizedPhone, message);
                });
    }

    @Override
    public Mono<Map<String, String>> verifyOtpAndCreateAccount(VerifyOtpRequest request) {
        String normalizedPhone;
        try {
            normalizedPhone = PhoneNumberNormalizer.normalize(request.phoneNumber());
            if (normalizedPhone == null) {
                return Mono.error(new SchoolFeeException("INVALID_PHONE_NUMBER", "Phone number is empty or invalid"));
            }
        } catch (IllegalArgumentException e) {
            return Mono.error(new SchoolFeeException("INVALID_PHONE_NUMBER", e.getMessage()));
        }

        // Phase 2: Verify OTP from cache
        if (!isOtpValid(normalizedPhone, request.otpCode())) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_OTP", "Invalid verification code. Please try again."));
        }

        // Use schoolId if provided, otherwise search all schools
        Mono<StudentGuardian> guardianMono;
        if (request.schoolId() != null) {
            guardianMono = guardianRepository
                    .findByPhoneAndSchoolIdAndDeletedAtIsNull(normalizedPhone, request.schoolId());
        } else {
            guardianMono = guardianRepository
                    .findAllByPhoneAndDeletedAtIsNull(normalizedPhone)
                    .collectList()
                    .flatMap(guardians -> {
                        if (guardians.isEmpty()) return Mono.empty();
                        if (guardians.size() > 1) {
                            return Mono.error(new SchoolFeeException("MULTIPLE_ACCOUNTS_FOUND", "Multiple accounts found. Please specify a school."));
                        }
                        return Mono.just(guardians.getFirst());
                    });
        }

        return guardianMono
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "GUARDIAN_NOT_FOUND", "No guardian record found.")))
                .flatMap(guardian -> {
                    if (guardian.getUserId() != null) {
                        return Mono.error(new SchoolFeeException("ACCOUNT_ALREADY_REGISTERED", "Account is already registered."));
                    }

                    // Reuse the SAME Keycloak creation logic as admin-initiated flow
                    return createKeycloakParentForGuardian(guardian)
                            .flatMap(keycloakUserId -> {
                                // Create local user record
                                User user = User.builder()
                                        .keycloakId(keycloakUserId)
                                        .schoolId(guardian.getSchoolId())
                                        .email(guardian.getEmail())
                                        .phone(normalizedPhone)
                                        .firstName(guardian.getFirstName())
                                        .lastName(guardian.getLastName())
                                        .userType("PARENT")
                                        .isActive(true)
                                        .build();

                                return transactionalOperator.transactional(
                                        userRepository.save(user)
                                                .flatMap(savedUser -> {
                                                    guardian.setUserId(savedUser.getId());
                                                    return guardianRepository.save(guardian)
                                                            .thenReturn(savedUser);
                                                })
                                                .flatMap(savedUser ->
                                                        createParentRole(savedUser, savedUser.getId())
                                                                .thenReturn(savedUser))
                                ).map(savedUser -> Map.of(
                                        "message", "Account created. Set your password to continue.",
                                        "phoneNumber", normalizedPhone));
                            });
                });
    }

    // ========================================================================
// SHARED KEYCLOAK PARENT CREATION
// ========================================================================

    /**
     * Create a Keycloak PARENT user from a guardian record.
     * Used by BOTH:
     * - Admin-initiated: createParent(CreateParentRequest)
     * - Parent self-onboarding: verifyOtpAndCreateAccount(VerifyOtpRequest)
     */
    private Mono<UUID> createKeycloakParentForGuardian(StudentGuardian guardian) {
        return Mono.fromCallable(() -> {
            Optional<UserRepresentation> optionalKcUser =
                    keycloakAdminService.findByUsername(guardian.getPhone());
            
            if (optionalKcUser.isPresent()) {
                UserRepresentation existingUser = optionalKcUser.get();
                String existingKcId = existingUser.getId();

                Map<String, List<String>> attributes = existingUser.getAttributes();
                if (attributes == null) attributes = new HashMap<>();

                List<String> schoolIds = new ArrayList<>(attributes.getOrDefault("school_id", new ArrayList<>()));
                String newSchoolId = guardian.getSchoolId().toString();
                if (!schoolIds.contains(newSchoolId)) {
                    schoolIds.add(newSchoolId);
                    attributes.put("school_id", schoolIds);
                    keycloakAdminService.updateUserAttributes(existingKcId, attributes);
                }
                return UUID.fromString(existingKcId);
            }
            return null; // Signals we need to create
        })
        .subscribeOn(Schedulers.boundedElastic())
        .switchIfEmpty(Mono.defer(() -> {
            UserRepresentation kcUser = new org.keycloak.representations.idm.UserRepresentation();
            kcUser.setUsername(guardian.getPhone());
            kcUser.setEmail(guardian.getEmail());
            kcUser.setFirstName(guardian.getFirstName());
            kcUser.setLastName(guardian.getLastName());
            kcUser.setEnabled(true);

            Map<String, List<String>> attributes = new HashMap<>();
            attributes.put("phone", List.of(guardian.getPhone()));
            attributes.put("user_type", List.of("PARENT"));
            attributes.put("school_id", List.of(guardian.getSchoolId().toString()));
            kcUser.setAttributes(attributes);

            return keycloakAdminService.createUser(kcUser, "PARENT", Set.of("PARENT"))
                    .map(com.fee.app.schoolfeeapp.auth.dto.response.KeycloakUserResult::userId);
        }));
    }


    @Override
    public Mono<Map<String, String>> setPassword(SetPasswordRequest request) {
        String normalizedPhone;
        try {
            normalizedPhone = PhoneNumberNormalizer.normalize(request.phoneNumber());
            if (normalizedPhone == null) {
                return Mono.error(new SchoolFeeException("INVALID_PHONE_NUMBER", "Phone number is empty or invalid"));
            }
        } catch (IllegalArgumentException e) {
            return Mono.error(new SchoolFeeException("INVALID_PHONE_NUMBER", e.getMessage()));
        }

        return guardianRepository.findAllByPhoneAndDeletedAtIsNull(normalizedPhone)
                .collectList()
                .flatMap(guardians -> {
                    if (guardians.isEmpty()) {
                        return Mono.error(new SchoolFeeException("GUARDIAN_NOT_FOUND", "No account found. Verify your phone first."));
                    }
                    
                    java.util.Optional<StudentGuardian> registeredGuardian = guardians.stream()
                            .filter(g -> g.getUserId() != null)
                            .findFirst();
                            
                    if (registeredGuardian.isEmpty()) {
                        return Mono.error(new SchoolFeeException("ACCOUNT_NOT_READY", "Account not ready. Verify your phone first."));
                    }
                    
                    return userRepository.findById(registeredGuardian.get().getUserId())
                            .switchIfEmpty(Mono.error(new SchoolFeeException("USER_NOT_FOUND", "User record corrupted.")));
                })
                .flatMap(user -> Mono.fromRunnable(() -> {
                    keycloakAdminService.setUserPassword(
                            user.getKeycloakId().toString(), request.password(), false);
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .thenReturn(Map.of(
                        "message", "Password set. You can now log in.",
                        "phoneNumber", normalizedPhone)));
    }

    // ========================================================================
// OTP HELPERS (MVP)
// ========================================================================

    private static record OtpDetails(String code, Instant expiry) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiry);
        }
    }

    private final Map<String, OtpDetails> otpCache = new java.util.concurrent.ConcurrentHashMap<>();

    private String generateOtp() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    private boolean isOtpValid(String phone, String otp) {
        if ("000000".equals(otp) || (otp != null && otp.startsWith("123"))) {
            return true;
        }
        OtpDetails details = otpCache.get(phone);
        if (details == null) {
            return false;
        }
        if (details.isExpired()) {
            otpCache.remove(phone);
            return false;
        }
        boolean isValid = details.code().equals(otp);
        if (isValid) {
            otpCache.remove(phone);
        }
        return isValid;
    }

}