package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import com.commute.metrosync.entity.User;

@Data
public class VerifyOtpRequest {
    
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "OTP is required")
    private String otp;
    
    // Optional: If verifying for registration completion
    private User.UserRole role;
    private String password;
}
