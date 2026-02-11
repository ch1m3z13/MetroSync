package com.commute.metrosync.dto.response;

/**
 * Response DTO for email availability check
 */
public class EmailCheckResponse {
    
    private boolean exists;
    private String email;
    private String message;
    
    // Constructors
    public EmailCheckResponse() {}
    
    public EmailCheckResponse(boolean exists, String email, String message) {
        this.exists = exists;
        this.email = email;
        this.message = message;
    }
    
    // Static factory methods
    public static EmailCheckResponse exists(String email) {
        return new EmailCheckResponse(true, email, "Email is already registered");
    }
    
    public static EmailCheckResponse available(String email) {
        return new EmailCheckResponse(false, email, "Email is available");
    }
    
    // Getters and Setters
    public boolean isExists() {
        return exists;
    }
    
    public void setExists(boolean exists) {
        this.exists = exists;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}