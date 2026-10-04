package com.labmarket.entity;

/**
 * Booking lifecycle.
 *
 * <ul>
 *   <li>PENDING — created by a student, awaiting staff decision.</li>
 *   <li>CONFIRMED / REJECTED — decided by LAB_STAFF or ADMIN.</li>
 *   <li>CANCELLED — by the owner (while PENDING/CONFIRMED) or staff/admin.</li>
 *   <li>CHECKED_IN / COMPLETED / OVERDUE — owned by the QR and overdue modules (later).</li>
 * </ul>
 */
public enum BookingStatus {
  PENDING,
  CONFIRMED,
  CHECKED_IN,
  COMPLETED,
  CANCELLED,
  OVERDUE,
  REJECTED;

  /** States that deterministically block a time slot (and a new reservation). */
  public static boolean isBlocking(BookingStatus status) {
    return status == CONFIRMED || status == CHECKED_IN || status == OVERDUE;
  }
}
