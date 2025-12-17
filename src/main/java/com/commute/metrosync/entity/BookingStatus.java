package com.commute.metrosync.entity;

/**
 * Lifecycle states of a booking
 */
public enum BookingStatus {
    PENDING,        // Booking created, awaiting driver confirmation
    CONFIRMED,      // Driver confirmed, waiting for scheduled time
    IN_PROGRESS,    // Ride in progress (picked up passenger)
    COMPLETED,      // Ride completed successfully
    CANCELLED,      // Booking cancelled by rider or driver
    NO_SHOW         // Rider didn't show up at pickup
}