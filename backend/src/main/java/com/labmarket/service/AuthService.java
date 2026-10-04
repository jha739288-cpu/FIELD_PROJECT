package com.labmarket.service;

import com.labmarket.dto.AuthResponse;
import com.labmarket.dto.LoginRequest;
import com.labmarket.dto.RegisterRequest;
import com.labmarket.dto.UserResponse;
import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import com.labmarket.exception.UserAlreadyExistsException;
import com.labmarket.mapper.UserMapper;
import com.labmarket.repository.RoleRepository;
import com.labmarket.repository.UserRepository;
import com.labmarket.security.JwtProperties;
import com.labmarket.security.JwtService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration + login. Passwords are BCrypt-hashed before persistence;
 * responses never contain password material.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository users;
  private final RoleRepository roles;
  private final PasswordEncoder encoder;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwt;
  private final JwtProperties jwtProperties;
  private final UserMapper mapper;

  public AuthService(
      UserRepository users,
      RoleRepository roles,
      PasswordEncoder encoder,
      AuthenticationManager authenticationManager,
      JwtService jwt,
      JwtProperties jwtProperties,
      UserMapper mapper) {
    this.users = users;
    this.roles = roles;
    this.encoder = encoder;
    this.authenticationManager = authenticationManager;
    this.jwt = jwt;
    this.jwtProperties = jwtProperties;
    this.mapper = mapper;
  }

  /** Public self-registration. Always assigns STUDENT; staff/admin are granted by an ADMIN later. */
  @Transactional
  public AuthResponse register(RegisterRequest req) {
    // Single generic message: distinct username/email errors would let attackers
    // enumerate registered accounts.
    if (users.existsByUsername(req.username()) || users.existsByEmail(req.email())) {
      throw new UserAlreadyExistsException("Username or email is already registered");
    }
    Role student =
        roles
            .findByName("STUDENT")
            .orElseThrow(() -> new IllegalStateException("STUDENT role is not seeded"));
    User user = new User();
    user.setUsername(req.username());
    user.setEmail(req.email());
    user.setPasswordHash(encoder.encode(req.password()));
    user.setFullName(req.fullName());
    user.setEnabled(true);
    user.getRoles().add(student);
    User saved = users.save(user);
    log.info("Registered user '{}'", saved.getUsername());
    return tokenFor(saved);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest req) {
    // Throws BadCredentialsException / DisabledException on failure (mapped to 401).
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(req.username(), req.password()));
    User user =
        users
            .findByUsername(req.username())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
    log.info("User '{}' logged in", user.getUsername());
    return tokenFor(user);
  }

  @Transactional(readOnly = true)
  public UserResponse me(String username) {
    User user =
        users
            .findByUsername(username)
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
    return mapper.toResponse(user);
  }

  private AuthResponse tokenFor(User user) {
    List<String> roleNames = user.getRoles().stream().map(Role::getName).sorted().toList();
    String token = jwt.generateToken(user.getUsername(), roleNames);
    return new AuthResponse(token, "Bearer", user.getUsername(), roleNames, jwtProperties.expirationMs());
  }
}
