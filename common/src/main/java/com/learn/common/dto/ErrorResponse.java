package com.learn.common.dto;

import java.time.LocalDateTime;

/**
 * Standardized error response sent to clients when something goes wrong
 * 
 * EDUCATIONAL NOTE - Error Handling in REST APIs
 * Good API design includes consistent error responses. This DTO ensures:
 * 1. Clients always get the same error structure
 * 2. Errors include helpful information (message, timestamp, path)
 * 3. HTTP status codes match the error type (404, 400, 500, etc.)
 * 
 * Spring Boot's @ControllerAdvice lets us handle exceptions globally
 * and convert them to this consistent format.
 */
public class ErrorResponse {

  /**
   * HTTP status code (e.g., 404, 400, 500)
   */
  private int status;

  /**
   * Error message describing what went wrong
   */
  private String message;

  /**
   * When the error occurred
   */
  private LocalDateTime timestamp;

  /**
   * The API path where the error occurred
   */
  private String path;

  /**
   * Optional: detailed error information (for debugging)
   * In production, you might want to hide this from clients
   */
  private String details;

  // Default constructor
  public ErrorResponse() {
    this.timestamp = LocalDateTime.now();
  }

  // Parameterized constructor
  public ErrorResponse(int status, String message, String path) {
    this.status = status;
    this.message = message;
    this.timestamp = LocalDateTime.now();
    this.path = path;
  }

  // Full constructor
  public ErrorResponse(int status, String message, String path, String details) {
    this.status = status;
    this.message = message;
    this.timestamp = LocalDateTime.now();
    this.path = path;
    this.details = details;
  }

  // Getters and Setters

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }
}
