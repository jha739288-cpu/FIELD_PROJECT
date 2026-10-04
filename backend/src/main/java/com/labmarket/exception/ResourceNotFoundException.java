package com.labmarket.exception;

/** Requested resource does not exist. Mapped to 404 Not Found. */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
