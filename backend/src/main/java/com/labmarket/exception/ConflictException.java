package com.labmarket.exception;

/**
 * Domain rule violation where the request is well-formed but conflicts with
 * current state (duplicate key, illegal transition, blocked delete).
 * Mapped to 409 Conflict.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
