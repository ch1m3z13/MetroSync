package com.commute.metrosync.dto;

import java.util.UUID;

public class UserDTO {
    private String id;  // Changed to String for Flutter compatibility
    private String username;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String roles;  // Changed from 'role' to 'roles'
    private Double rating;
    private Integer totalRatings;
    private Boolean isVerified;
    private String token;  // Optional, only included in login/register responses
    
    public UserDTO() {
        this.rating = 0.0;
        this.totalRatings = 0;
        this.isVerified = false;
    }
    
    public UserDTO(UUID id, String username, String fullName, String email, 
                   String phoneNumber, String role) {
        this.id = id.toString();
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.roles = role;
        this.rating = 0.0;
        this.totalRatings = 0;
        this.isVerified = false;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }
    
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    
    public Integer getTotalRatings() { return totalRatings; }
    public void setTotalRatings(Integer totalRatings) { this.totalRatings = totalRatings; }
    
    public Boolean getIsVerified() { return isVerified; }
    public void setIsVerified(Boolean isVerified) { this.isVerified = isVerified; }
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}