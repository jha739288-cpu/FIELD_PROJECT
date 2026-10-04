package com.labmarket.exception;

/** Duplicate username/email at registration. Mapped to 409 Conflict. */
public class UserAlreadyExistsException extends RuntimeException {

  public UserAlreadyExistsException(String message) {
    super(message);
  }
}
