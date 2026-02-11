package com.commute.metrosync.service;

import java.time.LocalDateTime;

/**
 * OTP Service Interface
 * Defines OTP operations
 */
public interface OtpService {
    
    /**
     * Send OTP
     */
    SendOtpResponse sendOtp(String phoneNumber, String purpose, String ipAddress, String userAgent);
    
    /**
     * Verify OTP
     */
    VerifyOtpResponse verifyOtp(String phoneNumber, String otpCode, String purpose);
    
    // ==================== RESPONSE CLASSES ====================
    
    record SendOtpResponse(
        boolean success,
        String message,
        String otpId,
        LocalDateTime expiresAt
    ) {}
    
    record VerifyOtpResponse(
        boolean success,
        boolean verified,
        String message
    ) {}
}