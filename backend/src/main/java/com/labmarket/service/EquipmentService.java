package com.labmarket.service;

import com.labmarket.dto.EquipmentCreateRequest;
import com.labmarket.dto.EquipmentResponse;
import com.labmarket.dto.EquipmentUpdateRequest;
import com.labmarket.dto.PagedResponse;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.User;
import com.labmarket.exception.ConflictException;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.mapper.EquipmentMapper;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.UserRepository;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Equipment catalog rules.
 *
 * <ul>
 *   <li>equipmentCode is unique and immutable.</li>
 *   <li>Only OPERATIONAL items may be AVAILABLE.</li>
 *   <li>IN_USE / RESERVED / OVERDUE items cannot be deleted (a booking owns them).</li>
 * </ul>
 */
@Service
public class EquipmentService {

  private static final Logger log = LoggerFactory.getLogger(EquipmentService.class);

  private static final EnumSet<EquipmentStatus> DELETE_BLOCKED =
      EnumSet.of(EquipmentStatus.IN_USE, EquipmentStatus.RESERVED, EquipmentStatus.OVERDUE);

  private final EquipmentRepository equipment;
  private final UserRepository users;
  private final BookingRepository bookings;
  private final EquipmentMapper mapper;

  public EquipmentService(
      EquipmentRepository equipment,
      UserRepository users,
      BookingRepository bookings,
      EquipmentMapper mapper) {
    this.equipment = equipment;
    this.users = users;
    this.bookings = bookings;
    this.mapper = mapper;
  }

  @Transactional
  public EquipmentResponse create(EquipmentCreateRequest req, String creatorUsername) {
    if (equipment.existsByEquipmentCode(req.equipmentCode())) {
      throw new ConflictException("Equipment code '" + req.equipmentCode() + "' already exists");
    }
    requireConsistentStatus(req.effectiveStatus(), req.effectiveMaintenanceStatus());
    Equipment e = mapper.toEntity(req);
    users.findByUsername(creatorUsername).ifPresent(e::setCreatedBy);
    Equipment saved = equipment.save(e);
    log.info("Equipment '{}' created by '{}'", saved.getEquipmentCode(), creatorUsername);
    return mapper.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public PagedResponse<EquipmentResponse> list(
      EquipmentStatus status, String category, String q, Pageable pageable) {
    // LIKE pattern is built in Java (lower-cased): portable across Oracle/H2
    // and immune to bind-parameter type-inference quirks in concatenations.
    String term = blankToNull(q);
    String pattern = term == null ? null : "%" + term.toLowerCase() + "%";
    Page<Equipment> page = equipment.search(status, blankToNull(category), pattern, pageable);
    return new PagedResponse<>(
        page.getContent().stream().map(mapper::toResponse).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  @Transactional(readOnly = true)
  public EquipmentResponse get(Long id) {
    return mapper.toResponse(findOrThrow(id));
  }

  @Transactional
  public EquipmentResponse update(Long id, EquipmentUpdateRequest req) {
    Equipment e = findOrThrow(id);
    requireConsistentStatus(req.currentStatus(), req.maintenanceStatus());
    mapper.applyUpdate(e, req);
    log.info("Equipment '{}' updated", e.getEquipmentCode());
    return mapper.toResponse(e);
  }

  @Transactional
  public void delete(Long id) {
    Equipment e = findOrThrow(id);
    if (DELETE_BLOCKED.contains(e.getCurrentStatus())) {
      throw new ConflictException(
          "Equipment '" + e.getEquipmentCode() + "' cannot be deleted while it is " + e.getCurrentStatus());
    }
    // Application-level guard: explicit domain rule (not just the DB foreign key),
    // so history-bearing items are protected on every database.
    if (bookings.existsByEquipmentId(id)) {
      throw new ConflictException(
          "Equipment '" + e.getEquipmentCode() + "' cannot be deleted while bookings reference it");
    }
    equipment.delete(e);
    log.info("Equipment '{}' deleted", e.getEquipmentCode());
  }

  private Equipment findOrThrow(Long id) {
    return equipment
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Equipment with id " + id + " not found"));
  }

  private static void requireConsistentStatus(EquipmentStatus status, MaintenanceStatus maintenance) {
    if (status == EquipmentStatus.AVAILABLE && maintenance != MaintenanceStatus.OPERATIONAL) {
      throw new ConflictException(
          "Equipment cannot be AVAILABLE while maintenance status is " + maintenance);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
