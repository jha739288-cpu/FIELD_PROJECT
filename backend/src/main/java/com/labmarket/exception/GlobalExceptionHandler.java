package com.labmarket.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handling (infrastructure, not a business module).
 * Business exceptions (e.g. booking conflicts) add handlers here in later modules.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .sorted()
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    return error(HttpStatus.BAD_REQUEST, message, req, false);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    return error(HttpStatus.BAD_REQUEST, ex.getMessage(), req, false);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest req) {
    return error(HttpStatus.BAD_REQUEST, ex.getMessage(), req, false);
  }

  @ExceptionHandler(UserAlreadyExistsException.class)
  public ResponseEntity<ApiError> handleUserAlreadyExists(
      UserAlreadyExistsException ex, HttpServletRequest req) {
    return error(HttpStatus.CONFLICT, ex.getMessage(), req, false);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest req) {
    // Thrown by @PreAuthorize role checks (method security runs after the filter chain,
    // so the SecurityConfig access-denied handler cannot see it).
    return error(HttpStatus.FORBIDDEN, "Access denied", req, false);
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiError> handleBadCredentials(
      BadCredentialsException ex, HttpServletRequest req) {
    // Generic message: never reveal whether the username exists.
    return error(HttpStatus.UNAUTHORIZED, "Invalid credentials", req, false);
  }

  @ExceptionHandler(DisabledException.class)
  public ResponseEntity<ApiError> handleDisabledAccount(
      DisabledException ex, HttpServletRequest req) {
    return error(HttpStatus.UNAUTHORIZED, "Account is disabled", req, false);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiError> handleAuthentication(
      AuthenticationException ex, HttpServletRequest req) {
    return error(HttpStatus.UNAUTHORIZED, "Authentication required", req, false);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest req) {
    // Malformed JSON or unknown enum values (e.g. sensor status typos).
    return error(HttpStatus.BAD_REQUEST, "Malformed request body", req, false);
  }

  @ExceptionHandler(SensorUnauthorizedException.class)
  public ResponseEntity<ApiError> handleSensorUnauthorized(
      SensorUnauthorizedException ex, HttpServletRequest req) {
    return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, false);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiError> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
    // Covers invalid enum query params (?status=BROKEN) and bad path variables.
    return error(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'", req, false);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiError> handleMissingParam(
      MissingServletRequestParameterException ex, HttpServletRequest req) {
    return error(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'", req, false);
  }

  @ExceptionHandler(InvalidDataAccessApiUsageException.class)
  public ResponseEntity<ApiError> handleBadSort(
      InvalidDataAccessApiUsageException ex, HttpServletRequest req) {
    // E.g. unknown ?sort= property (Hibernate UnknownPathException): client error, not a server failure.
    return error(HttpStatus.BAD_REQUEST, "Invalid query (check sort/filter properties)", req, false);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> handleDataIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest req) {
    // FK/unique violations at the DB layer (e.g. deleting equipment that has
    // bookings). Never leak constraint names or SQL.
    return error(
        HttpStatus.CONFLICT, "Request conflicts with existing records", req, false);
  }

  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ApiError> handleTooManyRequests(
      TooManyRequestsException ex, HttpServletRequest req) {
    return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req, false);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiError> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
    return error(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req, false);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiError> handleResourceNotFound(
      ResourceNotFoundException ex, HttpServletRequest req) {
    return error(HttpStatus.NOT_FOUND, ex.getMessage(), req, false);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
    return error(HttpStatus.CONFLICT, ex.getMessage(), req, false);
  }

  @ExceptionHandler(GoneException.class)
  public ResponseEntity<ApiError> handleGone(GoneException ex, HttpServletRequest req) {
    return error(HttpStatus.GONE, ex.getMessage(), req, false);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest req) {
    return error(HttpStatus.NOT_FOUND, ex.getMessage(), req, false);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req, true);
  }

  private ResponseEntity<ApiError> error(
      HttpStatus status, String message, HttpServletRequest req, boolean stack) {
    if (stack) {
      log.error("Unhandled error on {}: {}", req.getRequestURI(), message);
    } else {
      log.warn("Request error {} on {}: {}", status.value(), req.getRequestURI(), message);
    }
    return ResponseEntity.status(status)
        .body(
            new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
  }
}
