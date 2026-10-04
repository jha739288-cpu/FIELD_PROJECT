package com.labmarket.mapper;

import com.labmarket.dto.UserResponse;
import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import org.springframework.stereotype.Component;

/** Entity → DTO conversions for users. Password hash never leaves this layer. */
@Component
public class UserMapper {

  public UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getFullName(),
        user.isEnabled(),
        user.getRoles().stream().map(Role::getName).sorted().toList());
  }
}
