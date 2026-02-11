package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for sending OTP
 * Note: Uses public fields for compatibility but adds getters for AuthResource
 */
public class SendOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    public String phoneNumber;

    @NotBlank(message = "Purpose is required")
    public String purpose; // REGISTRATION, LOGIN, PHONE_VERIFICATION, etc.

    public String ipAddress;
    public String userAgent;
    public String deviceId;

    public SendOtpRequest() {}

    public SendOtpRequest(String phoneNumber, String purpose) {
        this.phoneNumber = phoneNumber;
        this.purpose = purpose;
    }
    
    // Getters for compatibility with AuthResource method calls
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public String getPurpose() {
        return purpose;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
}