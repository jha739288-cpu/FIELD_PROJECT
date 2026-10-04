package com.labmarket.security;

import com.labmarket.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads users for authentication. Role names map to ROLE_* granted authorities. */
@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  public CustomUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    var user =
        users
            .findByUsername(username)
            // Generic message: never reveal whether the username exists.
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    var authorities =
        user.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName())).toList();
    return new org.springframework.security.core.userdetails.User(
        user.getUsername(), user.getPasswordHash(), user.isEnabled(), true, true, true, authorities);
  }
}
