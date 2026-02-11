package com.commute.metrosync.dto.response;

/**
 * Response DTO for phone number availability check
 */
public class PhoneCheckResponse {
    
    private boolean exists;
    private String phoneNumber;
    private String message;
    
    // Constructors
    public PhoneCheckResponse() {}
    
    public PhoneCheckResponse(boolean exists, String phoneNumber, String message) {
        this.exists = exists;
        this.phoneNumber = phoneNumber;
        this.message = message;
    }
    
    // Static factory methods
    public static PhoneCheckResponse exists(String phoneNumber) {
        return new PhoneCheckResponse(true, phoneNumber, "Phone number is already registered");
    }
    
    public static PhoneCheckResponse available(String phoneNumber) {
        return new PhoneCheckResponse(false, phoneNumber, "Phone number is available");
    }
    
    // Getters and Setters
    public boolean isExists() {
        return exists;
    }
    
    public void setExists(boolean exists) {
        this.exists = exists;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}