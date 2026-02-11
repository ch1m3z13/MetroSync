package com.commute.metrosync.dto.response;

/**
 * Response DTO for OTP verification operation
 */
public class VerifyOtpResponse {
    
    private boolean success;
    private String message;
    private String phoneNumber;
    private String purpose;
    
    // Constructors
    public VerifyOtpResponse() {}
    
    public VerifyOtpResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public VerifyOtpResponse(boolean success, String message, String phoneNumber, String purpose) {
        this.success = success;
        this.message = message;
        this.phoneNumber = phoneNumber;
        this.purpose = purpose;
    }
    
    // Static factory methods
    public static VerifyOtpResponse success(String phoneNumber, String purpose) {
        return new VerifyOtpResponse(true, "OTP verified successfully", phoneNumber, purpose);
    }
    
    public static VerifyOtpResponse error(String message) {
        return new VerifyOtpResponse(false, message, null, null);
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
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