package com.commute.metrosync.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "routes", indexes = {
    @Index(name = "idx_routes_driver", columnList = "driver_id"),
    @Index(name = "idx_routes_active", columnList = "is_active")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class Route extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @Column(name = "from_location", nullable = false)
    private String fromLocation;

    @Column(name = "from_point", columnDefinition = "geography(Point, 4326)")
    private Point fromPoint;

    @Column(name = "to_location", nullable = false)
    private String toLocation;

    @Column(name = "to_point", columnDefinition = "geography(Point, 4326)")
    private Point toPoint;

    @Column(name = "route_path", columnDefinition = "geography(LineString, 4326)")
    private LineString routePath;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "booked_seats")
    private Integer bookedSeats = 0;

    @Column(name = "price_per_seat", nullable = false)
    private Integer pricePerSeat;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column
    private Boolean recurring = true;

    @Column(name = "days_of_week")
    private int[] daysOfWeek;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static List<Route> findActiveRoutes() {
        return list("isActive", true);
    }
}