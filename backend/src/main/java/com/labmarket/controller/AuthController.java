package com.labmarket.controller;

import com.labmarket.dto.AuthResponse;
import com.labmarket.dto.LoginRequest;
import com.labmarket.dto.RegisterRequest;
import com.labmarket.dto.UserResponse;
import com.labmarket.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public register/login plus the authenticated caller's own profile. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, login and current-user profile")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  @SecurityRequirements // public: no bearer token needed
  @Operation(summary = "Register a new STUDENT account (201 on success, 409 if taken)")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(request));
  }

  @PostMapping("/login")
  @SecurityRequirements // public: no bearer token needed
  @Operation(summary = "Login with username + password (401 on bad credentials)")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(auth.login(request));
  }

  @GetMapping("/me")
  @Operation(summary = "Current user's profile (requires a valid JWT)")
  public ResponseEntity<UserResponse> me(Authentication authentication) {
    return ResponseEntity.ok(auth.me(authentication.getName()));
  }
}
