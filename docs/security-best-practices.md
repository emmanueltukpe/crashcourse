# Security Best Practices - Production-Grade Payment System

A comprehensive guide to securing a payment system for 100 million users, covering environment variables, secrets management, application security, and deployment practices.

## Table of Contents

1. [Security Fundamentals](#security-fundamentals)
2. [Environment Variables & Configuration](#environment-variables--configuration)
3. [Secrets Management](#secrets-management)
4. [Authentication & Authorization](#authentication--authorization)
5. [Application Security](#application-security)
6. [Deployment Security](#deployment-security)
7. [Data Security](#data-security)
8. [Monitoring & Incident Response](#monitoring--incident-response)

---

## Security Fundamentals

### The CIA Triad

**Confidentiality**: Only authorized users access data  
**Integrity**: Data cannot be tampered with  
**Availability**: System is accessible when needed

### Defense in Depth

**Never rely on a single security control.**

```text
Layer 1: Network (Firewall, VPC)
Layer 2: Load Balancer (DDoS protection, TLS termination)
Layer 3: Application (Authentication, input validation)
Layer 4: Database (Encryption, access control)
Layer 5: Monitoring (Alerts, audit logs)
```

---

## Environment Variables & Configuration

### The Golden Rules

**1. Never commit secrets to version control**

```bash
# ❌ BAD: Hardcoded in code
spring.datasource.password=MySecretPassword123

# ✅ GOOD: Environment variable
spring.datasource.password=${DB_PASSWORD}
```

**2. Separate configs per environment**

```
config/
├── application.yml           # Defaults (no secrets)
├── application-dev.yml       # Development
├── application-staging.yml   # Staging
├── application-prod.yml      # Production
```

**3. Use .env files for local development**

```bash
# .env (gitignored!)
DB_PASSWORD=local_password
JWT_SECRET=local_jwt_secret_key_for_development_only
REDIS_PASSWORD=local_redis_password
```

```bash
# .gitignore
.env
.env.local
.env.*.local
```

### Environment Variable Validation

**Fail fast if required variables are missing:**

```java
@Configuration
public class EnvironmentValidator {

    @PostConstruct
    public void validateEnvironment() {
        List<String> required = List.of(
            "DB_PASSWORD",
            "JWT_SECRET",
            "REDIS_PASSWORD",
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY"
        );

        List<String> missing = required.stream()
            .filter(key -> System.getenv(key) == null)
            .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing required environment variables: " + missing
            );
        }

        // Validate JWT secret length
        String jwtSecret = System.getenv("JWT_SECRET");
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 32 characters"
            );
        }
    }
}
```

### Configuration Best Practices

```yaml
# application.yml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/payment_db}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD} # No default for secrets!
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:20}

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD}

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION_MS:3600000} # 1 hour default

aws:
  access-key-id: ${AWS_ACCESS_KEY_ID}
  secret-access-key: ${AWS_SECRET_ACCESS_KEY}
  region: ${AWS_REGION:us-east-1}
```

---

## Secrets Management

### AWS Secrets Manager

**Store secrets in dedicated service, not environment variables.**

#### Setup

```java
@Configuration
public class SecretsManagerConfig {

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }

    @Bean
    public Map<String, String> databaseCredentials(SecretsManagerClient client) {
        String secretName = "payment-system/database";

        GetSecretValueRequest request = GetSecretValueRequest.builder()
            .secretId(secretName)
            .build();

        GetSecretValueResponse response = client.getSecretValue(request);
        String secretString = response.secretString();

        // Parse JSON secret
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(secretString, Map.class);
    }
}
```

**Secret rotation:**

```java
@Component
public class SecretRotationHandler {

    @Autowired
    private DataSource dataSource;

    @Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
    public void rotateSecrets() {
        // 1. Fetch new credentials from Secrets Manager
        Map<String, String> newCreds = secretsManager.getCredentials();

        // 2. Update database with new credentials
        // (Implementation depends on your database)

        // 3. Update application datasource
        ((HikariDataSource) dataSource).getHikariConfigMXBean()
            .setPassword(newCreds.get("password"));

        logger.info("Database credentials rotated successfully");
    }
}
```

### HashiCorp Vault

```java
@Configuration
public class VaultConfig {

    @Bean
    public VaultTemplate vaultTemplate() {
        VaultEndpoint endpoint = VaultEndpoint.create("vault.example.com", 8200);
        endpoint.setScheme("https");

        TokenAuthentication authentication = new TokenAuthentication(
            System.getenv("VAULT_TOKEN")
        );

        return new VaultTemplate(endpoint, authentication);
    }
}

@Service
public class SecretService {

    @Autowired
    private VaultTemplate vaultTemplate;

    public String getDatabasePassword() {
        VaultResponse response = vaultTemplate
            .read("secret/payment-system/database");

        return (String) response.getData().get("password");
    }
}
```

### Kubernetes Secrets

```yaml
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: payment-system-secrets
type: Opaque
data:
  db-password: cGFzc3dvcmQxMjM= # base64 encoded
  jwt-secret: bXlqd3RzZWNyZXRrZXk=
```

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
spec:
  template:
    spec:
      containers:
        - name: payment-service
          image: payment-service:latest
          env:
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: payment-system-secrets
                  key: db-password
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: payment-system-secrets
                  key: jwt-secret
```

---

## Authentication & Authorization

### JWT Best Practices

```java
@Component
public class JwtTokenProvider {

    private final String SECRET_KEY;
    private final long EXPIRATION_MS = 3600000; // 1 hour

    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        // Validate secret key length
        if (secretKey.length() < 32) {
            throw new IllegalArgumentException(
                "JWT secret must be at least 32 characters"
            );
        }
        this.SECRET_KEY = secretKey;
    }

    public String createToken(User user) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
            .setSubject(user.getId().toString())
            .claim("username", user.getUsername())
            .claim("roles", user.getRoles())
            .setIssuedAt(now)
            .setExpiration(validity)
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
            .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid JWT token", e);
        }
    }
}
```

**JWT Security Checklist:**

- [ ] Secret key ≥ 32 characters (256 bits)
- [ ] Set expiration time (1-24 hours)
- [ ] Use HS256 or RS256 algorithm
- [ ] Validate signature on every request
- [ ] Don't store sensitive data in JWT
- [ ] Implement token refresh mechanism
- [ ] Have token revocation strategy

### OAuth 2.0 Integration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .oauth2Login()
                .authorizationEndpoint()
                    .baseUri("/oauth2/authorize")
                .and()
                .redirectionEndpoint()
                    .baseUri("/oauth2/callback/*")
                .and()
                .userInfoEndpoint()
                    .userService(customOAuth2UserService);
    }
}
```

### Multi-Factor Authentication (MFA)

```java
@Service
public class MfaService {

    @Autowired
    private TotpService totpService;

    public String generateMfaSecret(Long userId) {
        String secret = totpService.generateSecret();

        // Store secret encrypted
        userRepository.updateMfaSecret(userId, encrypt(secret));

        // Return QR code for user to scan
        return totpService.generateQRCode(secret, "PaymentApp:" + userId);
    }

    public boolean verifyMfaCode(Long userId, String code) {
        User user = userRepository.findById(userId).orElseThrow();
        String secret = decrypt(user.getMfaSecret());

        return totpService.verify(secret, code);
    }
}
```

---

## Application Security

### Input Validation

```java
@RestController
@Validated
public class PaymentController {

    @PostMapping("/api/v1/payments")
    public PaymentResponse createPayment(
        @Valid @RequestBody PaymentRequest request) {

        // Spring validates automatically with @Valid
        return paymentService.create(request);
    }
}

public class PaymentRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @DecimalMax(value = "1000000.00", message = "Amount too large")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^(USD|NGN|USDC)$", message = "Invalid currency")
    private String currency;

    @NotBlank(message = "Recipient is required")
    @Email(message = "Invalid email format")
    private String recipientEmail;
}
```

### SQL Injection Prevention

```java
// ❌ BAD: String concatenation (vulnerable!)
String query = "SELECT * FROM users WHERE username = '" + username + "'";

// ✅ GOOD: Parameterized query
@Query("SELECT u FROM User u WHERE u.username = :username")
User findByUsername(@Param("username") String username);

// ✅ GOOD: Named parameters
String jpql = "SELECT u FROM User u WHERE u.email = :email";
TypedQuery<User> query = em.createQuery(jpql, User.class);
query.setParameter("email", email);
```

### XSS (Cross-Site Scripting) Prevention

```java
@Configuration
public class SecurityHeadersConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request,
                                   HttpServletResponse response,
                                   Object handler) {
                // Content Security Policy
                response.setHeader("Content-Security-Policy",
                    "default-src 'self'; script-src 'self'; style-src 'self'");

                // XSS Protection
                response.setHeader("X-XSS-Protection", "1; mode=block");

                // Prevent clickjacking
                response.setHeader("X-Frame-Options", "DENY");

                // MIME type sniffing
                response.setHeader("X-Content-Type-Options", "nosniff");

                return true;
            }
        });
    }
}
```

### CORS Configuration

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(
                "https://app.example.com",  // Production
                "https://staging.example.com"  // Staging
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

### CSRF Protection

```java
@Configuration
@EnableWebSecurity
public class CsrfConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf()
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .and()
            .authorizeRequests()
                .antMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated();
    }
}
```

### Rate Limiting

```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private RedisTemplate<String, Integer> redisTemplate;

    private static final int MAX_REQUESTS_PER_MINUTE = 100;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws IOException {

        String clientId = getClientId(request); // IP or user ID
        String key = "rate_limit:" + clientId;

        Integer requests = redisTemplate.opsForValue().get(key);

        if (requests == null) {
            redisTemplate.opsForValue().set(key, 1, 1, TimeUnit.MINUTES);
        } else if (requests >= MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(429); // Too Many Requests
            response.getWriter().write("Rate limit exceeded");
            return;
        } else {
            redisTemplate.opsForValue().increment(key);
        }

        filterChain.doFilter(request, response);
    }
}
```

---

## Deployment Security

### HTTPS/TLS Everywhere

```yaml
# application-prod.yml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: ${SSL_KEYSTORE_PATH}
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    protocol: TLS
    enabled-protocols: TLSv1.3,TLSv1.2
```

**Certificate management:**

- Use Let's Encrypt for free SSL certificates
- Auto-renew certificates (certbot)
- Use AWS Certificate Manager (ACM) for AWS

### Container Security

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jre-alpine AS runtime

# Don't run as root!
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Read-only root filesystem
COPY --chown=appuser:appgroup target/app.jar /app/app.jar

WORKDIR /app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Security scanning:**

```bash
# Scan for vulnerabilities
docker scan payment-service:latest

# Use Trivy
trivy image payment-service:latest
```

### Kubernetes Security

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: payment-service
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    fsGroup: 2000

  containers:
    - name: app
      image: payment-service:latest
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities:
          drop:
            - ALL

      resources:
        limits:
          cpu: "1"
          memory: "512Mi"
        requests:
          cpu: "0.5"
          memory: "256Mi"
```

### Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: payment-service-network-policy
spec:
  podSelector:
    matchLabels:
      app: payment-service
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: gateway
      ports:
        - protocol: TCP
          port: 8080
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: postgres
      ports:
        - protocol: TCP
          port: 5432
```

---

## Data Security

### Encryption at Rest

**Database encryption:**

```yaml
# PostgreSQL with encryption
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payment_db?ssl=true&sslmode=require
```

```sql
-- Encrypt sensitive columns
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (email, ssn) VALUES (
  'user@example.com',
  PGP_SYM_ENCRYPT('123-45-6789', '${ENCRYPTION_KEY}')
);

SELECT
  email,
  PGP_SYM_DECRYPT(ssn::bytea, '${ENCRYPTION_KEY}') AS ssn
FROM users;
```

### Encryption in Transit

**All inter-service communication over TLS:**

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() throws Exception {
        SSLContext sslContext = SSLContextBuilder
            .create()
            .loadTrustMaterial(new TrustSelfSignedStrategy())
            .build();

        HttpClient httpClient = HttpClients.custom()
            .setSSLContext(sslContext)
            .build();

        HttpComponentsClientHttpRequestFactory factory =
            new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(factory);
    }
}
```

### PII/PCI Compliance

**Mask sensitive data in logs:**

```java
@Aspect
@Component
public class SensitiveDataMaskingAspect {

    @Around("execution(* com.learn..*.*(..)) && @annotation(SensitiveLogging)")
    public Object maskSensitiveData(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String) {
                String value = (String) args[i];
                // Mask credit card numbers
                value = value.replaceAll("\\d{4}-\\d{4}-\\d{4}-\\d{4}", "****-****-****-****");
                // Mask emails
                value = value.replaceAll("([^@]+)@", "***@");
                args[i] = value;
            }
        }

        return joinPoint.proceed(args);
    }
}
```

**Data retention:**

```java
@Component
public class DataRetentionPolicy {

    @Scheduled(cron = "0 0 3 * * *")  // 3 AM daily
    public void enforceRetention() {
        // Delete old logs (90 days)
        logRepository.deleteOlderThan(LocalDateTime.now().minusDays(90));

        // Archive old transactions (7 years for compliance)
        transactionRepository.archiveOlderThan(LocalDateTime.now().minusYears(7));

        // Anonymize deleted user data
        userRepository.anonymizeDeleted();
    }
}
```

---

## Monitoring & Incident Response

### Audit Logging

```java
@Aspect
@Component
public class AuditLoggingAspect {

    @Autowired
    private AuditLogRepository auditLogRepo;

    @Around("@annotation(Audited)")
    public Object logAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        String action = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        Long userId = SecurityContextHolder.getContext()
            .getAuthentication()
            .getUserId();

        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setTimestamp(LocalDateTime.now());
        log.setIpAddress(getClientIp());

        try {
            Object result = joinPoint.proceed();
            log.setStatus("SUCCESS");
            return result;
        } catch (Exception e) {
            log.setStatus("FAILURE");
            log.setError(e.getMessage());
            throw e;
        } finally {
            auditLogRepo.save(log);
        }
    }
}
```

### Security Alerts

```java
@Component
public class SecurityAlertService {

    @Async
    public void sendSecurityAlert(SecurityEvent event) {
        // Multiple failed login attempts
        if (event.getType() == EventType.FAILED_LOGIN &&
            event.getAttempts() >= 5) {

            notifySecurityTeam(
                "Possible brute force attack from IP: " + event.getIpAddress()
            );
        }

        // Large withdrawal
        if (event.getType() == EventType.WITHDRAWAL &&
            event.getAmount().compareTo(new BigDecimal("10000")) > 0) {

            notifySecurityTeam(
                "Large withdrawal: $" + event.getAmount() + " by user " + event.getUserId()
            );
        }
    }
}
```

### Dependency Scanning

```xml
<!-- pom.xml -->
<build>
    <plugins>
        <plugin>
            <groupId>org.owasp</groupId>
            <artifactId>dependency-check-maven</artifactId>
            <version>8.4.0</version>
            <executions>
                <execution>
                    <goals>
                        <goal>check</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

```bash
# Run dependency check
./mvnw dependency-check:check

# Update dependencies
./mvnw versions:display-dependency-updates
```

---

## Summary

**Security Checklist for Production:**

**Configuration:**

- [ ] No secrets in code
- [ ] Environment variables validated
- [ ] Secrets in dedicated manager (AWS/Vault/K8s)
- [ ] Secret rotation automated

**Authentication:**

- [ ] JWT secret ≥ 32 characters
- [ ] MFA for admin accounts
- [ ] OAuth 2.0 for third-party
- [ ] Session timeout configured

**Application:**

- [ ] Input validation on all endpoints
- [ ] Parameterized queries (no SQL injection)
- [ ] CORS configured
- [ ] CSRF protection enabled
- [ ] Rate limiting implemented
- [ ] Security headers set

**Deployment:**

- [ ] HTTPS everywhere (TLS 1.2+)
- [ ] Containers run as non-root
- [ ] Read-only root filesystem
- [ ] Network policies configured
- [ ] Resource limits set

**Data:**

- [ ] Database encryption at rest
- [ ] TLS for all connections
- [ ] PII masked in logs
- [ ] Data retention policy
- [ ] Regular backups

**Monitoring:**

- [ ] Audit logging enabled
- [ ] Security alerts configured
- [ ] Dependency scanning automated
- [ ] Incident response plan

**Next Steps:**

- Implement .env.example template
- Update all application.yml files
- Set up AWS Secrets Manager
- Configure security headers
- Enable dependency scanning in CI/CD

Security is not optional - it's foundational! 🔒
