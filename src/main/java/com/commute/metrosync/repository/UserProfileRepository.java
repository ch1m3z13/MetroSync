package com.commute.metrosync.repository;

import com.commute.metrosync.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserProfileRepository implements PanacheRepositoryBase<UserProfile, UUID> {

    public Optional<UserProfile> findByUserId(UUID userId) {
        return find("user.id", userId).firstResultOptional();
    }

    public Optional<UserProfile> findByNin(String nin) {
        return find("nin", nin).firstResultOptional();
    }

    public List<UserProfile> findPendingVerifications() {
        return find("verificationStatus", UserProfile.VerificationStatus.PENDING).list();
    }

    public List<UserProfile> findVerified() {
        return find("verificationStatus", UserProfile.VerificationStatus.VERIFIED).list();
    }

    public List<UserProfile> findByVerificationStatus(UserProfile.VerificationStatus status) {
        return find("verificationStatus", status).list();
    }

    public long countByVerificationStatus(UserProfile.VerificationStatus status) {
        return count("verificationStatus", status);
    }

    public List<UserProfile> findNinVerifiedWithoutSelfie() {
        return find("ninVerified = true AND selfieVerified = false").list();
    }

    public List<UserProfile> findSelfieVerifiedWithoutNin() {
        return find("selfieVerified = true AND ninVerified = false").list();
    }

    public boolean isNinInUse(String nin) {
        return count("nin = ?1", nin) > 0;
    }

    public boolean isNinInUseByOtherUser(String nin, UUID userId) {
        return count("nin = ?1 AND user.id != ?2", nin, userId) > 0;
    }

    public List<UserProfile> findByHomeCity(String city) {
        return find("homeCity", city).list();
    }

    public List<UserProfile> findByHomeState(String state) {
        return find("homeState", state).list();
    }
}