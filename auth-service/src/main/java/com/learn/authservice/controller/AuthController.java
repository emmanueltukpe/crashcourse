package com.learn.authservice.controller;

import com.learn.authservice.service.AuthService;
import com.learn.common.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * EDUCATIONAL NOTE - REST Controllers
 * 
 * A Controller handles HTTP requests and returns HTTP responses.
 * It's the entry point for client requests to your application.
 * 
 * @RestController is a Spring annotation that combines:
 *                 - @Controller - Marks this as a controller class
 *                 - @ResponseBody - Automatically converts return values to
 *                 JSON
 * 
 *                 Think of controllers as the "receptionist" of your
 *                 application:
 *                 - They receive requests from clients
 *                 - Validate the request data
 *                 - Call the appropriate service methods
 *                 - Return properly formatted responses
 * 
 *                 Best practice: Controllers should be "thin" - they should NOT
 *                 contain
 *                 business logic. That belongs in the Service layer.
 */
@RestController
@RequestMapping("/api/v1/auth") // All endpoints in this controller start with /api/v1/auth
public class AuthController {

  private final AuthService authService;

  /**
   * Dependency Injection via constructor
   * Spring automatically provides the AuthService instance
   */
  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * Register a new user
   * 
   * EDUCATIONAL NOTE - HTTP Methods and Annotations
   * 
   * @PostMapping maps HTTP POST requests to this method
   *              POST is used for creating new resources
   * 
   *              Full URL: POST http://localhost:8081/api/v1/auth/register
   * 
   * @RequestBody - Tells Spring to deserialize the request JSON into
   *              RegisterRequest
   * @Valid - Triggers validation (@Email, @NotBlank, etc. in RegisterRequest)
   * 
   *        ResponseEntity<T> allows us to:
   *        - Control the HTTP status code (200, 201, 400, 500, etc.)
   *        - Set response headers
   *        - Return the response body (automatically converted to JSON)
   * 
   *        HTTP Status Codes:
   *        - 201 Created - Resource successfully created
   *        - 400 Bad Request - Invalid input
   *        - 500 Internal Server Error - Something went wrong on server
   * 
   *        Example request:
   *        POST /api/v1/auth/register
   *        Content-Type: application/json
   *        {
   *        "email": "user@example.com",
   *        "password": "securePassword123"
   *        }
   */
  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    try {
      RegisterResponse response = authService.register(request);
      // Return 201 Created status with the response body
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (RuntimeException e) {
      // In a real application, use @ControllerAdvice for global exception handling
      // For now, return 400 Bad Request with error message
      RegisterResponse errorResponse = new RegisterResponse(null, null, e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
  }

  /**
   * Login and receive JWT token
   * 
   * @PostMapping("/login") maps to POST /api/v1/auth/login
   * 
   * Example request:
   * POST /api/v1/auth/login
   * Content-Type: application/json
   * {
   * "email": "user@example.com",
   * "password": "securePassword123"
   * }
   * 
   * Example response:
   * {
   * "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
   * "email": "user@example.com",
   * "userId": 1,
   * "expiresIn": 86400,
   * "message": "Login successful"
   * }
   */
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    try {
      LoginResponse response = authService.login(request);
      // Return 200 OK with the token
      return ResponseEntity.ok(response);
    } catch (RuntimeException e) {
      // Return 401 Unauthorized if login fails
      LoginResponse errorResponse = new LoginResponse(null, null, null, null, e.getMessage());
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
  }

  /**
   * Health check endpoint
   * 
   * @GetMapping maps HTTP GET requests
   *             GET is used for retrieving data (not modifying anything)
   * 
   *             This simple endpoint helps verify the service is running
   */
  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("Auth service is running");
  }
}
