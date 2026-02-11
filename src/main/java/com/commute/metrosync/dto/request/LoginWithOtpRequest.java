package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for OTP-based passwordless login
 */
public class LoginWithOtpRequest {
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+234[7-9][0-1][0-9]{8}$", message = "Invalid Nigerian phone number format")
    private String phoneNumber;
    
    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP code must be 6 digits")
    private String otpCode;
    
    private String deviceToken;  // Optional: for push notifications
    
    // Constructors
    public LoginWithOtpRequest() {}
    
    public LoginWithOtpRequest(String phoneNumber, String otpCode) {
        this.phoneNumber = phoneNumber;
        this.otpCode = otpCode;
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
    
    public String getDeviceToken() {
        return deviceToken;
    }
    
    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }
}