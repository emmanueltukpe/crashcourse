package com.learn.common.dto;

/**
 * Response object returned after successful login
 * Contains the JWT token that the client should include in subsequent requests
 * 
 * EDUCATIONAL NOTE - JWT (JSON Web Tokens)
 * JWTs are a compact way to securely transmit information between parties.
 * After login, the server generates a token that contains the user's identity.
 * The client includes this token in the Authorization header for subsequent
 * requests.
 * 
 * Format: Authorization: Bearer <token>
 */
public class LoginResponse {

  /**
   * The JWT token - a long string that encodes user information
   * Example: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   */
  private String token;

  /**
   * User's email for confirmation
   */
  private String email;

  /**
   * User ID
   */
  private Long userId;

  /**
   * Token expiration time in seconds (e.g., 3600 = 1 hour)
   */
  private Long expiresIn;

  private String message;

  // Default constructor
  public LoginResponse() {
  }

  // Parameterized constructor
  public LoginResponse(String token, String email, Long userId, Long expiresIn, String message) {
    this.token = token;
    this.email = email;
    this.userId = userId;
    this.expiresIn = expiresIn;
    this.message = message;
  }

  // Getters and Setters

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getExpiresIn() {
    return expiresIn;
  }

  public void setExpiresIn(Long expiresIn) {
    this.expiresIn = expiresIn;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
