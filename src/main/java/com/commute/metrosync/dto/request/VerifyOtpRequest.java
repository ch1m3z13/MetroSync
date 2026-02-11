package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for verifying OTP
 * Note: Uses public fields for compatibility but adds getters for AuthResource
 */
public class VerifyOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    public String phoneNumber;

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP code must be 6 digits")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP code must be 6 digits")
    public String code;

    @NotBlank(message = "Purpose is required")
    public String purpose;

    public VerifyOtpRequest() {}

    public VerifyOtpRequest(String phoneNumber, String code, String purpose) {
        this.phoneNumber = phoneNumber;
        this.code = code;
        this.purpose = purpose;
    }
    
    // Getters for compatibility with AuthResource method calls
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public String getOtpCode() {
        return code;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getPurpose() {
        return purpose;
    }
}