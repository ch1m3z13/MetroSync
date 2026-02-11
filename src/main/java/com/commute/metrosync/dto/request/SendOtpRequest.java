package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SendOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    public String phoneNumber;

    @NotBlank(message = "Purpose is required")
    public String purpose; // REGISTRATION, LOGIN, PHONE_VERIFICATION, etc.

    public String ipAddress;
    public String userAgent;
    public String deviceId;

    public SendOtpRequest() {}

    public SendOtpRequest(String phoneNumber, String purpose) {
        this.phoneNumber = phoneNumber;
        this.purpose = purpose;
    }
}