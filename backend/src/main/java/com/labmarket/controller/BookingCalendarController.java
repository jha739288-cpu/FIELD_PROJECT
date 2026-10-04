package com.labmarket.controller;

import com.labmarket.dto.AvailabilityResponse;
import com.labmarket.dto.CalendarResponse;
import com.labmarket.dto.EquipmentScheduleResponse;
import com.labmarket.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only calendar views for the frontend. Any authenticated user may call these;
 * slot purposes/usernames are redacted for non-owners (staff/admin see everything).
 * All logic lives in {@link BookingService} — nothing is duplicated here.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Booking calendar", description = "Schedules, availability and calendar lanes")
public class BookingCalendarController {

  private final BookingService bookings;

  public BookingCalendarController(BookingService bookings) {
    this.bookings = bookings;
  }

  @GetMapping("/equipment/{equipmentId}")
  @Operation(summary = "Bookings of one equipment item inside [from, to)")
  public ResponseEntity<EquipmentScheduleResponse> schedule(
      @PathVariable Long equipmentId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      Authentication authentication) {
    return ResponseEntity.ok(
        bookings.schedule(authentication.getName(), equipmentId, from, to));
  }

  @GetMapping("/equipment/{equipmentId}/availability")
  @Operation(summary = "Booked, pending and free periods plus maintenance state for one item")
  public ResponseEntity<AvailabilityResponse> availability(
      @PathVariable Long equipmentId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return ResponseEntity.ok(bookings.availability(equipmentId, from, to));
  }

  @GetMapping("/calendar")
  @Operation(summary = "Calendar lanes for one item (equipmentId) or every item inside [from, to)")
  public ResponseEntity<CalendarResponse> calendar(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) Long equipmentId,
      Authentication authentication) {
    return ResponseEntity.ok(
        bookings.calendar(authentication.getName(), from, to, equipmentId));
  }
}
