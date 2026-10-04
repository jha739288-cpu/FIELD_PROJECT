package com.labmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labmarket.dto.EquipmentCreateRequest;
import com.labmarket.dto.EquipmentResponse;
import com.labmarket.dto.EquipmentUpdateRequest;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentCondition;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.User;
import com.labmarket.exception.ConflictException;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.mapper.EquipmentMapper;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pure unit tests for catalog domain rules (no Spring context). */
@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

  @Mock private EquipmentRepository equipment;
  @Mock private UserRepository users;
  @Mock private BookingRepository bookings;
  @Spy private EquipmentMapper mapper = new EquipmentMapper();

  @InjectMocks private EquipmentService service;

  @Test
  void createAppliesDefaultsAndCreator() {
    User staff = new User();
    staff.setUsername("stf");
    when(equipment.existsByEquipmentCode("OSC-001")).thenReturn(false);
    when(users.findByUsername("stf")).thenReturn(Optional.of(staff));
    when(equipment.save(any(Equipment.class))).thenAnswer(i -> i.getArgument(0));

    EquipmentResponse res = service.create(create("OSC-001", null, null), "stf");

    assertEquals("OSC-001", res.equipmentCode());
    assertEquals(EquipmentCondition.GOOD, res.condition());
    assertEquals(EquipmentStatus.AVAILABLE, res.currentStatus());
    assertEquals(MaintenanceStatus.OPERATIONAL, res.maintenanceStatus());
    assertEquals("stf", res.createdByUsername());
  }

  @Test
  void createDuplicateCodeThrowsConflict() {
    when(equipment.existsByEquipmentCode("OSC-001")).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.create(create("OSC-001", null, null), "stf"));
    verify(equipment, never()).save(any());
  }

  @Test
  void createAvailableWithNonOperationalMaintenanceThrowsConflict() {
    assertThrows(
        ConflictException.class,
        () -> service.create(create("OSC-001", EquipmentStatus.AVAILABLE, MaintenanceStatus.OUT_OF_SERVICE), "stf"));
    verify(equipment, never()).save(any());
  }

  @Test
  void getMissingThrowsNotFound() {
    when(equipment.findById(99L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> service.get(99L));
  }

  @Test
  void updateAppliesChanges() {
    Equipment existing = existing(1L, "OSC-001");
    when(equipment.findById(1L)).thenReturn(Optional.of(existing));

    EquipmentResponse res =
        service.update(
            1L,
            new EquipmentUpdateRequest(
                "New name", "Physics", null, null, null, "Lab A",
                EquipmentCondition.FAIR, EquipmentStatus.MAINTENANCE, MaintenanceStatus.IN_MAINTENANCE));

    assertEquals("New name", res.name());
    assertEquals("OSC-001", res.equipmentCode()); // immutable
    assertEquals(EquipmentStatus.MAINTENANCE, res.currentStatus());
  }

  @Test
  void deleteBlockedWhileInUse() {
    Equipment existing = existing(1L, "OSC-001");
    existing.setCurrentStatus(EquipmentStatus.IN_USE);
    when(equipment.findById(1L)).thenReturn(Optional.of(existing));

    assertThrows(ConflictException.class, () -> service.delete(1L));
    verify(equipment, never()).delete(any());
  }

  @Test
  void deleteWithBookingsThrowsConflict() {
    Equipment existing = existing(1L, "OSC-001");
    when(equipment.findById(1L)).thenReturn(Optional.of(existing));
    when(bookings.existsByEquipmentId(1L)).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.delete(1L));
    verify(equipment, never()).delete(any());
  }

  @Test
  void deleteAvailableSucceeds() {
    Equipment existing = existing(1L, "OSC-001");
    when(equipment.findById(1L)).thenReturn(Optional.of(existing));

    service.delete(1L);

    verify(equipment).delete(existing);
  }

  private static EquipmentCreateRequest create(
      String code, EquipmentStatus status, MaintenanceStatus maintenance) {
    return new EquipmentCreateRequest(
        code, "Oscilloscope", "Electronics", null, "Acme", "X-100", "Lab A",
        null, status, maintenance);
  }

  private static Equipment existing(Long id, String code) {
    Equipment e = new Equipment();
    e.setEquipmentCode(code);
    e.setName("Oscilloscope");
    e.setCategory("Electronics");
    try {
      var idField = Equipment.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(e, id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return e;
  }
}
