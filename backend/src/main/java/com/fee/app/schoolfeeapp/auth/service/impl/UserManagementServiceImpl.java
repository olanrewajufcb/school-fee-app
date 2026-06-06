package com.fee.app.schoolfeeapp.auth.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.domain.UserSchoolRole;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateParentRequest;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.CreateParentResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.CreateStaffResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.UserGuardianResult;
import com.fee.app.schoolfeeapp.auth.dto.response.UserSummaryResponse;
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

    /**
     * Create a parent account.
     * IMPROVED ARCHITECTURE (Outbox Pattern):
     * - All DB operations in single transaction
     * - Keycloak user creation moved OUTSIDE transaction (non-transactional resource)
     * - SMS invitation via outbox event (async, retryable)
     *
     * Flow:
     * 1. Validate all student IDs belong to the current school
     * 2. Create or find guardian record
     * 3. Create Keycloak user with PARENT role (outside transaction)
     * 4. Create user record in auth.users (within transaction)
     * 5. Create PARENT role in auth.user_school_roles (within transaction)
     * 6. LINK guardian to user via guardian.user_id (within transaction)
     * 7. Create guardian-student links (within transaction)
     * 8. Create outbox event for async SMS processing (within transaction)
     * 9. Return success immediately
     *
     * COMPENSATING ACTION:
     * - If DB transaction fails, create KEYCLOAK_CLEANUP outbox event
     * - Background worker will delete orphaned Keycloak user
     */
    public Mono<CreateParentResponse> createParent(CreateParentRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(adminUser -> {
                    UUID schoolId = adminUser.getSchoolId();

                    return validateStudentsInSchool(request.children(), schoolId)
                            .then(findOrCreateGuardian(request, schoolId))
                            .flatMap(guardian ->
                                    createKeycloakParent(request, schoolId, guardian.getId())
                                            .flatMap(keycloakUserId -> {
                                                // DB work in transaction
                                                Mono<UserGuardianResult> transactionalWork =
                                                        createLocalUserAndRoles(keycloakUserId, request, schoolId, guardian, adminUser)
                                                                .flatMap(result ->
                                                                        linkGuardianToUser(guardian.getId(), result.user().getId())
                                                                                .then(createGuardianLinks(guardian, request.children(), schoolId))
                                                                                .then(createOutboxEventForParentInvitation(
                                                                                        result.user().getId(),
                                                                                        guardian.getId(),
                                                                                        schoolId))
                                                                                .thenReturn(result)
                                                                );

                                                return transactionalOperator.transactional(transactionalWork)
                                                        .doOnError(error -> log.error("Transaction failed: {}", error.getMessage()))

                                                        .onErrorResume(error -> {
                                                            // Compensating action: schedule Keycloak cleanup
                                                            log.error("Entering onErrorResume for cleanup");

                                                            log.error("DB transaction failed for parent creation. " +
                                                                    "Scheduling Keycloak user {} for cleanup", keycloakUserId, error);

                                                            return createOutboxEventForKeycloakCleanup(keycloakUserId)
                                                                    .doOnSuccess(v -> log.info("Cleanup event created"))

                                                                    .then(Mono.error(error));
                                                        });
                                            })
                            );
                })
                .map(savedParent -> new CreateParentResponse(
                        savedParent.user().getId(),
                        savedParent.guardian().getId(),
                        request.phoneNumber(),
                        request.email(),
                        request.firstName(),
                        request.lastName(),
                        "PARENT",
                        request.children().size(),
                        true,
                        "Pending",
                        "Parent account created. Invitation will be sent shortly."));
    }

    /**
     * Link guardian record to user account.
     * This ensures proper foreign key relationship for queries like findByUserId().
     */
    private Mono<StudentGuardian> linkGuardianToUser(UUID guardianId, UUID userId) {
        return guardianRepository.updateUserId(guardianId, userId)
                .doOnSuccess(linkedGuardian ->
                        log.info("Linked guardian {} to user {}", guardianId, userId));
    }

    /**
     * Create local user and PARENT role within transaction.
     * NO BLOCKING CALLS - fully reactive.
     */
    private Mono<UserGuardianResult> createLocalUserAndRoles(
            UUID keycloakUserId,
            CreateParentRequest request,
            UUID schoolId,
            StudentGuardian guardian,

            SchoolFeeUser adminUser) {

                    User user = User.builder()
                            .keycloakId(keycloakUserId)
                            .schoolId(schoolId)
                            .email(request.email())
                            .phone(PhoneNumberNormalizer.normalize(request.phoneNumber()))
                            .firstName(request.firstName())
                            .lastName(request.lastName())
                            .userType("PARENT")
                            .isActive(true)
                            .build();

                    return userRepository.save(user)
                            .flatMap(savedUser ->
                                    createParentRole(savedUser, adminUser.getUserId())
                                            .thenReturn(savedUser)
                            )
                            .map(savedUser -> new UserGuardianResult(savedUser, guardian));

    }


    /**
     * Create outbox event for Keycloak cleanup when DB transaction fails.
     * This prevents orphaned Keycloak users.
     */
    private Mono<Void> createOutboxEventForKeycloakCleanup(UUID keycloakUserId) {
        log.warn("Creating Keycloak cleanup event for orphaned user: keycloakId={}", keycloakUserId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("keycloakUserId", keycloakUserId.toString());
        payload.put("reason", "DB_TRANSACTION_FAILED");
        payload.put("timestamp", Instant.now().toString());

        OutboxEvent event = OutboxEvent.builder()
                .eventType("KEYCLOAK_CLEANUP")
                .aggregateId(keycloakUserId)
                .aggregateType("USER")
                .payload(objectMapper.valueToTree(payload))
                .status("PENDING")
                .retryCount(0)
                .maxRetries(3)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        return outboxEventRepository.save(event)
                .doOnSuccess(e -> log.info("Keycloak cleanup event created: eventId={}, keycloakId={}",
                        e.getId(), keycloakUserId))
                .doOnError(e -> log.error("Failed to create Keycloak cleanup event. " +
                        "Manual cleanup required for keycloakId={}", keycloakUserId, e))
                .then();
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
                    UUID adminUserId = adminUser.getUserId();

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

                    // Transactional work: save user + roles + outbox event
                    Mono<User> dbWork = userRepository.save(user)
                            .flatMap(savedUser ->
                                    createStaffRoles(savedUser, adminUserId, request.roles())
                                            .then(createOutboxEventForStaffCreation(
                                                    savedUser.getId(),
                                                    request,
                                                    schoolId,
                                                    schoolName,
                                                    adminUserId))
                                            .thenReturn(savedUser)
                            );

                    return transactionalOperator.transactional(dbWork);
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
                       if(error instanceof TimeoutException){
                           return Mono.error(new ResourceTimeoutException(
                                   "DB timed out",  error));
                       }
                       return Mono.error(new SchoolFeeException(
                               "INTERNAL_SERVER_ERROR",
                               "An error occurred while processing your request"));
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
        
        return keycloakAdminService.createUser(kcUser, "PARENT", Set.of("PARENT"));
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
                        user.getIsActive(),
                        tuple.getT1(),
                        user.getLastLogin(),
                        user.getCreatedAt()
                ));
    }
}