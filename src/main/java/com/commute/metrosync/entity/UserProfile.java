package com.commute.metrosync.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Past;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UserProfile entity - Identity verification and personal information
 * Links to User table (one-to-one relationship)
 */
@Entity
@Table(name = "user_profiles", indexes = {
    @Index(name = "idx_user_profiles_user_id", columnList = "user_id"),
    @Index(name = "idx_user_profiles_nin", columnList = "nin"),
    @Index(name = "idx_user_profiles_verification_status", columnList = "verification_status")
})
public class UserProfile extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Identity Information
    @Column(name = "nin", length = 11)
    private String nin;  // Nigerian National ID Number

    @Column(name = "nin_verified")
    private Boolean ninVerified = false;

    @Column(name = "nin_verified_at")
    private LocalDateTime ninVerifiedAt;

    // Selfie Verification
    @Column(name = "selfie_url", length = 500)
    private String selfieUrl;

    @Column(name = "selfie_verified")
    private Boolean selfieVerified = false;

    @Column(name = "selfie_verified_at")
    private LocalDateTime selfieVerifiedAt;

    // Personal Details
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Past
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    // Address
    @Column(name = "home_address", columnDefinition = "TEXT")
    private String homeAddress;

    @Column(name = "home_city", length = 100)
    private String homeCity;

    @Column(name = "home_state", length = 50)
    private String homeState;

    @Column(name = "home_location", columnDefinition = "geometry(Point,4326)")
    private Point homeLocation;  // PostGIS point

    // Verification Status
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;  // Admin who verified

    // Enums
    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum VerificationStatus {
        PENDING, VERIFIED, REJECTED
    }

    // Business Methods
    public boolean isSelfieVerified() {
        return Boolean.TRUE.equals(selfieVerified);
    }

    public boolean isNinVerified() {
        return Boolean.TRUE.equals(ninVerified);
    }

    public boolean isFullyVerified() {
        return verificationStatus == VerificationStatus.VERIFIED
                && isSelfieVerified()
                && isNinVerified();
    }

    public String getFullName() {
        if (firstName == null && lastName == null) {
            return null;
        }
        return String.format("%s %s", 
            firstName != null ? firstName : "", 
            lastName != null ? lastName : "").trim();
    }

    // Getters and Setters
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getNin() { return nin; }
    public void setNin(String nin) { this.nin = nin; }

    public Boolean getNinVerified() { return ninVerified; }
    public void setNinVerified(Boolean ninVerified) { this.ninVerified = ninVerified; }

    public LocalDateTime getNinVerifiedAt() { return ninVerifiedAt; }
    public void setNinVerifiedAt(LocalDateTime ninVerifiedAt) { 
        this.ninVerifiedAt = ninVerifiedAt; 
    }

    public String getSelfieUrl() { return selfieUrl; }
    public void setSelfieUrl(String selfieUrl) { this.selfieUrl = selfieUrl; }

    public Boolean getSelfieVerified() { return selfieVerified; }
    public void setSelfieVerified(Boolean selfieVerified) { 
        this.selfieVerified = selfieVerified; 
    }

    public LocalDateTime getSelfieVerifiedAt() { return selfieVerifiedAt; }
    public void setSelfieVerifiedAt(LocalDateTime selfieVerifiedAt) { 
        this.selfieVerifiedAt = selfieVerifiedAt; 
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getHomeAddress() { return homeAddress; }
    public void setHomeAddress(String homeAddress) { this.homeAddress = homeAddress; }

    public String getHomeCity() { return homeCity; }
    public void setHomeCity(String homeCity) { this.homeCity = homeCity; }

    public String getHomeState() { return homeState; }
    public void setHomeState(String homeState) { this.homeState = homeState; }

    public Point getHomeLocation() { return homeLocation; }
    public void setHomeLocation(Point homeLocation) { this.homeLocation = homeLocation; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { 
        this.verificationStatus = verificationStatus; 
    }

    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String verificationNotes) { 
        this.verificationNotes = verificationNotes; 
    }

    public User getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(User verifiedBy) { this.verifiedBy = verifiedBy; }
}