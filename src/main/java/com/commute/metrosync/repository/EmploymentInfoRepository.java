package com.commute.metrosync.repository;

import com.commute.metrosync.entity.EmploymentInfo;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class EmploymentInfoRepository implements PanacheRepositoryBase<EmploymentInfo, UUID> {

    @Inject
    EntityManager em;

    public Optional<EmploymentInfo> findByUserId(UUID userId) {
        return find("user.id", userId).firstResultOptional();
    }

    public Optional<EmploymentInfo> findByCompanyEmail(String companyEmail) {
        return find("companyEmail", companyEmail).firstResultOptional();
    }

    public List<EmploymentInfo> findByCompanyName(String companyName) {
        return find("companyName", companyName).list();
    }

    public long countByCompany(String companyName) {
        return count("companyName", companyName);
    }

    public List<EmploymentInfo> findPendingVerifications() {
        return find("verificationStatus", EmploymentInfo.VerificationStatus.PENDING).list();
    }

    public List<EmploymentInfo> findVerified() {
        return find("verificationStatus", EmploymentInfo.VerificationStatus.VERIFIED).list();
    }

    public List<EmploymentInfo> findByVerificationStatus(EmploymentInfo.VerificationStatus status) {
        return find("verificationStatus", status).list();
    }

    public long countByVerificationStatus(EmploymentInfo.VerificationStatus status) {
        return count("verificationStatus", status);
    }

    public List<EmploymentInfo> findWithMissingDocuments() {
        return find("idCardUrl IS NULL OR employmentLetterUrl IS NULL").list();
    }

    public List<EmploymentInfo> findByWorkCity(String city) {
        return find("workCity", city).list();
    }

    public List<EmploymentInfo> findByWorkState(String state) {
        return find("workState", state).list();
    }

    public boolean isCompanyEmailInUse(String companyEmail) {
        return count("companyEmail = ?1", companyEmail) > 0;
    }

    public boolean isCompanyEmailInUseByOtherUser(String companyEmail, UUID userId) {
        return count("companyEmail = ?1 AND user.id != ?2", companyEmail, userId) > 0;
    }

    public List<Object[]> getTopCompaniesByEmployeeCount(int limit) {
        return em.createQuery(
            "SELECT e.companyName, COUNT(e) FROM EmploymentInfo e " +
            "GROUP BY e.companyName ORDER BY COUNT(e) DESC",
            Object[].class
        )
        .setMaxResults(limit)
        .getResultList();
    }
}