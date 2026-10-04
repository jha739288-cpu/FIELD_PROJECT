package com.labmarket.exception;

/** Resource existed but its validity window has passed (e.g. expired QR token). Mapped to 410 Gone. */
public class GoneException extends RuntimeException {

  public GoneException(String message) {
    super(message);
  }
}
