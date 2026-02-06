package com.commute.metrosync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * OtpToken entity - One-Time Password tokens for phone verification
 * Supports SMS-based authentication and verification flows
 */
@Entity
@Table(name = "otp_tokens", indexes = {
    @Index(name = "idx_otp_tokens_phone_number", columnList = "phone_number"),
    @Index(name = "idx_otp_tokens_user_id", columnList = "user_id"),
    @Index(name = "idx_otp_tokens_code", columnList = "code"),
    @Index(name = "idx_otp_tokens_purpose", columnList = "purpose"),
    @Index(name = "idx_otp_tokens_expires_at", columnList = "expires_at"),
    @Index(name = "idx_otp_tokens_created_at", columnList = "created_at")
})
public class OtpToken extends BaseEntity {

    // User Information
    @Column(name = "phone_number", nullable = false, length = 15)
    private String phoneNumber;  // Can be used before user registration

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;  // NULL for pre-registration OTPs

    // OTP Details
    @Column(name = "code", nullable = false, length = 6)
    private String code;  // 6-digit numeric code

    // Purpose/Type
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 50)
    private OtpPurpose purpose;

    // Validation
    @Column(name = "is_used")
    private Boolean isUsed = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "attempts")
    private Integer attempts = 0;

    @Column(name = "max_attempts")
    private Integer maxAttempts = 3;

    // Expiry
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // IP and Device Info (security)
    @Column(name = "ip_address", length = 45)
    private String ipAddress;  // IPv6 compatible

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    // Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // Enums
    public enum OtpPurpose {
        REGISTRATION,
        LOGIN,
        PHONE_VERIFICATION,
        PASSWORD_RESET,
        TRANSACTION_VERIFICATION
    }

    // Business Methods
    
    /**
     * Generate a 6-digit OTP code
     */
    public static String generateCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    public boolean isUsed() {
        return Boolean.TRUE.equals(isUsed);
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isValid() {
        return !isUsed() && !isExpired() && attempts < maxAttempts;
    }

    public boolean hasExceededMaxAttempts() {
        return attempts >= maxAttempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void markAsUsed() {
        this.isUsed = true;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * Verify if the provided code matches and is valid
     */
    public boolean verify(String providedCode) {
        // Check if OTP is still valid
        if (!isValid()) {
            return false;
        }

        // Check if code matches
        if (!code.equals(providedCode)) {
            incrementAttempts();
            return false;
        }

        // Mark as used
        markAsUsed();
        return true;
    }

    // Getters and Setters
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public OtpPurpose getPurpose() { return purpose; }
    public void setPurpose(OtpPurpose purpose) { this.purpose = purpose; }

    public Boolean getIsUsed() { return isUsed; }
    public void setIsUsed(Boolean isUsed) { this.isUsed = isUsed; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}