package com.fee.app.schoolfeeapp.auth.service.impl;


import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.service.IdentityProviderService;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

import com.fee.app.schoolfeeapp.auth.dto.response.KeycloakUserResult;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class KeycloakAdminServiceImpl implements IdentityProviderService {

    private final Keycloak keycloak;
    private final String realm;

    private static final String DEFAULT_TEMP_PASSWORD = "SchoolFee123!";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    @Autowired
    public KeycloakAdminServiceImpl(
            @Value("${keycloak.auth-server-url}") String serverUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.admin.username}") String adminUsername,
            @Value("${keycloak.admin.password}") String adminPassword,
            @Value("${keycloak.admin.client-id}") String adminClientId) {

        this.realm = realm;

        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .clientId(adminClientId)
                .username(adminUsername)
                .password(adminPassword)
                .grantType(OAuth2Constants.PASSWORD)
                .build();

        log.info("Keycloak admin client initialized for realm: {}", realm);
    }

    /**
     * Package-private constructor for testing purposes.
     * Allows injection of mocked Keycloak instance.
     */
    KeycloakAdminServiceImpl(Keycloak keycloak, String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
        log.debug("KeycloakAdminServiceImpl initialized with mocked Keycloak for realm: {}", realm);
    }

    // ========================================================================
    // USER CREATION
    // ========================================================================

    /**
     * Create a user in Keycloak with specified roles.
     *
     * @param userRepresentation The user to create
     * @param userType USER_TYPE attribute value
     * @param realmRoles Roles to assign
     * @return The Keycloak user ID
     */
    public Mono<KeycloakUserResult> createUser(
            UserRepresentation userRepresentation,
            String userType,
            Set<String> realmRoles) {

        return Mono.fromCallable(() -> {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            // Set required attributes
            Map<String, List<String>> attributes = userRepresentation.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            attributes.put("user_type", List.of(userType));
            userRepresentation.setAttributes(attributes);

            // Enable the user immediately
            userRepresentation.setEnabled(true);

            // Create user
            try (Response response = usersResource.create(userRepresentation)) {
                if (response.getStatus() != 201) {
                    String errorBody = response.readEntity(String.class);
                    log.error("Failed to create Keycloak user. Status: {}, Body: {}",
                            response.getStatus(), errorBody);
                    throw new SchoolFeeException(
                            "KEYCLOAK_USER_CREATION_FAILED",
                            "Failed to create user in Keycloak: " + errorBody);
                }

                // Extract user ID from Location header
                String userId = extractCreatedId(response);
                log.info("Created Keycloak user: {}", userId);

                // Set temporary password
                String tempPassword = generateTempPassword();
                setUserPassword(userId, tempPassword, true);
                log.debug("Temporary password set for user: {}", userId);

                // Assign realm roles
                assignRealmRoles(userId, realmRoles);
                log.debug("Roles {} assigned to user: {}", realmRoles, userId);

                return new KeycloakUserResult(UUID.fromString(userId), tempPassword);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Create a staff user (admin, accountant, or teacher).
     */
    public Mono<KeycloakUserResult> createStaffUser(
            CreateStaffRequest request,
            UUID schoolId,
            String schoolName) {

        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.email());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);

        // Set attributes
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("phone", List.of(request.phoneNumber()));
        attributes.put("user_type", List.of(request.userType()));
        attributes.put("school_id", List.of(schoolId.toString()));
        attributes.put("school_name", List.of(schoolName != null ? schoolName : ""));
        user.setAttributes(attributes);

        return createUser(user, request.userType(), request.roles());
    }

    // ========================================================================
    // ROLE MANAGEMENT
    // ========================================================================

    /**
     * Assign realm roles to a user.
     */
    public void assignRealmRoles(String userId, Set<String> roleNames) {
        RealmResource realmResource = keycloak.realm(realm);
        UserResource userResource = realmResource.users().get(userId);

        List<RoleRepresentation> roles = roleNames.stream()
                .map(roleName -> {
                    try {
                        return realmResource.roles().get(roleName).toRepresentation();
                    } catch (Exception e) {
                        log.warn("Role not found in Keycloak: {}. Skipping.", roleName);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (!roles.isEmpty()) {
            userResource.roles().realmLevel().add(roles);
        }
    }

    /**
     * Remove realm roles from a user.
     */
    public void removeRealmRoles(String userId, Set<String> roleNames) {
        RealmResource realmResource = keycloak.realm(realm);
        UserResource userResource = realmResource.users().get(userId);

        List<RoleRepresentation> roles = roleNames.stream()
                .map(roleName -> realmResource.roles().get(roleName).toRepresentation())
                .collect(toList());

        userResource.roles().realmLevel().remove(roles);
    }

    /**
     * Get all realm roles assigned to a user.
     */
    public Set<String> getUserRoles(String userId) {
        RealmResource realmResource = keycloak.realm(realm);
        UserResource userResource = realmResource.users().get(userId);

        return userResource.roles().realmLevel().listAll().stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toSet());
    }

    // ========================================================================
    // PASSWORD MANAGEMENT
    // ========================================================================

    /**
     * Set a user's password.
     *
     * @param userId Keycloak user ID
     * @param password New password
     * @param temporary If true, user must change password on first login
     */
    public void setUserPassword(String userId, String password, boolean temporary) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setTemporary(temporary);
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);

        keycloak.realm(realm).users().get(userId).resetPassword(credential);
        log.debug("Password set for user: {}. Temporary: {}", userId, temporary);
    }

    /**
     * Send password reset email to user.
     */
    public void sendPasswordResetEmail(String userId) {
        keycloak.realm(realm).users().get(userId)
                .executeActionsEmail(List.of("UPDATE_PASSWORD"));
        log.debug("Password reset email sent to user: {}", userId);
    }

    // ========================================================================
    // USER MANAGEMENT
    // ========================================================================

    /**
     * Find a user by username.
     */
    public Optional<UserRepresentation> findByUsername(String username) {
        List<UserRepresentation> users = keycloak.realm(realm).users()
                .searchByUsername(username, true);

        if (users.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(users.get(0));
    }

    /**
     * Find a user by email.
     */
    public Optional<UserRepresentation> findByEmail(String email) {
        List<UserRepresentation> users = keycloak.realm(realm).users()
                .searchByEmail(email, true);

        if (users.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(users.get(0));
    }

    /**
     * Enable or disable a user.
     */
    public void setUserEnabled(String userId, boolean enabled) {
        UserRepresentation user = new UserRepresentation();
        user.setEnabled(enabled);
        keycloak.realm(realm).users().get(userId).update(user);
        log.debug("User {}: {}", enabled ? "enabled" : "disabled", userId);
    }

    /**
     * Delete a user (hard delete — use with caution).
     */
    public void deleteUser(String userId) {
        keycloak.realm(realm).users().get(userId).remove();
        log.info("User deleted from Keycloak: {}", userId);
    }

    /**
     * Update user attributes.
     */
    public void updateUserAttributes(String userId, Map<String, List<String>> attributes) {
        UserResource userResource = keycloak.realm(realm).users().get(userId);
        UserRepresentation user = userResource.toRepresentation();

        Map<String, List<String>> existingAttrs = user.getAttributes();
        if (existingAttrs == null) {
            existingAttrs = new HashMap<>();
        }
        existingAttrs.putAll(attributes);
        user.setAttributes(existingAttrs);

        userResource.update(user);
        log.debug("Attributes updated for user: {}", userId);
    }

    // ========================================================================
    // GROUP MANAGEMENT (Phase 2)
    // ========================================================================

    /**
     * Add user to a group.
     * Phase 2: Use groups for organizational hierarchy.
     */
    public void addUserToGroup(String userId, String groupId) {
        keycloak.realm(realm).users().get(userId).joinGroup(groupId);
    }

    /**
     * Remove user from a group.
     */
    public void removeUserFromGroup(String userId, String groupId) {
        keycloak.realm(realm).users().get(userId).leaveGroup(groupId);
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    /**
     * Extract created user ID from the Location header in the response.
     */
    private String extractCreatedId(Response response) {
        String location = response.getHeaderString("Location");
        if (location == null) {
            throw new SchoolFeeException(
                    "KEYCLOAK_ERROR",
                    "No Location header in Keycloak response");
        }
        return location.substring(location.lastIndexOf('/') + 1);
    }

    /**
     * Generate a secure temporary password.
     */
    private String generateTempPassword() {
        String upperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerChars = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specialChars = "!@#$%";
        String allChars = upperChars + lowerChars + digits + specialChars;

        Random random = new SecureRandom();
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);

        // Ensure at least one of each required character type
        password.append(upperChars.charAt(random.nextInt(upperChars.length())));
        password.append(lowerChars.charAt(random.nextInt(lowerChars.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(specialChars.charAt(random.nextInt(specialChars.length())));

        // Fill remaining characters
        for (int i = 4; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // Shuffle
        List<Character> chars = password.chars()
                .mapToObj(c -> (char) c)
                .collect(toList());
        Collections.shuffle(chars, random);

        return chars.stream()
                .map(String::valueOf)
                .collect(Collectors.joining());
    }
}