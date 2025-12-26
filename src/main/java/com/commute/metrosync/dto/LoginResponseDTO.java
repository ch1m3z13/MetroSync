package com.commute.metrosync.dto;

public class LoginResponseDTO {
    private UserDTO user;
    private String token;
    
    public LoginResponseDTO(UserDTO user, String token) {
        this.user = user;
        this.token = token;
    }
    
    // Getters and setters
    public UserDTO getUser() { return user; }
    public void setUser(UserDTO user) { this.user = user; }
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
