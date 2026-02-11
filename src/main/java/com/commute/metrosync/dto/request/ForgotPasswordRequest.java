package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for initiating password reset
 */
public class ForgotPasswordRequest {
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+234[7-9][0-1][0-9]{8}$", message = "Invalid Nigerian phone number format")
    private String phoneNumber;
    
    // Constructors
    public ForgotPasswordRequest() {}
    
    public ForgotPasswordRequest(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    // Getters and Setters
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}