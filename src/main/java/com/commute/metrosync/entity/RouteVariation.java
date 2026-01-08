package com.commute.metrosync.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.locationtech.jts.geom.LineString;

/**
 * Stores multiple route variations for a driver's commute.
 * A driver can have multiple ways to get from home to work (and vice versa).
 * Example: "Fast route via highway", "Scenic route", "Traffic-free route"
 */
@Entity
@Table(name = "route_variations", indexes = {
    @Index(name = "idx_variation_commute", columnList = "commute_id"),
    @Index(name = "idx_variation_direction", columnList = "direction"),
    @Index(name = "idx_variation_preferred", columnList = "is_preferred")
})
public class RouteVariation extends BaseEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commute_id", nullable = false)
    private DriverCommute commute;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private CommuteDirection direction;
    
    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    private String name;  // e.g., "Fast Route", "Scenic Route"
    
    @Column(name = "description", length = 500)
    private String description;
    
    /**
     * Actual road geometry from Google Directions API
     */
    @NotNull
    @Column(name = "geometry", columnDefinition = "geometry(LineString,4326)", nullable = false)
    private LineString geometry;
    
    /**
     * Google's encoded polyline (for efficient storage/transmission)
     */
    @Column(name = "encoded_polyline", length = 10000)
    private String encodedPolyline;
    
    /**
     * Actual road distance (from Google Directions)
     */
    @Column(name = "distance_km")
    private Double distanceKm;
    
    /**
     * Estimated duration in minutes
     */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    
    /**
     * Is this the driver's preferred route?
     * Only one route per direction can be preferred
     */
    @Column(name = "is_preferred", nullable = false)
    private Boolean isPreferred = false;
    
    /**
     * Is this route currently active?
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    /**
     * Route summary from Google (e.g., "Via I-95 N")
     */
    @Column(name = "route_summary", length = 200)
    private String routeSummary;
    
    // Constructors
    public RouteVariation() {}
    
    public RouteVariation(DriverCommute commute, CommuteDirection direction,
                         String name, LineString geometry) {
        this.commute = commute;
        this.direction = direction;
        this.name = name;
        this.geometry = geometry;
    }
    
    // Getters and Setters
    public DriverCommute getCommute() { return commute; }
    public void setCommute(DriverCommute commute) { this.commute = commute; }
    
    public CommuteDirection getDirection() { return direction; }
    public void setDirection(CommuteDirection direction) { this.direction = direction; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public LineString getGeometry() { return geometry; }
    public void setGeometry(LineString geometry) { this.geometry = geometry; }
    
    public String getEncodedPolyline() { return encodedPolyline; }
    public void setEncodedPolyline(String encodedPolyline) { 
        this.encodedPolyline = encodedPolyline; 
    }
    
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { 
        this.durationMinutes = durationMinutes; 
    }
    
    public Boolean getIsPreferred() { return isPreferred; }
    public void setIsPreferred(Boolean isPreferred) { this.isPreferred = isPreferred; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public String getRouteSummary() { return routeSummary; }
    public void setRouteSummary(String routeSummary) { this.routeSummary = routeSummary; }
}