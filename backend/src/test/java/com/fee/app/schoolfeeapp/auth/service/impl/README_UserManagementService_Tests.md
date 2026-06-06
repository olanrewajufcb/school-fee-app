# UserManagementService Integration Tests

## Overview

This directory contains integration tests for `UserManagementServiceImpl` that verify:
- Service layer orchestration with real Spring context
- Transactional behavior with reactive streams
- Error handling and rollback scenarios
- Outbox pattern implementation
- Guardian-student linking logic

## Test Files

### 1. `UserManagementServiceImplTest.java` (Unit Tests)
- **Type**: Pure unit tests with Mockito mocks
- **Scope**: Tests service logic in isolation
- **Speed**: Very fast (~milliseconds per test)
- **Dependencies**: All mocked (repositories, Keycloak, JWT)

### 2. `UserManagementServiceIntegrationTest.java` (Integration Tests)
- **Type**: Service-layer integration tests with Spring context
- **Scope**: Tests reactive stream composition and transactional operators
- **Speed**: Moderate (~seconds per test)
- **Dependencies**: Real Spring beans, mocked external services (Keycloak)

## Running the Tests

### Run all tests
```bash
cd /Users/sulaiman/IdeaProjects/school-fee-app/backend
mvn test -Dtest=UserManagementServiceImplTest
mvn test -Dtest=UserManagementServiceIntegrationTest
```

### Run specific test category
```bash
# Unit tests only
mvn test -Dtest=UserManagementServiceImplTest

# Integration tests only  
mvn test -Dtest=UserManagementServiceIntegrationTest

# Specific nested test class
mvn test -Dtest=UserManagementServiceIntegrationTest\$CreateParentIntegrationTests
```

### Run with coverage
```bash
mvn test jacoco:report -Dtest=UserManagementServiceImpl*
```

## Test Coverage

### Create Parent Tests
✅ Student validation (school ownership)  
✅ Guardian creation/reuse  
✅ Keycloak user creation (mocked)  
✅ Local user creation  
✅ Role assignment  
✅ Guardian-student linking  
✅ Outbox event creation  
✅ Cross-school validation failures  
✅ Student not found failures  

### Create Staff Tests
✅ Teacher account creation  
✅ Admin account creation  
✅ Accountant account creation  
✅ Multiple role assignment  
✅ Invalid user type rejection  
✅ Outbox event for async Keycloak creation  

### List Users Tests (Future)
🔄 Pagination  
🔄 Filtering by user type  
🔄 Filtering by status  
🔄 Search by name/email  

### Transactional Tests (Future)
🔄 Rollback on failure  
rollback on Keycloak cleanup  
🔄 Compensating actions  

## Current Test Architecture

