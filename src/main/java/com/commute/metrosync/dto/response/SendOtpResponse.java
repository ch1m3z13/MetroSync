package com.commute.metrosync.dto.response;

/**
 * Response DTO for OTP sending operation
 */
public class SendOtpResponse {
    
    private boolean success;
    private String message;
    private String messageId;  // From SMS provider
    private Integer expiresInSeconds;
    
    // Constructors
    public SendOtpResponse() {}
    
    public SendOtpResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public SendOtpResponse(boolean success, String message, String messageId, Integer expiresInSeconds) {
        this.success = success;
        this.message = message;
        this.messageId = messageId;
        this.expiresInSeconds = expiresInSeconds;
    }
    
    // Static factory methods
    public static SendOtpResponse success(String messageId, Integer expiresInSeconds) {
        return new SendOtpResponse(true, "OTP sent successfully", messageId, expiresInSeconds);
    }
    
    public static SendOtpResponse error(String message) {
        return new SendOtpResponse(false, message, null, null);
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
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public Integer getExpiresInSeconds() {
        return expiresInSeconds;
    }
    
    public void setExpiresInSeconds(Integer expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}