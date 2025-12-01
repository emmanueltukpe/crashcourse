package com.learn.common.exception;

/**
 * Thrown when a requested resource is not found
 * This will typically map to HTTP 404 Not Found
 */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }

  public ResourceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
