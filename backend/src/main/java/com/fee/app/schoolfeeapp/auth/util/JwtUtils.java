package com.fee.app.schoolfeeapp.auth.util;

import com.fee.app.schoolfeeapp.common.exceptions.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

@Component
@Slf4j
public class JwtUtils {
    
    /**
     * Extract school ID from JWT token
     */
    public UUID getSchoolId(Jwt jwt) {
        String schoolId = jwt.getClaimAsString("school_id");
        return schoolId != null && !schoolId.equals("*") ? UUID.fromString(schoolId) : null;
    }
    
    /**
     * Extract user type from JWT token
     */
    public String getUserType(Jwt jwt) {
        return jwt.getClaimAsString("user_type");
    }
    
    /**
     * Extract roles from JWT token
     */
    @SuppressWarnings("unchecked")
    public Set<String> getRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Collections.emptySet();
        }
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles != null ? new HashSet<>(roles) : Collections.emptySet();
    }
    

    /**
     * Get current authenticated user context reactively
     */
    public Mono<SchoolFeeUser> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .switchIfEmpty(Mono.error(
                        new UnauthorizedException("No security context found")))
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .switchIfEmpty(Mono.error(
                        new UnauthorizedException("User not authenticated")))
                .map(this::extractUserFromAuthentication);
    }
    
    private SchoolFeeUser extractUserFromAuthentication(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            
            return SchoolFeeUser.builder()
                    .userId(UUID.fromString(jwt.getSubject()))
                    .username(jwt.getClaimAsString("preferred_username"))
                    .email(jwt.getClaimAsString("email"))
                    .firstName(jwt.getClaimAsString("given_name"))
                    .lastName(jwt.getClaimAsString("family_name"))
                    .phoneNumber(jwt.getClaimAsString("phone_number"))
                    .schoolId(getSchoolId(jwt))
                    .userType(getUserType(jwt))
                    .roles(getRoles(jwt))
                    .build();
        }
        
        throw new IllegalStateException("Unexpected authentication type: " + 
                authentication.getClass().getName());
    }
    
    /**
     * Check if user has a specific role
     */
    public boolean hasRole(Jwt jwt, String role) {
        return getRoles(jwt).contains(role);
    }
    
    /**
     * Check if user belongs to a specific school
     */
    public boolean belongsToSchool(Jwt jwt, UUID schoolId) {
        UUID tokenSchoolId = getSchoolId(jwt);
        return tokenSchoolId != null && tokenSchoolId.equals(schoolId);
    }
}