package com.labmarket.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmarket.exception.ApiError;
import com.labmarket.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security.
 *
 * <ul>
 *   <li>Public: /api/v1/health, /api/v1/auth/register, /api/v1/auth/login, actuator health/info, swagger.</li>
 *   <li>Everything else: valid Bearer JWT required (401 without, 403 when the role is insufficient).</li>
 *   <li>Fine-grained role rules additionally use {@code @PreAuthorize} on controllers
 *       (USER / VENDOR / ADMIN) as modules land.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            writeError(
                                objectMapper,
                                req,
                                res,
                                HttpStatus.UNAUTHORIZED,
                                "Authentication required"))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            writeError(
                                objectMapper, req, res, HttpStatus.FORBIDDEN, "Access denied")))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/health",
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        // Sensor ingest authenticates per-device via X-Sensor-Key (see SensorService),
                        // so it must pass the JWT filter and enforce its own credentials.
                        "/api/v1/sensors/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private static void writeError(
      ObjectMapper mapper,
      HttpServletRequest req,
      HttpServletResponse res,
      HttpStatus status,
      String message)
      throws IOException {
    res.setStatus(status.value());
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(
        res.getOutputStream(),
        new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            req.getRequestURI()));
  }
}
