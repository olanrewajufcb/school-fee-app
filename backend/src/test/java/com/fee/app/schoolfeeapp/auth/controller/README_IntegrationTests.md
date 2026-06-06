# Integration Tests for AuthController

## Overview

This file contains **integration tests** for the `AuthController` endpoints that test the complete request/response pipeline with real JWT authentication and Spring Security context.

## Key Differences from Unit Tests

### Unit Tests (`AuthControllerTest.java`)
- ✅ Isolated controller testing
- ✅ All dependencies mocked (`@Mock`, `@InjectMocks`)
- ✅ No Spring context loading
- ✅ Fast execution (~milliseconds)
- ✅ Test controller logic only
- ❌ Don't test security configuration
- ❌ Don't test actual HTTP pipeline

### Integration Tests (`AuthControllerIntegrationTest.java`)
- ✅ Full Spring Boot context loading (`@SpringBootTest`)
- ✅ Real JWT token creation and validation
- ✅ Actual Spring Security filter chain
- ✅ WebTestClient for real HTTP requests
- ✅ Test complete request/response pipeline
- ✅ Verify security configuration works correctly
- ❌ Slower execution (~seconds)

## Test Structure

### 1. **GET /me - With Real JWT Authentication** (8 tests)
Tests the authenticated endpoint with realistic JWT tokens:
- ✅ Valid JWT token returns user profile
- ✅ Missing JWT token returns 401 Unauthorized
- ✅ Invalid JWT token returns 401 Unauthorized
- ✅ School admin with multiple roles
- ✅ Parent with children
- ✅ Teacher without children
- ✅ All required fields present in response

### 2. **GET /keycloak-config - Public Endpoint** (4 tests)
Tests the public endpoint that doesn't require authentication:
- ✅ Returns config without authentication
- ✅ Correct Keycloak endpoint URLs
- ✅ Allows access with authentication (optional)
- ✅ Consistent responses on multiple requests

### 3. **Security and Error Handling** (5 tests)
Tests edge cases and error scenarios:
- ✅ Expired JWT token handling
- ✅ Malformed Authorization header
- ✅ Anonymous user access denial
- ✅ Concurrent request handling
- ✅ CORS configuration respect

### 4. **Response Format Validation** (3 tests)
Validates JSON structure and format:
- ✅ Proper JSON structure for `/me`
- ✅ Proper JSON structure for `/keycloak-config`
- ✅ ISO 8601 format for timestamps

## Running the Tests

### Run all integration tests:
```bash
cd /Users/sulaiman/IdeaProjects/school-fee-app/backend
mvn test -Dtest=AuthControllerIntegrationTest
```

### Run specific test category:
```bash
# Only authenticated endpoint tests
mvn test -Dtest=AuthControllerIntegrationTest$GetCurrentUserWithRealJwtTests

# Only public endpoint tests
mvn test -Dtest=AuthControllerIntegrationTest$GetKeycloakConfigPublicEndpointTests

# Only security tests
mvn test -Dtest=AuthControllerIntegrationTest$SecurityAndErrorHandlingTests
```

### Run with coverage:
```bash
mvn test jacoco:report -Dtest=AuthControllerIntegrationTest
```

## Technical Details

### Annotations Used
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` - Loads full Spring context with random port
- `@AutoConfigureWebTestClient` - Auto-configures WebTestClient bean
- `@MockitoBean` - Mocks service dependencies (replaces deprecated `@MockBean`)

### JWT Token Creation
Creates realistic JWT tokens with Keycloak-standard claims:
```java
Map<String, Object> claims = new HashMap<>();
claims.put("sub", KEYCLOAK_ID.toString());           // User ID
claims.put("preferred_username", "testuser");
claims.put("email", "test@school.edu");
claims.put("given_name", "Test");
claims.put("family_name", "User");
claims.put("phone_number", "+2348012345678");
claims.put("school_id", SCHOOL_ID.toString());
claims.put("user_type", "PARENT");

// Realm access roles (Keycloak standard claim)
Map<String, Object> realmAccess = new HashMap<>();
realmAccess.put("roles", List.of("PARENT", "ACCOUNTANT"));
claims.put("realm_access", realmAccess);

validJwt = Jwt.withTokenValue("mock-token")
        .headers(h -> h.putAll(headers))
        .claims(c -> c.putAll(claims))
        .build();
```

### WebTestClient Usage
Makes real HTTP requests to test the complete pipeline:
```java
webTestClient.get()
        .uri("/api/v1/auth/me")
        .cookie("TEST_SESSION", "valid-session")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody(ApiResponse.class)
        .consumeWith(response -> {
            assertThat(response.getResponseBody()).isNotNull();
        });
```

## What These Tests Verify

### ✅ Controller Layer
- Endpoint mappings work correctly
- Request routing is correct
- Response serialization to JSON

### ✅ Security Layer
- JWT token validation works
- Unauthorized requests are rejected (401)
- Role-based access control functions
- CORS configuration is applied

### ✅ Serialization Layer
- Java objects → JSON conversion
- JSON structure matches API contract
- Timestamp formatting (ISO 8601)

### ❌ What These Tests DON'T Cover
- Service layer business logic (mocked)
- Database operations (not tested)
- External API calls (Keycloak not started)
- Network issues or timeouts

## Best Practices Followed

1. **Nested Test Classes** - Organized by functionality for clarity
2. **Descriptive Display Names** - Clear test purpose documentation
3. **Arrange-Act-Assert Pattern** - Structured test code
4. **JSON Path Assertions** - Validate response structure
5. **Realistic JWT Tokens** - Mimic production Keycloak tokens
6. **Edge Case Coverage** - Test error scenarios and boundaries
7. **No Hard-Coded Dependencies** - Use constants and helpers

## When to Add More Tests

Add integration tests when you:
- ✅ Change security configuration
- ✅ Modify endpoint URLs or methods
- ✅ Update response DTO structures
- ✅ Add new authentication requirements
- ✅ Change CORS or other web configurations
- ✅ Need to verify complete request pipeline

Keep unit tests when you:
- ✅ Testing business logic in isolation
- ✅ Need fast feedback during development
- ✅ Testing edge cases that don't need full context
- ✅ Want high code coverage quickly

## Performance Notes

- **Unit Tests**: ~10-50ms per test (very fast)
- **Integration Tests**: ~1-5 seconds per test (slower but comprehensive)
- **Total Suite**: Run unit tests first for quick feedback, integration tests before deployment

## Future Enhancements

Potential improvements:
1. [ ] Add Testcontainers for real Keycloak integration
2. [ ] Add database integration tests with Testcontainers
3. [ ] Add performance benchmarks
4. [ ] Add chaos engineering tests (timeouts, failures)
5. [ ] Add contract tests for API versioning

## References

- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [WebTestClient Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-client-builder-test)
- [Spring Security Testing](https://docs.spring.io/spring-security/reference/servlet/test/index.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
