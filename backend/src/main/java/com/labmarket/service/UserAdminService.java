package com.labmarket.service;

import com.labmarket.dto.PagedResponse;
import com.labmarket.dto.RolesUpdateRequest;
import com.labmarket.dto.UserAdminResponse;
import com.labmarket.dto.VendorSummaryResponse;
import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.RoleRepository;
import com.labmarket.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration (ADMIN only — see controller). Admins can search,
 * enable/disable and re-role accounts, but never touch their own account
 * (avoids self-lockout) and never see password material.
 */
@Service
public class UserAdminService {

  private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

  private static final Set<String> KNOWN_ROLES = Set.of("ADMIN", "USER", "VENDOR");

  private final UserRepository users;
  private final RoleRepository roles;
  private final BookingRepository bookings;
  private final EquipmentRepository equipment;

  public UserAdminService(
      UserRepository users,
      RoleRepository roles,
      BookingRepository bookings,
      EquipmentRepository equipment) {
    this.users = users;
    this.roles = roles;
    this.bookings = bookings;
    this.equipment = equipment;
  }

  @Transactional(readOnly = true)
  public PagedResponse<UserAdminResponse> list(
      String q, String role, Boolean enabled, Pageable pageable) {
    String term = q == null || q.isBlank() ? null : "%" + q.toLowerCase() + "%";
    Page<User> page = users.searchAdmin(term, role, enabled, pageable);
    return new PagedResponse<>(
        page.getContent().stream().map(this::toResponse).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  @Transactional(readOnly = true)
  public UserAdminResponse get(Long id) {
    return toResponse(findOrThrow(id));
  }

  @Transactional
  public UserAdminResponse setEnabled(String callerUsername, Long id, boolean enabled) {
    User target = findOrThrow(id);
    if (target.getUsername().equals(callerUsername)) {
      throw new AccessDeniedException("You cannot change your own account status");
    }
    target.setEnabled(enabled);
    log.info("Account '{}' {} by '{}'", target.getUsername(), enabled ? "enabled" : "disabled",
        callerUsername);
    return toResponse(target);
  }

  @Transactional
  public UserAdminResponse setRoles(String callerUsername, Long id, RolesUpdateRequest req) {
    User target = findOrThrow(id);
    if (target.getUsername().equals(callerUsername)) {
      throw new AccessDeniedException("You cannot change your own roles");
    }
    for (String name : req.roles()) {
      if (!KNOWN_ROLES.contains(name)) {
        throw new IllegalArgumentException("Unknown role '" + name + "'");
      }
    }
    target.getRoles().clear();
    for (String name : req.roles()) {
      Role role =
          roles
              .findByName(name)
              .orElseThrow(() -> new IllegalStateException(name + " role is not seeded"));
      target.getRoles().add(role);
    }
    log.info("Roles of '{}' set to {} by '{}'", target.getUsername(), req.roles(), callerUsername);
    return toResponse(target);
  }

  @Transactional(readOnly = true)
  public PagedResponse<VendorSummaryResponse> vendors(String q, Pageable pageable) {
    String term = q == null || q.isBlank() ? null : "%" + q.toLowerCase() + "%";
    Page<User> page = users.searchAdmin(term, "VENDOR", null, pageable);
    return new PagedResponse<>(
        page.getContent().stream()
            .map(
                u ->
                    new VendorSummaryResponse(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getFullName(),
                        u.isEnabled(),
                        u.getCreatedAt(),
                        equipment.countByCreatedById(u.getId()),
                        bookings.countByEquipmentCreatedById(u.getId())))
            .toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  private User findOrThrow(Long id) {
    return users
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " not found"));
  }

  private UserAdminResponse toResponse(User u) {
    List<String> roleNames =
        u.getRoles().stream().map(Role::getName).sorted().toList();
    return new UserAdminResponse(
        u.getId(),
        u.getUsername(),
        u.getEmail(),
        u.getFullName(),
        u.isEnabled(),
        roleNames,
        u.getCreatedAt(),
        bookings.countByOwner_Id(u.getId()));
  }
}
