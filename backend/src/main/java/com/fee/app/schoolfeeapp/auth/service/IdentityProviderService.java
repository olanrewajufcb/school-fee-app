package com.fee.app.schoolfeeapp.auth.service;

import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.KeycloakUserResult;
import org.keycloak.representations.idm.UserRepresentation;
import reactor.core.publisher.Mono;

import java.util.*;

public interface IdentityProviderService {
    Mono<KeycloakUserResult> createUser(UserRepresentation user, String userType, Set<String> roles);
    Mono<KeycloakUserResult> createStaffUser(CreateStaffRequest request, UUID schoolId, String schoolName);
    void assignRealmRoles(String userId, Set<String> roleNames);
    void removeRealmRoles(String userId, Set<String> roleNames);
    Set<String> getUserRoles(String userId);
    void setUserPassword(String userId, String password, boolean temporary);
    void sendPasswordResetEmail(String userId);
    Optional<UserRepresentation> findByUsername(String username);
    Optional<UserRepresentation> findByEmail(String email);
    void setUserEnabled(String userId, boolean enabled);
    void deleteUser(String userId);
    void updateUserAttributes(String userId, Map<String, List<String>> attributes);
    void removeUserFromGroup(String userId, String groupId);

    void addUserToGroup(String userId, String groupId);

}