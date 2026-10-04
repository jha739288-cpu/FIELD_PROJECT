package com.labmarket.exception;

/** Device-key authentication failure on the sensor ingest path. Mapped to 401. */
public class SensorUnauthorizedException extends RuntimeException {

  public SensorUnauthorizedException(String message) {
    super(message);
  }
}
