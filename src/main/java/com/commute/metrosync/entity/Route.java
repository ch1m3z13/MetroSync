package com.commute.metrosync.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a driving route as a PostGIS LineString (polyline).
 * Contains the complete path geometry for geospatial queries.
 */
@Entity
@Table(name = "routes", indexes = {
    @Index(name = "idx_route_geometry", columnList = "geometry"),
    @Index(name = "idx_route_driver", columnList = "driver_id"),
    @Index(name = "idx_route_active", columnList = "is_active")
})
public class Route extends BaseEntity {
    
    @NotNull
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    /**
     * PostGIS LineString geometry (SRID 4326 = WGS84)
     * Represents the complete route path as a polyline
     * Format: LINESTRING(lon1 lat1, lon2 lat2, ...)
     */
    @NotNull
    @Column(name = "geometry", columnDefinition = "geometry(LineString,4326)", nullable = false)
    private LineString geometry;
    
    /**
     * Pre-calculated route distance in kilometers
     * Computed using ST_Length(geography)
     */
    @Column(name = "distance_km")
    private Double distanceKm;
    
    @NotNull
    @Column(name = "driver_id", nullable = false)
    private UUID driverId; // FK to User entity
    
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceOrder ASC")
    private List<VirtualStop> virtualStops = new ArrayList<>();
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_published", nullable = false)
    private Boolean isPublished = false;
    
    /**
     * Maximum deviation from route (in meters) for pickup/dropoff matching
     */
    @Column(name = "max_deviation_meters")
    private Integer maxDeviationMeters = 500;
    
    // Constructors
    public Route() {}
    
    public Route(String name, LineString geometry, UUID driverId) {
        this.name = name;
        this.geometry = geometry;
        this.driverId = driverId;
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public LineString getGeometry() { return geometry; }
    public void setGeometry(LineString geometry) { this.geometry = geometry; }
    
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    
    public UUID getDriverId() { return driverId; }
    public void setDriverId(UUID driverId) { this.driverId = driverId; }
    
    public List<VirtualStop> getVirtualStops() { return virtualStops; }
    public void setVirtualStops(List<VirtualStop> virtualStops) { 
        this.virtualStops = virtualStops; 
    }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }
    
    public Integer getMaxDeviationMeters() { return maxDeviationMeters; }
    public void setMaxDeviationMeters(Integer maxDeviationMeters) { 
        this.maxDeviationMeters = maxDeviationMeters; 
    }
    
    // Helper methods
    public void addVirtualStop(VirtualStop stop) {
        virtualStops.add(stop);
        stop.setRoute(this);
    }
    
    public void removeVirtualStop(VirtualStop stop) {
        virtualStops.remove(stop);
        stop.setRoute(null);
    }
    
    /**
     * Get start point of route
     */
    public Point getStartPoint() {
        return geometry.getStartPoint();
    }
    
    /**
     * Get end point of route
     */
    public Point getEndPoint() {
        return geometry.getEndPoint();
    }
}
