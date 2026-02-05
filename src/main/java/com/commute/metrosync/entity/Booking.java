package com.commute.metrosync.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_ride", columnList = "ride_id"),
    @Index(name = "idx_bookings_rider", columnList = "rider_id"),
    @Index(name = "idx_bookings_status", columnList = "status"),
    @Index(name = "idx_bookings_pin", columnList = "safety_pin")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class Booking extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id", nullable = false)
    private User rider;

    @Column(name = "pickup_location", nullable = false)
    private String pickupLocation;

    @Column(name = "pickup_point", columnDefinition = "geography(Point, 4326)")
    private Point pickupPoint;

    @Column(name = "dropoff_location", nullable = false)
    private String dropoffLocation;

    @Column(name = "dropoff_point", columnDefinition = "geography(Point, 4326)")
    private Point dropoffPoint;

    @Column(name = "seats_requested", nullable = false)
    private Integer seatsRequested = 1;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "safety_pin", length = 4)
    private String safetyPin;

    @CreationTimestamp
    @Column(name = "booking_time", nullable = false, updatable = false)
    private LocalDateTime bookingTime;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    // Business methods
    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void start() {
        this.status = BookingStatus.ACTIVE;
        this.startedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = BookingStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel(String reason) {
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }

    public boolean canBeCancelled() {
        return status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED;
    }

    public enum BookingStatus {
        PENDING, CONFIRMED, ACTIVE, COMPLETED, CANCELLED
    }

    // Static finder methods
    public static java.util.List<Booking> findByRider(Long riderId) {
        return list("rider.id", riderId);
    }

    public static java.util.List<Booking> findByRide(Long rideId) {
        return list("ride.id", rideId);
    }

    public static Booking findByPin(String pin) {
        return find("safetyPin", pin).firstResult();
    }
}