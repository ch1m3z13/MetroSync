package com.commute.metrosync.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DriverDocuments entity - Driver license, vehicle registration, insurance
 * Links to User table (one-to-one relationship)
 * Required for drivers only
 */
@Entity
@Table(name = "driver_documents", indexes = {
    @Index(name = "idx_driver_documents_user_id", columnList = "user_id"),
    @Index(name = "idx_driver_documents_license", columnList = "license_number"),
    @Index(name = "idx_driver_documents_verification_status", columnList = "verification_status"),
    @Index(name = "idx_driver_documents_license_expiry", columnList = "license_expiry_date"),
    @Index(name = "idx_driver_documents_insurance_expiry", columnList = "insurance_expiry_date")
})
public class DriverDocuments extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Driver's License
    @Column(name = "license_number", nullable = false, length = 50)
    private String licenseNumber;

    @Column(name = "license_expiry_date", nullable = false)
    private LocalDate licenseExpiryDate;

    @Column(name = "license_front_url", length = 500)
    private String licenseFrontUrl;

    @Column(name = "license_back_url", length = 500)
    private String licenseBackUrl;

    @Column(name = "license_verified")
    private Boolean licenseVerified = false;

    @Column(name = "license_verified_at")
    private LocalDateTime licenseVerifiedAt;

    // Vehicle Registration
    @Column(name = "vehicle_registration_number", length = 50)
    private String vehicleRegistrationNumber;

    @Column(name = "vehicle_registration_url", length = 500)
    private String vehicleRegistrationUrl;

    @Column(name = "vehicle_registration_verified")
    private Boolean vehicleRegistrationVerified = false;

    @Column(name = "vehicle_registration_verified_at")
    private LocalDateTime vehicleRegistrationVerifiedAt;

    // Vehicle Insurance
    @Column(name = "insurance_provider", length = 150)
    private String insuranceProvider;

    @Column(name = "insurance_policy_number", length = 100)
    private String insurancePolicyNumber;

    @Column(name = "insurance_expiry_date")
    private LocalDate insuranceExpiryDate;

    @Column(name = "insurance_document_url", length = 500)
    private String insuranceDocumentUrl;

    @Column(name = "insurance_verified")
    private Boolean insuranceVerified = false;

    @Column(name = "insurance_verified_at")
    private LocalDateTime insuranceVerifiedAt;

    // Vehicle Photos
    @Column(name = "vehicle_front_photo_url", length = 500)
    private String vehicleFrontPhotoUrl;

    @Column(name = "vehicle_back_photo_url", length = 500)
    private String vehicleBackPhotoUrl;

    @Column(name = "vehicle_side_photo_url", length = 500)
    private String vehicleSidePhotoUrl;

    @Column(name = "vehicle_interior_photo_url", length = 500)
    private String vehicleInteriorPhotoUrl;

    // Background Check
    @Column(name = "police_clearance_url", length = 500)
    private String policeClearanceUrl;

    @Column(name = "police_clearance_verified")
    private Boolean policeClearanceVerified = false;

    @Column(name = "police_clearance_verified_at")
    private LocalDateTime policeClearanceVerifiedAt;

    // Overall Verification Status
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // Enum
    public enum VerificationStatus {
        PENDING, VERIFIED, REJECTED
    }

    // Business Methods
    public boolean isLicenseVerified() {
        return Boolean.TRUE.equals(licenseVerified);
    }

    public boolean isVehicleRegistrationVerified() {
        return Boolean.TRUE.equals(vehicleRegistrationVerified);
    }

    public boolean isInsuranceVerified() {
        return Boolean.TRUE.equals(insuranceVerified);
    }

    public boolean isPoliceClearanceVerified() {
        return Boolean.TRUE.equals(policeClearanceVerified);
    }

    public boolean isAllVerified() {
        return isLicenseVerified()
                && isVehicleRegistrationVerified()
                && isInsuranceVerified()
                && isPoliceClearanceVerified();
    }

    public boolean isLicenseExpired() {
        return licenseExpiryDate != null && licenseExpiryDate.isBefore(LocalDate.now());
    }

    public boolean isInsuranceExpired() {
        return insuranceExpiryDate != null && insuranceExpiryDate.isBefore(LocalDate.now());
    }

    public boolean hasExpiredDocuments() {
        return isLicenseExpired() || isInsuranceExpired();
    }

    public boolean hasLicensePhotos() {
        return licenseFrontUrl != null && licenseBackUrl != null;
    }

    public boolean hasVehiclePhotos() {
        return vehicleFrontPhotoUrl != null
                && vehicleBackPhotoUrl != null
                && vehicleSidePhotoUrl != null
                && vehicleInteriorPhotoUrl != null;
    }

    public boolean hasAllRequiredDocuments() {
        return hasLicensePhotos()
                && vehicleRegistrationUrl != null
                && insuranceDocumentUrl != null
                && policeClearanceUrl != null
                && hasVehiclePhotos();
    }

    // Getters and Setters
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public LocalDate getLicenseExpiryDate() { return licenseExpiryDate; }
    public void setLicenseExpiryDate(LocalDate licenseExpiryDate) { 
        this.licenseExpiryDate = licenseExpiryDate; 
    }

    public String getLicenseFrontUrl() { return licenseFrontUrl; }
    public void setLicenseFrontUrl(String licenseFrontUrl) { 
        this.licenseFrontUrl = licenseFrontUrl; 
    }

    public String getLicenseBackUrl() { return licenseBackUrl; }
    public void setLicenseBackUrl(String licenseBackUrl) { 
        this.licenseBackUrl = licenseBackUrl; 
    }

    public Boolean getLicenseVerified() { return licenseVerified; }
    public void setLicenseVerified(Boolean licenseVerified) { 
        this.licenseVerified = licenseVerified; 
    }

    public LocalDateTime getLicenseVerifiedAt() { return licenseVerifiedAt; }
    public void setLicenseVerifiedAt(LocalDateTime licenseVerifiedAt) { 
        this.licenseVerifiedAt = licenseVerifiedAt; 
    }

    public String getVehicleRegistrationNumber() { return vehicleRegistrationNumber; }
    public void setVehicleRegistrationNumber(String vehicleRegistrationNumber) { 
        this.vehicleRegistrationNumber = vehicleRegistrationNumber; 
    }

    public String getVehicleRegistrationUrl() { return vehicleRegistrationUrl; }
    public void setVehicleRegistrationUrl(String vehicleRegistrationUrl) { 
        this.vehicleRegistrationUrl = vehicleRegistrationUrl; 
    }

    public Boolean getVehicleRegistrationVerified() { return vehicleRegistrationVerified; }
    public void setVehicleRegistrationVerified(Boolean vehicleRegistrationVerified) { 
        this.vehicleRegistrationVerified = vehicleRegistrationVerified; 
    }

    public LocalDateTime getVehicleRegistrationVerifiedAt() { 
        return vehicleRegistrationVerifiedAt; 
    }
    public void setVehicleRegistrationVerifiedAt(LocalDateTime vehicleRegistrationVerifiedAt) { 
        this.vehicleRegistrationVerifiedAt = vehicleRegistrationVerifiedAt; 
    }

    public String getInsuranceProvider() { return insuranceProvider; }
    public void setInsuranceProvider(String insuranceProvider) { 
        this.insuranceProvider = insuranceProvider; 
    }

    public String getInsurancePolicyNumber() { return insurancePolicyNumber; }
    public void setInsurancePolicyNumber(String insurancePolicyNumber) { 
        this.insurancePolicyNumber = insurancePolicyNumber; 
    }

    public LocalDate getInsuranceExpiryDate() { return insuranceExpiryDate; }
    public void setInsuranceExpiryDate(LocalDate insuranceExpiryDate) { 
        this.insuranceExpiryDate = insuranceExpiryDate; 
    }

    public String getInsuranceDocumentUrl() { return insuranceDocumentUrl; }
    public void setInsuranceDocumentUrl(String insuranceDocumentUrl) { 
        this.insuranceDocumentUrl = insuranceDocumentUrl; 
    }

    public Boolean getInsuranceVerified() { return insuranceVerified; }
    public void setInsuranceVerified(Boolean insuranceVerified) { 
        this.insuranceVerified = insuranceVerified; 
    }

    public LocalDateTime getInsuranceVerifiedAt() { return insuranceVerifiedAt; }
    public void setInsuranceVerifiedAt(LocalDateTime insuranceVerifiedAt) { 
        this.insuranceVerifiedAt = insuranceVerifiedAt; 
    }

    public String getVehicleFrontPhotoUrl() { return vehicleFrontPhotoUrl; }
    public void setVehicleFrontPhotoUrl(String vehicleFrontPhotoUrl) { 
        this.vehicleFrontPhotoUrl = vehicleFrontPhotoUrl; 
    }

    public String getVehicleBackPhotoUrl() { return vehicleBackPhotoUrl; }
    public void setVehicleBackPhotoUrl(String vehicleBackPhotoUrl) { 
        this.vehicleBackPhotoUrl = vehicleBackPhotoUrl; 
    }

    public String getVehicleSidePhotoUrl() { return vehicleSidePhotoUrl; }
    public void setVehicleSidePhotoUrl(String vehicleSidePhotoUrl) { 
        this.vehicleSidePhotoUrl = vehicleSidePhotoUrl; 
    }

    public String getVehicleInteriorPhotoUrl() { return vehicleInteriorPhotoUrl; }
    public void setVehicleInteriorPhotoUrl(String vehicleInteriorPhotoUrl) { 
        this.vehicleInteriorPhotoUrl = vehicleInteriorPhotoUrl; 
    }

    public String getPoliceClearanceUrl() { return policeClearanceUrl; }
    public void setPoliceClearanceUrl(String policeClearanceUrl) { 
        this.policeClearanceUrl = policeClearanceUrl; 
    }

    public Boolean getPoliceClearanceVerified() { return policeClearanceVerified; }
    public void setPoliceClearanceVerified(Boolean policeClearanceVerified) { 
        this.policeClearanceVerified = policeClearanceVerified; 
    }

    public LocalDateTime getPoliceClearanceVerifiedAt() { return policeClearanceVerifiedAt; }
    public void setPoliceClearanceVerifiedAt(LocalDateTime policeClearanceVerifiedAt) { 
        this.policeClearanceVerifiedAt = policeClearanceVerifiedAt; 
    }

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

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
}