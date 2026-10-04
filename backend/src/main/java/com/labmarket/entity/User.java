package com.labmarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Application user.
 *
 * <p>Credentials live ONLY in {@code passwordHash} as a BCrypt (or stronger) hash.
 * There is no plaintext password field by design — authentication (Module 1) must
 * hash before calling {@link #setPasswordHash(String)}.
 */
@Entity
@Table(
    name = "USERS",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_users_username", columnNames = "username"),
      @UniqueConstraint(name = "uq_users_email", columnNames = "email")
    })
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 50)
  private String username;

  @Column(nullable = false, length = 255)
  private String email;

  /** Secure hash only — NEVER assign a plaintext password here. */
  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "full_name", length = 100)
  private String fullName;

  /** Roles held by this user (join table USER_ROLES). Assigned at registration; managed by ADMIN later. */
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "USER_ROLES",
      joinColumns = @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_ur_user")),
      inverseJoinColumns =
          @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "fk_ur_role")))
  private Set<Role> roles = new HashSet<>();

  @Column(name = "is_enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Public for services/builders; JPA also uses it. Password handling stays hash-only. */
  public User() {}

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Set<Role> getRoles() {
    return roles;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
