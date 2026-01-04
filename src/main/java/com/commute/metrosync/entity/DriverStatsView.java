package com.commute.metrosync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Maps to the 'driver_dashboard_stats' SQL view.
 * Read-only entity for performance.
 */
@Entity
@Table(name = "driver_dashboard_stats")
@Immutable
public class DriverStatsView {

    @Id
    @Column(name = "driver_id")
    public UUID driverId;

    @Column(precision = 3, scale = 2)
    public BigDecimal rating;

    @Column(name = "total_ratings")
    public Integer totalRatings;

    public String status;

    @Column(name = "current_route_id")
    public UUID currentRouteId;

    @Column(name = "active_passengers")
    public Integer activePassengers;

    @Column(name = "completed_trips")
    public Integer completedTrips;

    @Column(name = "completed_trips_today")
    public Integer completedTripsToday;

    @Column(name = "completed_trips_this_week")
    public Integer completedTripsThisWeek;

    @Column(name = "completed_trips_this_month")
    public Integer completedTripsThisMonth;

    @Column(name = "total_earnings")
    public BigDecimal totalEarnings;

    @Column(name = "earnings_today")
    public BigDecimal earningsToday;

    @Column(name = "earnings_this_week")
    public BigDecimal earningsThisWeek;

    @Column(name = "earnings_this_month")
    public BigDecimal earningsThisMonth;
}