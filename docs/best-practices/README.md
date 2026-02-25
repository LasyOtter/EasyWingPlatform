# Best Practices

## 📚 Best Practices Overview

This guide covers best practices for developing, deploying, and maintaining applications built on EasyWing Platform.

## 🏗️ Architecture Best Practices

### Service Design

1. **Single Responsibility**: Each microservice should have one clear purpose
2. **Loose Coupling**: Services should communicate via well-defined APIs
3. **High Cohesion**: Related functionality should be grouped together

### API Design

```java
// Good: RESTful resource naming
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) { ... }

// Good: Consistent response format
@GetMapping("/users")
public PageResult<User> listUsers(Pageable pageable) { ... }

// Good: RFC 9457 error responses
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException e) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problem.setTitle("Resource Not Found");
    problem.setDetail(e.getMessage());
    problem.setType(URI.create("/errors/not-found"));
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
}
```

## 🔐 Security Best Practices

### Authentication & Authorization

```java
// Good: Use method-level security
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(Long id) { ... }

// Good: Use OAuth2 with JWT
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
}
```

### Input Validation

```java
// Good: Use Bean Validation
public record CreateUserRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password
) {}
```

### Secret Management

```yaml
# Good: Use environment variables
spring:
  datasource:
    password: ${DB_PASSWORD}

# Bad: Hardcoded secrets
spring:
  datasource:
    password: "secret123"  # NEVER do this!
```

## 📊 Observability Best Practices

### Logging

```java
// Good: Structured logging with context
@Slf4j
@Service
public class UserService {
    
    public User createUser(CreateUserRequest request) {
        log.info("Creating user with email: {}", request.email());
        try {
            User user = userRepository.save(request);
            log.info("User created successfully: userId={}", user.getId());
            return user;
        } catch (Exception e) {
            log.error("Failed to create user: email={}", request.email(), e);
            throw e;
        }
    }
}
```

### Tracing

```java
// Good: Add custom spans
@WithSpan("userService.createUser")
public User createUser(CreateUserRequest request) {
    // Business logic
}

// Good: Add span attributes
@SpanAttribute("user.email")
public String getEmail() {
    return email;
}
```

### Metrics

```java
// Good: Use Micrometer for custom metrics
@Service
public class OrderService {
    
    private final Counter orderCounter;
    private final Timer orderTimer;
    
    public OrderService(MeterRegistry registry) {
        this.orderCounter = Counter.builder("orders.created")
            .description("Number of orders created")
            .register(registry);
        this.orderTimer = Timer.builder("orders.processing.time")
            .description("Time to process orders")
            .register(registry);
    }
    
    @Timed(value = "orders.create", description = "Time to create order")
    public Order createOrder(OrderRequest request) {
        orderCounter.increment();
        // Business logic
    }
}
```

## 🔄 Resilience Best Practices

### Circuit Breaker

```java
// Good: Use Resilience4j circuit breaker
@Service
public class PaymentService {
    
    private final CircuitBreaker circuitBreaker;
    
    public PaymentService(CircuitBreakerRegistry registry) {
        this.circuitBreaker = registry.circuitBreaker("payment");
    }
    
    public PaymentResult processPayment(PaymentRequest request) {
        return circuitBreaker.executeSupplier(() -> 
            paymentClient.processPayment(request)
        );
    }
}
```

### Retry with Backoff

```java
// Good: Configure retry with exponential backoff
@Retry(name = "userService", fallbackMethod = "getUserFallback")
public User getUser(Long id) {
    return userClient.getUser(id);
}

public User getUserFallback(Long id, Exception e) {
    log.warn("Fallback triggered for getUser: id={}", id, e);
    return User.empty();
}
```

### Rate Limiting

```java
// Good: Configure rate limiting
@RateLimiter(name = "api", fallbackMethod = "rateLimitFallback")
public ApiResponse processRequest(Request request) {
    // Business logic
}
```

## 💾 Data Access Best Practices

### Transaction Management

```java
// Good: Use @Transactional appropriately
@Service
public class OrderService {
    
    @Transactional
    public Order createOrder(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        orderItems.forEach(item -> orderItemRepository.save(item));
        return order;
    }
    
    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id).orElseThrow();
    }
}
```

### Caching

```java
// Good: Use multi-level caching
@Service
public class UserService {
    
    @Cacheable(value = "users", key = "#id", unless = "#result == null")
    public User getUser(Long id) {
        return userRepository.findById(id).orElseThrow();
    }
    
    @CacheEvict(value = "users", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }
}
```

## 🧪 Testing Best Practices

### Unit Tests

```java
// Good: Use descriptive test names
@DisplayName("UserService")
class UserServiceTest {
    
    @Nested
    @DisplayName("createUser")
    class CreateUser {
        
        @Test
        @DisplayName("should create user when email is valid")
        void shouldCreateUserWhenEmailIsValid() {
            // Given
            CreateUserRequest request = new CreateUserRequest("test@example.com", "password");
            
            // When
            User result = userService.createUser(request);
            
            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("test@example.com");
        }
        
        @Test
        @DisplayName("should throw exception when email is duplicate")
        void shouldThrowExceptionWhenEmailIsDuplicate() {
            // Test implementation
        }
    }
}
```

### Integration Tests

```java
// Good: Use Testcontainers
@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
    
    @Test
    void shouldFindUserByEmail() {
        // Test implementation
    }
}
```

## 🚀 Performance Best Practices

### Database Optimization

```java
// Good: Use pagination
@GetMapping("/users")
public Page<User> listUsers(Pageable pageable) {
    return userRepository.findAll(pageable);
}

// Good: Use fetch join for N+1 problem
@EntityGraph(attributePaths = {"orders", "orders.items"})
public User findUserWithOrders(Long id);
```

### Caching Strategy

```yaml
# Good: Multi-level cache configuration
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m
easywing:
  cache:
    redis:
      enabled: true
      ttl: 1h
```

### Connection Pool

```yaml
# Good: Configure connection pool
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000
```

## 📦 Build Best Practices

### Maven Configuration

```xml
<!-- Good: Use dependency management -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.easywing.platform</groupId>
            <artifactId>easywing-platform-bom</artifactId>
            <version>${project.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Good: Specify versions only in BOM -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <!-- No version needed, managed by BOM -->
</dependency>
```

### CI/CD Pipeline

```yaml
# Good: Use proper CI stages
stages:
  - build
  - test
  - quality
  - deploy

# Good: Fail fast
jobs:
  build:
    # Fast feedback for compilation errors
  test:
    needs: build
    # Run tests in parallel
  quality:
    needs: test
    # Code quality checks
  deploy:
    needs: quality
    # Only deploy after all checks pass
```

## 📝 Documentation Best Practices

### API Documentation

```java
// Good: Use OpenAPI annotations
@Tag(name = "User", description = "User management APIs")
public class UserController {
    
    @Operation(summary = "Get user by ID", description = "Returns a single user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) { ... }
}
```

### Code Comments

```java
// Good: Document why, not what
public User createUser(CreateUserRequest request) {
    // Validate email uniqueness to prevent duplicate accounts
    if (userRepository.existsByEmail(request.email())) {
        throw new DuplicateEmailException(request.email());
    }
    
    // Hash password before storing for security compliance
    String hashedPassword = passwordEncoder.encode(request.password());
    
    return userRepository.save(new User(request.email(), hashedPassword));
}
```

---

*EasyWing Platform Best Practices*