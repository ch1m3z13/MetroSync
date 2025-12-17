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
 * Represents a user in the system (can be Driver, Rider, or both).
 * Integrates with Quarkus Security JPA for authentication.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_phone", columnList = "phone_number", unique = true),
    @Index(name = "idx_user_active", columnList = "is_active"),
    @Index(name = "idx_user_current_location", columnList = "current_location")
})
@UserDefinition
public class User extends BaseEntity {
    
    @Username
    @NotNull
    @Size(min = 3, max = 50)
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;
    
    @Password
    @NotNull
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Roles
    @Column(name = "roles", nullable = false)
    private String roles = "RIDER"; // Default role
    
    @NotNull
    @Size(min = 2, max = 100)
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;
    
    @NotNull
    @Email
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;
    
    @NotNull
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;
    
    /**
     * User's current location (updated when app is active)
     */
    @Column(name = "current_location", columnDefinition = "geometry(Point,4326)")
    private Point currentLocation;
    
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;
    
    /**
     * Average rating (1.0 to 5.0)
     */
    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.ZERO;
    
    @Column(name = "total_ratings")
    private Integer totalRatings = 0;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;
    
    @Column(name = "verification_token", length = 100)
    private String verificationToken;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    // Relationships
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Vehicle> vehicles = new ArrayList<>();
    
    @OneToMany(mappedBy = "rider", cascade = CascadeType.ALL)
    private List<Booking> bookingsAsRider = new ArrayList<>();
    
    // Constructors
    public User() {}
    
    public User(String username, String password, String fullName, 
                String email, String phoneNumber) {
        this.username = username;
        this.passwordHash = BcryptUtil.bcryptHash(password);
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }
    
    // Helper methods
    public void addRole(UserRole role) {
        Set<String> roleSet = new HashSet<>(Set.of(roles.split(",")));
        roleSet.add(role.name());
        this.roles = String.join(",", roleSet);
    }
    
    public boolean hasRole(UserRole role) {
        return roles.contains(role.name());
    }
    
    public boolean isDriver() {
        return hasRole(UserRole.DRIVER);
    }
    
    public boolean isRider() {
        return hasRole(UserRole.RIDER);
    }
    
    public void updateRating(int newRating) {
        if (newRating < 1 || newRating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        
        BigDecimal totalPoints = rating.multiply(BigDecimal.valueOf(totalRatings));
        totalPoints = totalPoints.add(BigDecimal.valueOf(newRating));
        totalRatings++;
        rating = totalPoints.divide(BigDecimal.valueOf(totalRatings), 2, java.math.RoundingMode.HALF_UP);
    }
    
    public void addVehicle(Vehicle vehicle) {
        vehicles.add(vehicle);
        vehicle.setOwner(this);
    }
    
    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    
    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public Point getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Point currentLocation) { this.currentLocation = currentLocation; }
    
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    
    public Integer getTotalRatings() { return totalRatings; }
    public void setTotalRatings(Integer totalRatings) { this.totalRatings = totalRatings; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public Boolean getIsVerified() { return isVerified; }
    public void setIsVerified(Boolean isVerified) { this.isVerified = isVerified; }
    
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { 
        this.verificationToken = verificationToken; 
    }
    
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
    
    public List<Vehicle> getVehicles() { return vehicles; }
    public void setVehicles(List<Vehicle> vehicles) { this.vehicles = vehicles; }
    
    public List<Booking> getBookingsAsRider() { return bookingsAsRider; }
    public void setBookingsAsRider(List<Booking> bookingsAsRider) { 
        this.bookingsAsRider = bookingsAsRider; 
    }
}
