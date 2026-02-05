package com.commute.metrosync.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private Long userId;
    private String phoneNumber;
    private String role;
    private boolean verified;
    private String message;
    private boolean requiresOtp;
    private String otp; // Only for dev/test
}