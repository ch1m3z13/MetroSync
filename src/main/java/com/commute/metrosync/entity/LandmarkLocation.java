package com.commute.metrosync.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.locationtech.jts.geom.Point;

/**
 * Curated landmark locations in Abuja
 * Used for driver signup to reduce reliance on external geocoding APIs
 */
@Entity
@Table(name = "landmark_locations", indexes = {
    @Index(name = "idx_landmark_location", columnList = "location"),
    @Index(name = "idx_landmark_category", columnList = "category"),
    @Index(name = "idx_landmark_district", columnList = "district"),
    @Index(name = "idx_landmark_popularity", columnList = "popularity_score")
})
public class LandmarkLocation extends BaseEntity {

    public enum LandmarkCategory {
        JUNCTION,      // Roundabouts, intersections
        ESTATE,        // Residential estates
        LANDMARK,      // General landmarks
        DISTRICT,      // Area/district names
        MALL,          // Shopping centers
        HOSPITAL,      // Medical centers
        GOVERNMENT,    // Government buildings
        TRANSPORT,     // Bus stops, airports
        OTHER          // Miscellaneous
    }
    
    @NotNull
    @Size(min = 2, max = 200)
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private LandmarkCategory category;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @NotNull
    @Column(name = "location", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point location;
    
    @Column(name = "district", length = 100)
    private String district;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    /**
     * Array of lowercase search terms for fuzzy matching
     * Example: ["wuse market", "wuse zone 5", "wuse"]
     */
    @Column(name = "search_terms", columnDefinition = "text[]")
    private String[] searchTerms;
    
    /**
     * Track how often this landmark is selected by users
     * Incremented each time a driver selects this landmark
     */
    @Column(name = "popularity_score")
    private Integer popularityScore = 0;
    
    // Constructors
    public LandmarkLocation() {}
    
    public LandmarkLocation(String name, String category, Point location, String district) {
        this.name = name;
        this.category = LandmarkCategory.valueOf(category);
        this.location = location;
        this.district = district;
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public LandmarkCategory getCategory() { return category; }
    public void setCategory(LandmarkCategory category) { this.category = category; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Point getLocation() { return location; }
    public void setLocation(Point location) { this.location = location; }
    
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public String[] getSearchTerms() { return searchTerms; }
    public void setSearchTerms(String[] searchTerms) { this.searchTerms = searchTerms; }
    
    public Integer getPopularityScore() { return popularityScore; }
    public void setPopularityScore(Integer popularityScore) { 
        this.popularityScore = popularityScore; 
    }
    
    /**
     * Increment popularity when this landmark is used
     */
    public void incrementPopularity() {
        this.popularityScore = (this.popularityScore != null ? this.popularityScore : 0) + 1;
    }
    
    /**
     * Get coordinates as [latitude, longitude]
     */
    public double[] getCoordinates() {
        return new double[]{location.getY(), location.getX()};
    }
}
