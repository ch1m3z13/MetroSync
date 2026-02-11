package com.commute.metrosync.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Verification Service Interface  
 * Defines operations for user verification
 */
public interface VerificationService {
    
    /**
     * Submit identity verification
     */
    IdentityVerificationResponse submitIdentityVerification(
        UUID userId, IdentityVerificationRequest request
    );
    
    /**
     * Approve identity verification (admin)
     */
    void approveIdentityVerification(UUID userId, UUID adminId, String notes);
    
    /**
     * Reject identity verification (admin)
     */
    void rejectIdentityVerification(UUID userId, UUID adminId, String reason);
    
    /**
     * Submit employment verification
     */
    EmploymentVerificationResponse submitEmploymentVerification(
        UUID userId, EmploymentVerificationRequest request
    );
    
    /**
     * Submit driver documents
     */
    DriverDocumentsResponse submitDriverDocuments(
        UUID userId, DriverDocumentsRequest request
    );
    
    /**
     * Approve driver documents (admin)
     */
    void approveDriverDocuments(UUID userId, UUID adminId, String notes);
    
    /**
     * Reject driver documents (admin)
     */
    void rejectDriverDocuments(UUID userId, UUID adminId, String reason);
    
    /**
     * Get verification status
     */
    VerificationStatusResponse getVerificationStatus(UUID userId);
    
    /**
     * Check if user is fully verified
     */
    boolean isUserFullyVerified(UUID userId);
    
    // ==================== REQUEST/RESPONSE CLASSES ====================
    
    record IdentityVerificationRequest(
        String ninNumber,
        String selfieUrl,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String country,
        String postalCode
    ) {}
    
    record IdentityVerificationResponse(
        UUID verificationId,
        String status,
        String message,
        LocalDateTime submittedAt
    ) {}
    
    record EmploymentVerificationRequest(
        String companyName,
        String companyEmail,
        String jobTitle,
        String employmentType,
        LocalDate startDate,
        String workIdUrl
    ) {}
    
    record EmploymentVerificationResponse(
        UUID verificationId,
        String status,
        String message,
        LocalDateTime submittedAt
    ) {}
    
    record DriverDocumentsRequest(
        String licenseNumber,
        LocalDate licenseExpiryDate,
        String licenseUrl,
        String vehicleRegistrationNumber,
        String registrationUrl,
        String insurancePolicyNumber,
        LocalDate insuranceExpiryDate,
        String insuranceUrl,
        String roadworthinessCertNumber,
        LocalDate roadworthinessExpiryDate,
        String roadworthinessUrl
    ) {}
    
    record DriverDocumentsResponse(
        UUID documentId,
        String status,
        String message,
        LocalDateTime submittedAt
    ) {}
    
    record VerificationStatusResponse(
        boolean ninVerified,
        boolean selfieVerified,
        boolean employmentVerified,
        boolean driverDocumentsVerified,
        boolean fullyVerified,
        String profileStatus,
        String documentStatus
    ) {}
}