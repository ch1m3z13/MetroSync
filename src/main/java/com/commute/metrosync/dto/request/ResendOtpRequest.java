package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for resending OTP
 */
public class ResendOtpRequest {
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+234[7-9][0-1][0-9]{8}$", message = "Invalid Nigerian phone number format")
    private String phoneNumber;
    
    @NotBlank(message = "Purpose is required")
    @Pattern(regexp = "^(REGISTRATION|LOGIN|PHONE_VERIFICATION|PASSWORD_RESET)$", 
             message = "Purpose must be REGISTRATION, LOGIN, PHONE_VERIFICATION, or PASSWORD_RESET")
    private String purpose;
    
    // Constructors
    public ResendOtpRequest() {}
    
    public ResendOtpRequest(String phoneNumber, String purpose) {
        this.phoneNumber = phoneNumber;
        this.purpose = purpose;
    }
    
    // Getters and Setters
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getPurpose() {
        return purpose;
    }
    
    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}