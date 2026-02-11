package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for resetting password with OTP
 */
public class ResetPasswordRequest {
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+234[7-9][0-1][0-9]{8}$", message = "Invalid Nigerian phone number format")
    private String phoneNumber;
    
    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP code must be 6 digits")
    private String otpCode;
    
    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
    
    // Constructors
    public ResetPasswordRequest() {}
    
    public ResetPasswordRequest(String phoneNumber, String otpCode, String newPassword) {
        this.phoneNumber = phoneNumber;
        this.otpCode = otpCode;
        this.newPassword = newPassword;
    }
    
    // Getters and Setters
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getOtpCode() {
        return otpCode;
    }
    
    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
    
    public String getNewPassword() {
        return newPassword;
    }
    
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}