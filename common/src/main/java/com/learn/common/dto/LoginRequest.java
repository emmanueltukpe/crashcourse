package com.learn.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request object for user login
 */
public class LoginRequest {

  @Email(message = "Must be a valid email address")
  @NotBlank(message = "Email is required")
  private String email;

  @NotBlank(message = "Password is required")
  private String password;

  // Default constructor
  public LoginRequest() {
  }

  // Parameterized constructor
  public LoginRequest(String email, String password) {
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
