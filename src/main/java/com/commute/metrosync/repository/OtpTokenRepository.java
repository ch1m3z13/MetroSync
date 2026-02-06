package com.commute.metrosync.repository;

import com.commute.metrosync.entity.OtpToken;
import com.commute.metrosync.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OtpTokenRepository implements PanacheRepositoryBase<OtpToken, UUID> {

    @Inject
    EntityManager em;

    public Optional<OtpToken> findActiveByPhoneAndPurpose(
        String phoneNumber,
        OtpToken.OtpPurpose purpose
    ) {
        return find(
            "phoneNumber = ?1 AND purpose = ?2 AND isUsed = false AND expiresAt > ?3 " +
            "ORDER BY createdAt DESC",
            phoneNumber,
            purpose,
            LocalDateTime.now()
        ).firstResultOptional();
    }

    public Optional<OtpToken> findByPhoneCodeAndPurpose(
        String phoneNumber,
        String code,
        OtpToken.OtpPurpose purpose
    ) {
        return find(
            "phoneNumber = ?1 AND code = ?2 AND purpose = ?3 AND isUsed = false " +
            "ORDER BY createdAt DESC",
            phoneNumber,
            code,
            purpose
        ).firstResultOptional();
    }

    public List<OtpToken> findByPhoneNumber(String phoneNumber) {
        return find("phoneNumber = ?1 ORDER BY createdAt DESC", phoneNumber).list();
    }

    public List<OtpToken> findByUserId(UUID userId) {
        return find("user.id = ?1 ORDER BY createdAt DESC", userId).list();
    }

    public List<OtpToken> findActive() {
        return find("isUsed = false AND expiresAt > ?1", LocalDateTime.now()).list();
    }

    public List<OtpToken> findExpired() {
        return find("expiresAt < ?1", LocalDateTime.now()).list();
    }

    public List<OtpToken> findUsed() {
        return find("isUsed = true").list();
    }

    public List<OtpToken> findByPurpose(OtpToken.OtpPurpose purpose) {
        return find("purpose = ?1 ORDER BY createdAt DESC", purpose).list();
    }

    public long countRecentByPhoneNumber(String phoneNumber, int minutes) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(minutes);
        return count("phoneNumber = ?1 AND createdAt > ?2", phoneNumber, cutoffTime);
    }

    public boolean hasExceededRateLimit(String phoneNumber, int maxOtpsPerHour) {
        long count = countRecentByPhoneNumber(phoneNumber, 60);
        return count >= maxOtpsPerHour;
    }

    @Transactional
    public OtpToken createToken(
        String phoneNumber,
        User user,
        OtpToken.OtpPurpose purpose,
        int validityMinutes,
        String ipAddress,
        String userAgent,
        String deviceId
    ) {
        // Invalidate existing active OTPs for same phone + purpose
        em.createQuery(
            "UPDATE OtpToken o SET o.isUsed = true, o.usedAt = :now " +
            "WHERE o.phoneNumber = :phone AND o.purpose = :purpose " +
            "AND o.isUsed = false AND o.expiresAt > :now")
            .setParameter("now", LocalDateTime.now())
            .setParameter("phone", phoneNumber)
            .setParameter("purpose", purpose)
            .executeUpdate();

        // Create new OTP
        OtpToken token = new OtpToken();
        token.setPhoneNumber(phoneNumber);
        token.setUser(user);
        token.setPurpose(purpose);
        token.setCode(OtpToken.generateCode());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(validityMinutes));
        token.setIpAddress(ipAddress);
        token.setUserAgent(userAgent);
        token.setDeviceId(deviceId);

        persist(token);
        return token;
    }

    @Transactional
    public OtpVerificationResult verifyOtp(
        String phoneNumber,
        String code,
        OtpToken.OtpPurpose purpose
    ) {
        Optional<OtpToken> tokenOpt = findByPhoneCodeAndPurpose(phoneNumber, code, purpose);

        if (tokenOpt.isEmpty()) {
            return new OtpVerificationResult(false, null, "Invalid OTP code");
        }

        OtpToken token = tokenOpt.get();

        if (token.isExpired()) {
            return new OtpVerificationResult(false, token.getUser(), "OTP has expired");
        }

        if (token.hasExceededMaxAttempts()) {
            return new OtpVerificationResult(
                false,
                token.getUser(),
                "Maximum verification attempts exceeded"
            );
        }

        if (!token.verify(code)) {
            persist(token);  // Save incremented attempts
            return new OtpVerificationResult(false, token.getUser(), "Invalid OTP code");
        }

        persist(token);  // Save marked as used
        return new OtpVerificationResult(true, token.getUser(), "OTP verified successfully");
    }

    @Transactional
    public boolean incrementAttempts(String phoneNumber, String code, OtpToken.OtpPurpose purpose) {
        Optional<OtpToken> tokenOpt = findByPhoneCodeAndPurpose(phoneNumber, code, purpose);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        OtpToken token = tokenOpt.get();
        token.incrementAttempts();
        persist(token);
        return true;
    }

    @Transactional
    public long deleteExpired() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusHours(24);
        return delete("expiresAt < ?1", cutoffDate);
    }

    @Transactional
    public long deleteOldUsed(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        return delete("isUsed = true AND createdAt < ?1", cutoffDate);
    }

    public OtpStatistics getStatistics() {
        long totalOtps = count();
        long activeOtps = count("isUsed = false AND expiresAt > ?1", LocalDateTime.now());
        long usedOtps = count("isUsed", true);
        long expiredOtps = count("expiresAt < ?1", LocalDateTime.now());

        return new OtpStatistics(totalOtps, activeOtps, usedOtps, expiredOtps);
    }

    public static class OtpVerificationResult {
        public final boolean isValid;
        public final User user;
        public final String message;

        public OtpVerificationResult(boolean isValid, User user, String message) {
            this.isValid = isValid;
            this.user = user;
            this.message = message;
        }
    }

    public static class OtpStatistics {
        public final long totalOtps;
        public final long activeOtps;
        public final long usedOtps;
        public final long expiredOtps;

        public OtpStatistics(long totalOtps, long activeOtps, long usedOtps, long expiredOtps) {
            this.totalOtps = totalOtps;
            this.activeOtps = activeOtps;
            this.usedOtps = usedOtps;
            this.expiredOtps = expiredOtps;
        }
    }
}