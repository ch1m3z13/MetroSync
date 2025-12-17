package com.commute.metrosync.entity;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a vehicle owned by a driver
 */
@Entity
@Table(name = "vehicles", indexes = {
    @Index(name = "idx_vehicle_owner", columnList = "owner_id"),
    @Index(name = "idx_vehicle_plate", columnList = "license_plate", unique = true),
    @Index(name = "idx_vehicle_active", columnList = "is_active")
})
public class Vehicle extends BaseEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    
    @NotNull
    @Size(min = 2, max = 50)
    @Column(name = "make", nullable = false, length = 50)
    private String make;
    
    @NotNull
    @Size(min = 2, max = 50)
    @Column(name = "model", nullable = false, length = 50)
    private String model;
    
    @NotNull
    @Column(name = "year", nullable = false)
    private Integer year;
    
    @NotNull
    @Column(name = "color", nullable = false, length = 30)
    private String color;
    
    @NotNull
    @Size(min = 3, max = 20)
    @Column(name = "license_plate", nullable = false, unique = true, length = 20)
    private String licensePlate;
    
    @NotNull
    @Column(name = "capacity", nullable = false)
    private Integer capacity; // Number of passengers
    
    @Column(name = "vehicle_image_url", length = 500)
    private String vehicleImageUrl;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 20)
    private VehicleType vehicleType = VehicleType.SEDAN;
    
    // Constructors
    public Vehicle() {}
    
    public Vehicle(User owner, String make, String model, Integer year, 
                   String color, String licensePlate, Integer capacity) {
        this.owner = owner;
        this.make = make;
        this.model = model;
        this.year = year;
        this.color = color;
        this.licensePlate = licensePlate;
        this.capacity = capacity;
    }
    
    // Getters and Setters
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    
    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    
    public String getVehicleImageUrl() { return vehicleImageUrl; }
    public void setVehicleImageUrl(String vehicleImageUrl) { 
        this.vehicleImageUrl = vehicleImageUrl; 
    }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public Boolean getIsVerified() { return isVerified; }
    public void setIsVerified(Boolean isVerified) { this.isVerified = isVerified; }
    
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    
    public String getDisplayName() {
        return String.format("%s %s %s (%s)", year, make, model, licensePlate);
    }
}
