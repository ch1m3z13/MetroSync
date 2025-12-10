package com.commute.metrosync.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Represents a pickup/dropoff point along a route.
 * Uses PostGIS Point geometry for precise location.
 */
@Entity
@Table(name = "virtual_stops", indexes = {
    @Index(name = "idx_virtual_stop_location", columnList = "location"),
    @Index(name = "idx_virtual_stop_route", columnList = "route_id"),
    @Index(name = "idx_virtual_stop_sequence", columnList = "route_id, sequence_order")
})
public class VirtualStop extends BaseEntity {
    
    @NotNull
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    
    @Column(name = "description", length = 500)
    private String description;
    
    /**
     * PostGIS Point geometry (SRID 4326 = WGS84)
     * Stored as POINT(longitude latitude)
     */
    @NotNull
    @Column(name = "location", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point location;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    /**
     * Order of this stop in the route sequence (0-based)
     */
    @NotNull
    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;
    
    /**
     * Estimated time offset from route start (in minutes)
     */
    @Column(name = "time_offset_minutes")
    private Integer timeOffsetMinutes;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    // Constructors
    public VirtualStop() {}
    
    public VirtualStop(String name, Point location, Route route, Integer sequenceOrder) {
        this.name = name;
        this.location = location;
        this.route = route;
        this.sequenceOrder = sequenceOrder;
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Point getLocation() { return location; }
    public void setLocation(Point location) { this.location = location; }
    
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }
    
    public Integer getSequenceOrder() { return sequenceOrder; }
    public void setSequenceOrder(Integer sequenceOrder) { this.sequenceOrder = sequenceOrder; }
    
    public Integer getTimeOffsetMinutes() { return timeOffsetMinutes; }
    public void setTimeOffsetMinutes(Integer timeOffsetMinutes) { 
        this.timeOffsetMinutes = timeOffsetMinutes; 
    }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    /**
     * Helper: Get coordinates as [longitude, latitude]
     */
    public double[] getCoordinates() {
        return new double[]{location.getX(), location.getY()};
    }
}
