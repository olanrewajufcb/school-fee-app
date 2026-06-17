package com.fee.app.schoolfeeapp.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {


        @Bean
        public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
            return http
                    // Disable CSRF for REST API
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)

                    // CORS configuration

                    // Stateless session (JWT-based)
                    .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

                    // Route-based authorization
                    .authorizeExchange(exchanges -> exchanges
                            // Allow OPTIONS requests for CORS preflight
                            .pathMatchers(HttpMethod.OPTIONS).permitAll()
                            
                            // Public endpoints
                            .pathMatchers("/api/v1/auth/login").permitAll()
                            .pathMatchers("/api/v1/auth/keycloak-config").permitAll()
                            .pathMatchers("/api/v1/auth/check-account").permitAll()
                            .pathMatchers("/api/v1/auth/send-otp").permitAll()
                            .pathMatchers("/api/v1/auth/verify-otp").permitAll()
                            .pathMatchers("/api/v1/auth/set-password").permitAll()
                            .pathMatchers("/api/public/**").permitAll()
                            .pathMatchers("/api/health").permitAll()
                            .pathMatchers("/actuator/health").permitAll()
                            // 1. Whitelist Swagger UI and OpenAPI docs
                            .pathMatchers(
                                    "/swagger-ui.html",
                                    "/swagger-ui/**",
                                    "/v3/api-docs/**",
                                    "/webjars/**"
                            ).permitAll()

                            // Webhook endpoints (no auth, IP whitelisted by gateway)
                            .pathMatchers("/api/v1/webhooks/**").permitAll()

                            // Auth endpoints require authentication
                            .pathMatchers("/api/v1/auth/me").authenticated()

                            // Super admin endpoints
                            .pathMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")

                            // School management
                            .pathMatchers(HttpMethod.POST, "/api/v1/schools").hasRole("SUPER_ADMIN")
                            .pathMatchers(HttpMethod.GET, "/api/v1/schools").hasRole("SUPER_ADMIN")
                            .pathMatchers(HttpMethod.PATCH, "/api/v1/schools/*/deactivate").hasRole("SUPER_ADMIN")

                            // All other endpoints require authentication
                            .anyExchange().authenticated()
                    )
                    // OAuth2 Resource Server with JWT
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt
                                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                            )
                    )
                    .build();
        }

        /**
         * Custom JWT converter that extracts roles from Keycloak token
         */
        @Bean
        public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
            JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
            jwtConverter.setJwtGrantedAuthoritiesConverter(new SchoolFeeJwtAuthoritiesConverter());

            return new ReactiveJwtAuthenticationConverterAdapter(jwtConverter);
        }

        /**
         * Converts both Keycloak realm roles and the app-specific user_type claim
         * into Spring Security ROLE_* authorities.
         */
        static class SchoolFeeJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
            @Override
            public Collection<GrantedAuthority> convert(Jwt jwt) {
                Set<GrantedAuthority> authorities = new HashSet<>();

                Object realmAccessClaim = jwt.getClaim("realm_access");
                if (realmAccessClaim instanceof Map<?, ?> realmAccess) {
                    Object rolesClaim = realmAccess.get("roles");
                    if (rolesClaim instanceof Collection<?> roles) {
                        roles.stream()
                                .filter(String.class::isInstance)
                                .map(String.class::cast)
                                .filter(role -> !role.isBlank())
                                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                                .map(SimpleGrantedAuthority::new)
                                .forEach(authorities::add);
                    }
                }

                String userType = jwt.getClaimAsString("user_type");
                if (userType != null && !userType.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + userType));
                }

                return authorities;
            }
        }

        /**
         * CORS configuration for frontend
         */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(List.of(
                    "http://localhost:5173",
                    "http://localhost:3000",
                    "https://schoolfee.app"
            ));
            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("*"));
            configuration.setAllowCredentials(true);
            configuration.setMaxAge(3600L);

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }

    @Bean
    public CorsWebFilter corsWebFilter() {
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "https://schoolfee.app"
        ));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }

  /**
   * Stateless security context — no server-side session
   */
  static class NoOpServerSecurityContextRepository implements ServerSecurityContextRepository {
    private static final NoOpServerSecurityContextRepository INSTANCE =
        new NoOpServerSecurityContextRepository();

    public static NoOpServerSecurityContextRepository getInstance() {
      return INSTANCE;
    }

    @Override
    public Mono<Void> save(
        ServerWebExchange exchange,
        SecurityContext context) {
      return Mono.empty();
    }

    @Override
    public Mono<SecurityContext> load(
        ServerWebExchange exchange) {
      return Mono.empty();
    }
}

}
