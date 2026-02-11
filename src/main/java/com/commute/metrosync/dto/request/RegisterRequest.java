package com.commute.metrosync.dto.request;

import com.commute.metrosync.service.AuthService;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * Request DTO for user registration with OTP
 * Wraps the AuthService.RegisterRequest record
 */
public class RegisterRequest {
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+234[7-9][0-1][0-9]{8}$", message = "Invalid Nigerian phone number format")
    private String phoneNumber;
    
    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP code must be 6 digits")
    private String otpCode;
    
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;
    
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "User type is required")
    @Pattern(regexp = "^(RIDER|DRIVER)$", message = "User type must be RIDER or DRIVER")
    private String userType;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
    
    // Constructors
    public RegisterRequest() {}
    
    public RegisterRequest(String phoneNumber, String otpCode, String fullName, 
                          String email, String userType, String password) {
        this.phoneNumber = phoneNumber;
        this.otpCode = otpCode;
        this.fullName = fullName;
        this.email = email;
        this.userType = userType;
        this.password = password;
    }
    
    /**
     * Convert to AuthService.RegisterRequest record
     */
    public AuthService.RegisterRequest toServiceRequest() {
        return new AuthService.RegisterRequest(
            fullName,
            email,
            phoneNumber,
            password,
            Set.of(userType) // Convert userType string to Set of roles
        );
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
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getUserType() {
        return userType;
    }
    
    public void setUserType(String userType) {
        this.userType = userType;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}