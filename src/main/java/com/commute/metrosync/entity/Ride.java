package com.commute.metrosync.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "rides", indexes = {
    @Index(name = "idx_rides_route", columnList = "route_id"),
    @Index(name = "idx_rides_driver", columnList = "driver_id"),
    @Index(name = "idx_rides_status", columnList = "status"),
    @Index(name = "idx_rides_start_time", columnList = "scheduled_start_time")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class Ride extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @Column(name = "scheduled_start_time", nullable = false)
    private LocalDateTime scheduledStartTime;

    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RideStatus status = RideStatus.SCHEDULED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum RideStatus {
        SCHEDULED, ACTIVE, COMPLETED, CANCELLED
    }
}