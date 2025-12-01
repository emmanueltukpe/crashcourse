package com.learn.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request object for user registration
 * 
 * EDUCATIONAL NOTE - Validation Annotations
 * Spring Boot uses Bean Validation (Jakarta Validation) to validate request
 * data.
 * Annotations like @NotBlank, @Email, @Size provide declarative validation.
 * 
 * When you use @Valid in the controller, Spring automatically validates
 * the object and returns 400 Bad Request if validation fails.
 */
public class RegisterRequest {

  /**
   * User's email address
   * 
   * @Email - Validates email format (must contain @, proper domain, etc.)
   * @NotBlank - Cannot be null, empty, or only whitespace
   */
  @Email(message = "Must be a valid email address")
  @NotBlank(message = "Email is required")
  private String email;

  /**
   * User's password (plain text - will be hashed before storage)
   * 
   * @NotBlank - Cannot be empty
   * @Size - Must be between 8 and 100 characters
   * 
   *       SECURITY NOTE: Never store passwords in plain text!
   *       We will hash this using BCrypt before saving to the database.
   */
  @NotBlank(message = "Password is required")
  @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
  private String password;

  // Default constructor
  public RegisterRequest() {
  }

  // Parameterized constructor
  public RegisterRequest(String email, String password) {
    this.email = email;
    this.password = password;
  }

  // Getters and Setters

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
