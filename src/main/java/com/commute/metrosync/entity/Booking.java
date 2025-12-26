package com.commute.metrosync.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a ride booking in the system.
 * Manages the complete lifecycle from request to completion.
 */
@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_booking_rider", columnList = "rider_id"),
    @Index(name = "idx_booking_route", columnList = "route_id"),
    @Index(name = "idx_booking_status", columnList = "status"),
    @Index(name = "idx_booking_scheduled_time", columnList = "scheduled_pickup_time"),
    @Index(name = "idx_booking_pickup_location", columnList = "pickup_location"),
    @Index(name = "idx_booking_dropoff_location", columnList = "dropoff_location")
})
public class Booking extends BaseEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id", nullable = false)
    private User rider;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    /**
     * Pickup location (must be near route)
     */
    @NotNull
    @Column(name = "pickup_location", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point pickupLocation;
    
    /**
     * Dropoff location (must be near route)
     */
    @NotNull
    @Column(name = "dropoff_location", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point dropoffLocation;
    
    /**
     * Nearest virtual stop to pickup
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_stop_id")
    private VirtualStop pickupStop;
    
    /**
     * Nearest virtual stop to dropoff
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dropoff_stop_id")
    private VirtualStop dropoffStop;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;
    
    @NotNull
    @Column(name = "scheduled_pickup_time", nullable = false)
    private LocalDateTime scheduledPickupTime;
    
    @Column(name = "estimated_dropoff_time")
    private LocalDateTime estimatedDropoffTime;
    
    @Column(name = "actual_pickup_time")
    private LocalDateTime actualPickupTime;
    
    @Column(name = "actual_dropoff_time")
    private LocalDateTime actualDropoffTime;
    
    /**
     * Number of passengers
     */
    @NotNull
    @Column(name = "passenger_count", nullable = false)
    private Integer passengerCount = 1;
    
    /**
     * Calculated fare
     */
    @NotNull
    @Column(name = "fare_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal fareAmount;
    
    /**
     * Distance from pickup to dropoff along route (in km)
     */
    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;
    
    /**
     * Special instructions from rider
     */
    @Column(name = "special_instructions", length = 500)
    private String specialInstructions;
    
    /**
     * Rider's rating of the trip (1-5)
     */
    @Column(name = "rider_rating")
    private Integer riderRating;
    
    /**
     * Driver's rating of the rider (1-5)
     */
    @Column(name = "driver_rating")
    private Integer driverRating;
    
    @Column(name = "rider_feedback", length = 1000)
    private String riderFeedback;
    
    @Column(name = "driver_feedback", length = 1000)
    private String driverFeedback;
    
    /**
     * Cancellation reason if cancelled
     */
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;
    
    @Column(name = "cancelled_by")
    private UUID cancelledBy;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    // Reference number for booking
    @Column(name = "reference_number", unique = true, length = 50)
    private String referenceNumber;

    // Constructors - generate reference number if not provided
    public Booking() {
        super();
        if (this.referenceNumber == null) {
            this.referenceNumber = generateReferenceNumber();
        }
    }
    
    public Booking(User rider, Route route, Point pickupLocation, 
                   Point dropoffLocation, LocalDateTime scheduledTime, 
                   BigDecimal fareAmount) {
        this.rider = rider;
        this.route = route;
        this.pickupLocation = pickupLocation;
        this.dropoffLocation = dropoffLocation;
        this.scheduledPickupTime = scheduledTime;
        this.fareAmount = fareAmount;
        if (this.referenceNumber == null) {
            this.referenceNumber = generateReferenceNumber();
        }
    }
        // Getter and setter for referenceNumber
        public String getReferenceNumber() {
            return referenceNumber;
        }

        public void setReferenceNumber(String referenceNumber) {
            this.referenceNumber = referenceNumber;
        }

        // Helper method to generate reference numbers
        private String generateReferenceNumber() {
            return "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    
    // State transition methods
    public void confirm() {
        if (status != BookingStatus.PENDING) {
            throw new IllegalStateException("Can only confirm pending bookings");
        }
        this.status = BookingStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }
    
    public void startRide() {
        if (status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Can only start confirmed bookings");
        }
        this.status = BookingStatus.IN_PROGRESS;
        this.actualPickupTime = LocalDateTime.now();
    }
    
    public void complete() {
        if (status != BookingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only complete in-progress bookings");
        }
        this.status = BookingStatus.COMPLETED;
        this.actualDropoffTime = LocalDateTime.now();
        this.completedAt = LocalDateTime.now();
    }
    
    public void cancel(UUID cancelledBy, String reason) {
        if (status == BookingStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed bookings");
        }
        if (status == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking already cancelled");
        }
        
        this.status = BookingStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancellationReason = reason;
        this.cancelledAt = LocalDateTime.now();
    }
    
    public boolean canBeCancelled() {
        return status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED;
    }
    
    public boolean canBeModified() {
        return status == BookingStatus.PENDING;
    }
    
    public boolean isActive() {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.IN_PROGRESS;
    }
    
    // Getters and Setters
    public User getRider() { return rider; }
    public void setRider(User rider) { this.rider = rider; }
    
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }
    
    public Point getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(Point pickupLocation) { 
        this.pickupLocation = pickupLocation; 
    }
    
    public Point getDropoffLocation() { return dropoffLocation; }
    public void setDropoffLocation(Point dropoffLocation) { 
        this.dropoffLocation = dropoffLocation; 
    }
    
    public VirtualStop getPickupStop() { return pickupStop; }
    public void setPickupStop(VirtualStop pickupStop) { this.pickupStop = pickupStop; }
    
    public VirtualStop getDropoffStop() { return dropoffStop; }
    public void setDropoffStop(VirtualStop dropoffStop) { this.dropoffStop = dropoffStop; }
    
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    
    public LocalDateTime getScheduledPickupTime() { return scheduledPickupTime; }
    public void setScheduledPickupTime(LocalDateTime scheduledPickupTime) { 
        this.scheduledPickupTime = scheduledPickupTime; 
    }
    
    public LocalDateTime getEstimatedDropoffTime() { return estimatedDropoffTime; }
    public void setEstimatedDropoffTime(LocalDateTime estimatedDropoffTime) { 
        this.estimatedDropoffTime = estimatedDropoffTime; 
    }
    
    public LocalDateTime getActualPickupTime() { return actualPickupTime; }
    public void setActualPickupTime(LocalDateTime actualPickupTime) { 
        this.actualPickupTime = actualPickupTime; 
    }
    
    public LocalDateTime getActualDropoffTime() { return actualDropoffTime; }
    public void setActualDropoffTime(LocalDateTime actualDropoffTime) { 
        this.actualDropoffTime = actualDropoffTime; 
    }
    
    public Integer getPassengerCount() { return passengerCount; }
    public void setPassengerCount(Integer passengerCount) { 
        this.passengerCount = passengerCount; 
    }
    
    public BigDecimal getFareAmount() { return fareAmount; }
    public void setFareAmount(BigDecimal fareAmount) { this.fareAmount = fareAmount; }
    
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }
    
    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { 
        this.specialInstructions = specialInstructions; 
    }
    
    public Integer getRiderRating() { return riderRating; }
    public void setRiderRating(Integer riderRating) { this.riderRating = riderRating; }
    
    public Integer getDriverRating() { return driverRating; }
    public void setDriverRating(Integer driverRating) { this.driverRating = driverRating; }
    
    public String getRiderFeedback() { return riderFeedback; }
    public void setRiderFeedback(String riderFeedback) { this.riderFeedback = riderFeedback; }
    
    public String getDriverFeedback() { return driverFeedback; }
    public void setDriverFeedback(String driverFeedback) { 
        this.driverFeedback = driverFeedback; 
    }
    
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { 
        this.cancellationReason = cancellationReason; 
    }
    
    public UUID getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(UUID cancelledBy) { this.cancelledBy = cancelledBy; }
    
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
