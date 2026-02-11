package com.commute.metrosync.service;

import com.commute.metrosync.dto.LoginDTO;
import com.commute.metrosync.dto.RegisterDTO;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Auth Service Interface
 * Defines authentication and user management operations
 */
public interface AuthService {
    
    /**
     * Register with OTP
     */
    AuthResponse registerWithOtp(RegisterRequest request);
    
    /**
     * Check if phone number is registered
     */
    boolean isPhoneNumberRegistered(String phoneNumber);
    
    /**
     * Check if email is registered
     */
    boolean isEmailRegistered(String email);
    
    /**
     * Login with credentials
     */
    AuthResponse login(LoginRequest request);
    
    /**
     * Login with OTP
     */
    AuthResponse loginWithOtp(String phoneNumber);
    
    /**
     * Reset password
     */
    void resetPassword(String phoneNumber, String newPassword);
    
    /**
     * Verify phone number
     */
    void verifyPhoneNumber(UUID userId, String phoneNumber);
    
    /**
     * Refresh access token
     */
    RefreshTokenResponse refreshToken(String refreshToken);
    
    /**
     * Logout (invalidate refresh token)
     */
    void logout(String refreshToken);
    
    // ==================== REQUEST/RESPONSE CLASSES ====================
    
    record RegisterRequest(
        String fullName,
        String email,
        String phoneNumber,
        String password,
        Set<String> roles
    ) {
        // Constructor that accepts RegisterDTO
        public static RegisterRequest from(RegisterDTO dto) {
            return new RegisterRequest(
                dto.getFullName(),
                dto.getEmail(),
                dto.getPhoneNumber(),
                dto.getPassword(),
                dto.getRoles()
            );
        }
    }
    
    record LoginRequest(
        String emailOrPhone,
        String password
    ) {
        // Constructor that accepts LoginDTO
        public static LoginRequest from(LoginDTO dto) {
            return new LoginRequest(
                dto.getEmailOrPhone(),
                dto.getPassword()
            );
        }
    }
    
    record AuthResponse(
        boolean success,
        String accessToken,
        String refreshToken,
        UUID userId,
        String email,
        String fullName,
        Set<String> roles,
        LocalDateTime expiresAt,
        String message
    ) {}
    
    record RefreshTokenResponse(
        boolean success,
        String accessToken,
        String refreshToken,
        LocalDateTime expiresAt
    ) {}
}