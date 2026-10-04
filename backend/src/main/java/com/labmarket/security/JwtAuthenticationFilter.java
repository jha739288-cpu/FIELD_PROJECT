package com.labmarket.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless JWT filter. Valid Bearer token → authenticated request.
 * Missing/invalid token → request stays anonymous and the entry point
 * answers 401 on protected endpoints.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private final JwtService jwt;
  private final CustomUserDetailsService userDetails;

  public JwtAuthenticationFilter(JwtService jwt, CustomUserDetailsService userDetails) {
    this.jwt = jwt;
    this.userDetails = userDetails;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      if (jwt.isValid(token)) {
        try {
          UserDetails details = userDetails.loadUserByUsername(jwt.extractUsername(token));
          SecurityContextHolder.getContext()
              .setAuthentication(
                  new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        } catch (UsernameNotFoundException e) {
          log.debug("JWT subject no longer exists; leaving request anonymous");
          SecurityContextHolder.clearContext();
        }
      } else {
        log.debug("Invalid JWT on {} — leaving request anonymous", request.getRequestURI());
      }
    }
    chain.doFilter(request, response);
  }
}
