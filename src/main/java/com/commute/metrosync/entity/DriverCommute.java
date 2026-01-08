package com.commute.metrosync.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.locationtech.jts.geom.Point;

import java.time.LocalTime;

/**
 * Stores a driver's daily commute information.
 * This is set up once during onboarding and used to auto-generate routes.
 */
@Entity
@Table(name = "driver_commutes", indexes = {
    @Index(name = "idx_commute_driver", columnList = "driver_id", unique = true),
    @Index(name = "idx_commute_home_location", columnList = "home_location"),
    @Index(name = "idx_commute_work_location", columnList = "work_location")
})
public class DriverCommute extends BaseEntity {
    
    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false, unique = true)
    private User driver;
    
    // ==================== HOME ADDRESS ====================
    
    @NotNull
    @Size(min = 5, max = 500)
    @Column(name = "home_address", nullable = false, length = 500)
    private String homeAddress;
    
    @NotNull
    @Column(name = "home_location", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point homeLocation;
    
    // ==================== WORK ADDRESS ====================
    
    @NotNull
    @Size(min = 5, max = 500)
    @Column(name = "work_address", nullable = false, length = 500)
    private String workAddress;
    
    @NotNull
    @Column(name = "work_location", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point workLocation;
    
    // ==================== SCHEDULE ====================
    
    /**
     * Time driver leaves home for work (e.g., "08:00")
     */
    @NotNull
    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;
    
    /**
     * Time driver leaves work for home (e.g., "17:00")
     */
    @NotNull
    @Column(name = "return_time", nullable = false)
    private LocalTime returnTime;
    
    /**
     * Maximum number of passengers (excluding driver)
     */
    @NotNull
    @Min(1)
    @Max(20)
    @Column(name = "capacity", nullable = false)
    private Integer capacity;
    
    /**
     * Pre-calculated distance between home and work (in km)
     */
    @Column(name = "commute_distance_km")
    private Double commuteDistanceKm;
    
    /**
     * Is this commute currently active/enabled?
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    // ==================== CONSTRUCTORS ====================
    
    public DriverCommute() {}
    
    public DriverCommute(User driver, String homeAddress, Point homeLocation,
                        String workAddress, Point workLocation,
                        LocalTime departureTime, LocalTime returnTime, Integer capacity) {
        this.driver = driver;
        this.homeAddress = homeAddress;
        this.homeLocation = homeLocation;
        this.workAddress = workAddress;
        this.workLocation = workLocation;
        this.departureTime = departureTime;
        this.returnTime = returnTime;
        this.capacity = capacity;
    }
    
    // ==================== GETTERS AND SETTERS ====================
    
    public User getDriver() { return driver; }
    public void setDriver(User driver) { this.driver = driver; }
    
    public String getHomeAddress() { return homeAddress; }
    public void setHomeAddress(String homeAddress) { this.homeAddress = homeAddress; }
    
    public Point getHomeLocation() { return homeLocation; }
    public void setHomeLocation(Point homeLocation) { this.homeLocation = homeLocation; }
    
    public String getWorkAddress() { return workAddress; }
    public void setWorkAddress(String workAddress) { this.workAddress = workAddress; }
    
    public Point getWorkLocation() { return workLocation; }
    public void setWorkLocation(Point workLocation) { this.workLocation = workLocation; }
    
    public LocalTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalTime departureTime) { this.departureTime = departureTime; }
    
    public LocalTime getReturnTime() { return returnTime; }
    public void setReturnTime(LocalTime returnTime) { this.returnTime = returnTime; }
    
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    
    public Double getCommuteDistanceKm() { return commuteDistanceKm; }
    public void setCommuteDistanceKm(Double commuteDistanceKm) { 
        this.commuteDistanceKm = commuteDistanceKm; 
    }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Get home coordinates as [longitude, latitude]
     */
    public double[] getHomeCoordinates() {
        return new double[]{homeLocation.getX(), homeLocation.getY()};
    }
    
    /**
     * Get work coordinates as [longitude, latitude]
     */
    public double[] getWorkCoordinates() {
        return new double[]{workLocation.getX(), workLocation.getY()};
    }
    
    /**
     * Check if driver should be heading to work at given time
     */
    public boolean isToWorkTime(LocalTime currentTime) {
        // Simple logic: before 12pm = going to work
        return currentTime.isBefore(LocalTime.NOON);
    }
}