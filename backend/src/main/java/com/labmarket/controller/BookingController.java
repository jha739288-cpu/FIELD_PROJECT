package com.labmarket.controller;

import com.labmarket.dto.BookingCreateRequest;
import com.labmarket.dto.BookingResponse;
import com.labmarket.dto.CheckInResponse;
import com.labmarket.dto.CheckOutResponse;
import com.labmarket.dto.PagedResponse;
import com.labmarket.dto.QrCheckInRequest;
import com.labmarket.dto.QrTokenResponse;
import com.labmarket.entity.BookingStatus;
import com.labmarket.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bookings. Any authenticated user may book; staff/admin additionally
 * confirm, reject and see everything.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Equipment reservations with server-side conflict detection")
public class BookingController {

  private final BookingService bookings;

  public BookingController(BookingService bookings) {
    this.bookings = bookings;
  }

  @PostMapping
  @Operation(summary = "Book equipment for a time range (201 PENDING; 409 on overlap)")
  public ResponseEntity<BookingResponse> create(
      @Valid @RequestBody BookingCreateRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(bookings.create(authentication.getName(), request));
  }

  @GetMapping
  @Operation(summary = "List bookings (own for students; all + filters for staff/admin)")
  public ResponseEntity<PagedResponse<BookingResponse>> list(
      @RequestParam(required = false) Long equipmentId,
      @RequestParam(required = false) BookingStatus status,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
      Authentication authentication) {
    return ResponseEntity.ok(
        bookings.list(authentication.getName(), equipmentId, status, pageable));
  }

  @GetMapping("/my")
  @Operation(summary = "Current user's own bookings")
  public ResponseEntity<PagedResponse<BookingResponse>> my(
      @RequestParam(required = false) BookingStatus status,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
      Authentication authentication) {
    return ResponseEntity.ok(bookings.my(authentication.getName(), status, pageable));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get one booking (owner, staff or admin)")
  public ResponseEntity<BookingResponse> get(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(bookings.get(authentication.getName(), id));
  }

  @PutMapping("/{id}/cancel")
  @Operation(summary = "Cancel a booking (owner while PENDING/CONFIRMED, or staff/admin)")
  public ResponseEntity<BookingResponse> cancel(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(bookings.cancel(authentication.getName(), id));
  }

  @PutMapping("/{id}/confirm")
  @PreAuthorize("hasAnyRole('VENDOR', 'ADMIN')")
  @Operation(summary = "Confirm a PENDING booking (staff/admin; re-checks overlap)")
  public ResponseEntity<BookingResponse> confirm(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(bookings.confirm(authentication.getName(), id));
  }

  @PutMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('VENDOR', 'ADMIN')")
  @Operation(summary = "Reject a PENDING booking (staff/admin)")
  public ResponseEntity<BookingResponse> reject(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(bookings.reject(authentication.getName(), id));
  }

  @PostMapping("/{id}/qr")
  @Operation(summary = "Generate a single-use QR check-in token (owner/staff/admin, CONFIRMED only)")
  public ResponseEntity<QrTokenResponse> generateQr(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(bookings.generateQr(authentication.getName(), id));
  }

  @PostMapping("/check-in")
  @Operation(
      summary = "Check in by scanning a QR token (owner only; booking/equipment resolved server-side)")
  public ResponseEntity<CheckInResponse> checkIn(
      @Valid @RequestBody QrCheckInRequest request, Authentication authentication) {
    return ResponseEntity.ok(bookings.checkIn(authentication.getName(), request.qrToken()));
  }

  @PutMapping("/{id}/checkout")
  @Operation(summary = "Check out: closes the usage session with duration (owner/staff/admin)")
  public ResponseEntity<CheckOutResponse> checkOut(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(bookings.checkOut(authentication.getName(), id));
  }
}
