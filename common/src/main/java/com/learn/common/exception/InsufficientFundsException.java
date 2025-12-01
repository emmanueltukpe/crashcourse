package com.learn.common.exception;

/**
 * EDUCATIONAL NOTE - Custom Exceptions in Java
 * 
 * Custom exceptions allow you to create meaningful error types for your domain.
 * By extending RuntimeException, we create "unchecked" exceptions that don't
 * require try-catch blocks (unlike checked exceptions that extend Exception).
 * 
 * Why custom exceptions?
 * 1. Clarity - "InsufficientFundsException" is more descriptive than generic Exception
 * 2. Handling - We can catch specific exception types and handle them differently
 * 3. HTTP mapping - Different exceptions can map to different HTTP status codes
 */

/**
 * Thrown when a user doesn't have enough balance for an operation
 * This will typically map to HTTP 400 Bad Request
 */
public class InsufficientFundsException extends RuntimeException {

  public InsufficientFundsException(String message) {
    super(message);
  }

  public InsufficientFundsException(String message, Throwable cause) {
    super(message, cause);
  }
}