### What's Tested with Real Spring Context
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserManagementServiceIntegrationTest {
    
    @Autowired          // ✅ Real Spring bean
    private UserManagementServiceImpl userManagementService;
    
    @MockitoBean        // ⚠️ Mocked external dependency
    private KeycloakAdminServiceImpl keycloakAdminService;
    
    @MockitoBean        // ⚠️ Mocked security context
    private JwtUtils jwtUtils;
}
```

**Real Beans:**
- UserRepository
- StudentGuardianRepository
- StudentGuardianLinkRepository
- StudentRepository
- OutboxEventRepository
- TransactionalOperator
- ObjectMapper

**Mocked Dependencies:**
- KeycloakAdminService (external API)
- JwtUtils (security context)

## Future: Full Database Integration Tests with Testcontainers

To add real database testing with PostgreSQL containers:

### 1. Dependencies (Already Added to build.gradle)
```gradle
testImplementation 'org.testcontainers:testcontainers:1.20.4'
testImplementation 'org.testcontainers:postgresql:1.20.4'
testImplementation 'org.testcontainers:r2dbc:1.20.4'
```

### 2. Create Testcontainers-Based Test
```java
@SpringBootTest
@Testcontainers
class UserManagementServiceDatabaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("school_fee_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String r2dbcUrl = String.format("r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName());
        
        registry.add("spring.r2dbc.url", () -> r2dbcUrl);
        registry.add("spring.r2dbc.username", () -> postgres.getUsername());
        registry.add("spring.r2dbc.password", () -> postgres.getPassword());
    }
    
    // Tests would verify actual database state
}
```

### 3. Benefits of Database Integration Tests
- ✅ Verify actual SQL queries work correctly
- ✅ Test database constraints and indexes
- ✅ Verify transaction isolation levels
- ✅ Test foreign key relationships
- ✅ Validate schema migrations (Flyway)

### 4. Trade-offs
| Aspect | Service Integration (Current) | Database Integration (Future) |
|--------|------------------------------|-------------------------------|
| Speed | Fast (~seconds) | Slower (~minutes) |
| Complexity | Low | Higher (container management) |
| Coverage | Service logic | Database + service logic |
| Maintenance | Easy | More complex |
| CI/CD Impact | Minimal | Requires Docker |

## Test Data Management

### Current Approach
```java
@AfterEach
void tearDown() {
    // Clean up test data after each test
    // Note: With mocked repositories, no actual DB cleanup needed
}
```

### Future with Testcontainers
```java
@AfterEach
void tearDown() {
    databaseClient.sql("DELETE FROM auth.user_school_roles").execute().block();
    databaseClient.sql("DELETE FROM auth.student_guardian_links").execute().block();
    databaseClient.sql("DELETE FROM auth.student_guardians").execute().block();
    databaseClient.sql("DELETE FROM auth.users").execute().block();
    databaseClient.sql("DELETE FROM school.students").execute().block();
    databaseClient.sql("DELETE FROM outbox_events").execute().block();
}
```

## Common Test Patterns

### Arrange-Act-Assert with Reactive Streams
```java
@Test
void shouldCreateParentSuccessfully() {
    // Arrange
    UUID studentId = UUID.randomUUID();
    Student student = Student.builder()
            .id(studentId)
            .schoolId(SCHOOL_ID)
            .build();
    
    studentRepository.save(student).block();
    
    when(keycloakAdminService.createUser(any(), any(), any()))
            .thenReturn(Mono.just(UUID.randomUUID()));
    
    // Act & Assert
    StepVerifier.create(userManagementService.createParent(request))
            .assertNext(response -> {
                assertThat(response.firstName()).isEqualTo("John");
            })
            .verifyComplete();
}
```

### Error Handling Verification
```java
@Test
void shouldFailWhenStudentNotFound() {
    StepVerifier.create(userManagementService.createParent(request))
            .expectErrorMatches(error -> 
                error instanceof RuntimeException &&
                error.getMessage().contains("STUDENT_NOT_FOUND"))
            .verify();
}
```

## Best Practices

1. **Use Nested Test Classes** - Organize by functionality
2. **Descriptive Test Names** - Use "Should [behavior] when [condition]"
3. **Clean Test Data** - Each test should be independent
4. **Mock External Services** - Don't depend on Keycloak/network
5. **Verify Side Effects** - Check outbox events, linked records
6. **Use StepVerifier** - Proper reactive stream testing
7. **Test Failure Scenarios** - Not just happy paths

## Troubleshooting

### Test Fails with "No qualifying bean"
**Solution**: Add `@SpringBootTest` to load Spring context

### Test Fails with Timeout
**Solution**: Increase timeout or check for blocking calls in reactive chain

### Mock Not Working
**Solution**: Ensure `@MockitoBean` is used instead of deprecated `@MockBean`

### Reactive Stream Doesn't Execute
**Solution**: Use `.block()` for setup, `StepVerifier` for assertions

## References

- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Reactor Test](https://projectreactor.io/docs/test/release/reference/)
- [Testcontainers](https://www.testcontainers.org/)
- [R2DBC Testing](https://r2dbc.io/spec/1.0.0.RELEASE/reference/html/#testing)

## Next Steps

1. ✅ Service-layer integration tests (current)
2. 🔄 Add database integration tests with Testcontainers
3. 🔄 Add performance tests for large datasets
4. 🔄 Add contract tests for Keycloak API
5. 🔄 Add E2E tests with real HTTP requests
