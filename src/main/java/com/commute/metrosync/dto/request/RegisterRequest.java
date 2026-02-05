package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import com.commute.metrosync.entity.User;

@Data
public class RegisterRequest {
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?234[0-9]{10}$", message = "Invalid Nigerian phone number")
    private String phoneNumber;

    @NotNull(message = "Role is required")
    private User.UserRole role;

    private String password; // Optional, can use OTP only
}