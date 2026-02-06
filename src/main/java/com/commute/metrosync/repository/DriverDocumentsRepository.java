package com.commute.metrosync.repository;

import com.commute.metrosync.entity.DriverDocuments;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DriverDocumentsRepository implements PanacheRepositoryBase<DriverDocuments, UUID> {

    public Optional<DriverDocuments> findByUserId(UUID userId) {
        return find("user.id", userId).firstResultOptional();
    }

    public Optional<DriverDocuments> findByLicenseNumber(String licenseNumber) {
        return find("licenseNumber", licenseNumber).firstResultOptional();
    }

    public List<DriverDocuments> findPendingVerifications() {
        return find("verificationStatus", DriverDocuments.VerificationStatus.PENDING).list();
    }

    public List<DriverDocuments> findVerified() {
        return find("verificationStatus", DriverDocuments.VerificationStatus.VERIFIED).list();
    }

    public List<DriverDocuments> findByVerificationStatus(DriverDocuments.VerificationStatus status) {
        return find("verificationStatus", status).list();
    }

    public long countByVerificationStatus(DriverDocuments.VerificationStatus status) {
        return count("verificationStatus", status);
    }

    public List<DriverDocuments> findExpiredLicenses() {
        return find("licenseExpiryDate < ?1", LocalDate.now()).list();
    }

    public List<DriverDocuments> findLicensesExpiringSoon(int days) {
        LocalDate futureDate = LocalDate.now().plusDays(days);
        return find("licenseExpiryDate BETWEEN ?1 AND ?2", LocalDate.now(), futureDate).list();
    }

    public List<DriverDocuments> findExpiredInsurance() {
        return find("insuranceExpiryDate < ?1", LocalDate.now()).list();
    }

    public List<DriverDocuments> findInsuranceExpiringSoon(int days) {
        LocalDate futureDate = LocalDate.now().plusDays(days);
        return find("insuranceExpiryDate BETWEEN ?1 AND ?2", LocalDate.now(), futureDate).list();
    }

    public List<DriverDocuments> findWithExpiredDocuments() {
        LocalDate today = LocalDate.now();
        return find("licenseExpiryDate < ?1 OR insuranceExpiryDate < ?1", today).list();
    }

    public List<DriverDocuments> findWithMissingPhotos() {
        return find(
            "licenseFrontUrl IS NULL OR licenseBackUrl IS NULL OR " +
            "vehicleFrontPhotoUrl IS NULL OR vehicleBackPhotoUrl IS NULL OR " +
            "vehicleSidePhotoUrl IS NULL OR vehicleInteriorPhotoUrl IS NULL"
        ).list();
    }

    public List<DriverDocuments> findFullyVerified() {
        return find(
            "licenseVerified = true AND vehicleRegistrationVerified = true AND " +
            "insuranceVerified = true AND policeClearanceVerified = true"
        ).list();
    }

    public boolean isLicenseNumberInUse(String licenseNumber) {
        return count("licenseNumber = ?1", licenseNumber) > 0;
    }

    public boolean isLicenseNumberInUseByOtherUser(String licenseNumber, UUID userId) {
        return count("licenseNumber = ?1 AND user.id != ?2", licenseNumber, userId) > 0;
    }

    public long countExpiredLicenses() {
        return count("licenseExpiryDate < ?1", LocalDate.now());
    }

    public long countExpiredInsurance() {
        return count("insuranceExpiryDate < ?1", LocalDate.now());
    }
}