package com.fee.app.schoolfeeapp.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final SecurityConfig.SchoolFeeJwtAuthoritiesConverter converter =
            new SecurityConfig.SchoolFeeJwtAuthoritiesConverter();

    @Test
    @DisplayName("Should grant Spring role from user type claim")
    void shouldGrantSpringRoleFromUserTypeClaim() {
        Jwt jwt = jwt(Map.of("user_type", "SUPER_ADMIN"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_SUPER_ADMIN");
    }

    @Test
    @DisplayName("Should preserve Keycloak realm roles and user type role")
    void shouldPreserveRealmRolesAndUserTypeRole() {
        Jwt jwt = jwt(Map.of(
                "user_type", "SCHOOL_ADMIN",
                "realm_access", Map.of("roles", java.util.List.of("ACCOUNTANT"))));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_SCHOOL_ADMIN", "ROLE_ACCOUNTANT");
    }

    @Test
    @DisplayName("Configured reactive JWT converter should expose user type as authority")
    void configuredReactiveJwtConverterShouldExposeUserTypeAsAuthority() {
        Jwt jwt = jwt(Map.of("user_type", "SUPER_ADMIN"));

        AbstractAuthenticationToken authentication =
                new SecurityConfig().jwtAuthenticationConverter().convert(jwt).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_SUPER_ADMIN");
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("381a2bc3-1508-4d44-bf1c-0e79ebf85ce3")
                .issuedAt(Instant.parse("2026-06-12T11:28:36Z"))
                .expiresAt(Instant.parse("2026-06-12T11:58:36Z"))
                .claims(jwtClaims -> jwtClaims.putAll(claims))
                .build();
    }
}
