package com.commute.metrosync.dto.request;

import com.commute.metrosync.service.AuthService;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for password-based login
 * Wraps the AuthService.LoginRequest record
 */
public class LoginRequest {
    
    @NotBlank(message = "Identifier is required (phone number or email)")
    private String identifier;  // Can be phone number or email
    
    @NotBlank(message = "Password is required")
    private String password;
    
    private String deviceToken;  // Optional: for push notifications
    
    // Constructors
    public LoginRequest() {}
    
    public LoginRequest(String identifier, String password) {
        this.identifier = identifier;
        this.password = password;
    }
    
    /**
     * Convert to AuthService.LoginRequest record
     */
    public AuthService.LoginRequest toServiceRequest() {
        return new AuthService.LoginRequest(identifier, password);
    }
    
    // Getters and Setters
    public String getIdentifier() {
        return identifier;
    }
    
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getDeviceToken() {
        return deviceToken;
    }
    
    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }
}