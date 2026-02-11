package com.commute.metrosync.dto.request;

/**
 * Request DTO for user logout
 */
public class LogoutRequest {
    
    private String refreshToken;  // Required: to invalidate the refresh token
    private String deviceToken;  // Optional: to unregister device from push notifications
    
    // Constructors
    public LogoutRequest() {}
    
    public LogoutRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public LogoutRequest(String refreshToken, String deviceToken) {
        this.refreshToken = refreshToken;
        this.deviceToken = deviceToken;
    }
    
    // Getters and Setters
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    // Getters and Setters
    public String getDeviceToken() {
        return deviceToken;
    }
    
    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }
}