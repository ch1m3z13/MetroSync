package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class WalletTopUpRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "100.0", message = "Minimum top-up amount is ₦100")
    @DecimalMax(value = "1000000.0", message = "Maximum top-up amount is ₦1,000,000")
    public BigDecimal amount;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    public String email;

    public String callbackUrl;

    public WalletTopUpRequest() {}

    public WalletTopUpRequest(BigDecimal amount, String email) {
        this.amount = amount;
        this.email = email;
    }
}