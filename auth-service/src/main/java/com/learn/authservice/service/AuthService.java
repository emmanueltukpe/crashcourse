package com.learn.authservice.service;

import com.learn.authservice.entity.User;
import com.learn.authservice.repository.UserRepository;
import com.learn.authservice.util.JwtUtil;
import com.learn.common.dto.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EDUCATIONAL NOTE - @Service Layer
 * 
 * The Service layer contains business logic - the "brain" of your application.
 * It sits between the Controller (which handles HTTP requests) and the
 * Repository (which handles database access).
 * 
 * Why use a Service layer?
 * 1. Separation of Concerns - Controllers handle HTTP, Services handle logic
 * 2. Reusability - Multiple controllers can use the same service
 * 3. Testability - Easier to unit test business logic without HTTP
 * 4. Transaction Management - @Transactional works at the service level
 * 
 * @Service annotation tells Spring this is a service bean that should be
 *          managed by the Spring container and available for dependency
 *          injection.
 */
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final JwtUtil jwtUtil;
  private final BCryptPasswordEncoder passwordEncoder;

  /**
   * EDUCATIONAL NOTE - Constructor Injection
   * 
   * This constructor uses Dependency Injection (DI). Spring automatically
   * provides (injects) the required dependencies when creating this service.
   * 
   * Constructor injection is preferred over @Autowired field injection because:
   * 1. Makes dependencies explicit and required
   * 2. Makes the class easier to test (can pass mock objects)
   * 3. Ensures the class is in a valid state after construction
   * 4. Works better with immutable (final) fields
   * 
   * Spring sees this constructor and says:
   * "I need UserRepository, JwtUtil, and BCryptPasswordEncoder to create
   * AuthService.
   * Let me find or create those beans and pass them to this constructor."
   */
  public AuthService(UserRepository userRepository, JwtUtil jwtUtil) {
    this.userRepository = userRepository;
    this.jwtUtil = jwtUtil;
    // BCryptPasswordEncoder is created here - we'll configure it as a bean later
    this.passwordEncoder = new BCryptPasswordEncoder();
  }

  /**
   * Register a new user
   * 
   * @Transactional ensures this method runs in a database transaction.
   *                If any exception occurs, all database changes are rolled back.
   * 
   *                EDUCATIONAL NOTE - BCrypt Password Hashing
   * 
   *                BCrypt is a password hashing function designed for security:
   *                1. One-way hash - You can't reverse it to get the original
   *                password
   *                2. Salted - Random data added to each password before hashing
   *                3. Adaptive - Can increase complexity as computers get faster
   * 
   *                Example:
   *                Password: "myPassword123"
   *                BCrypt hash:
   *                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
   * 
   *                Each time you hash the same password, you get a different hash
   *                (due to salt)!
   *                But BCrypt can still verify if a password matches a hash.
   */
  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    // Check if user already exists
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new RuntimeException("Email already registered");
    }

    // Hash the password using BCrypt
    // This is CRITICAL for security - NEVER store plain text passwords!
    String hashedPassword = passwordEncoder.encode(request.getPassword());

    // Create new user entity
    User user = new User(request.getEmail(), hashedPassword);

    // Save to database
    User savedUser = userRepository.save(user);

    // Return response
    return new RegisterResponse(
        savedUser.getId(),
        savedUser.getEmail(),
        "User registered successfully");
  }

  /**
   * Authenticate a user and return JWT token
   * 
   * @param request Login credentials
   * @return JWT token if authentication succeeds
   * @throws RuntimeException if authentication fails
   */
  @Transactional(readOnly = true) // readOnly = true optimizes for read operations
  public LoginResponse login(LoginRequest request) {
    // Find user by email
    User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new RuntimeException("Invalid email or password"));

    // Verify password using BCrypt
    // passwordEncoder.matches() hashes the input and compares to stored hash
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new RuntimeException("Invalid email or password");
    }

    // Generate JWT token
    String token = jwtUtil.generateToken(user.getEmail(), user.getId());

    // Return login response with token
    return new LoginResponse(
        token,
        user.getEmail(),
        user.getId(),
        86400L, // Token expires in 24 hours (86400 seconds)
        "Login successful");
  }
}
