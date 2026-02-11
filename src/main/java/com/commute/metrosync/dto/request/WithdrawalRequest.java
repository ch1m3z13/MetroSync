package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class WithdrawalRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1000.0", message = "Minimum withdrawal amount is ₦1,000")
    public BigDecimal amount;

    @NotBlank(message = "Bank code is required")
    public String bankCode;

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Account number must be 10 digits")
    public String accountNumber;

    @NotBlank(message = "Account name is required")
    public String accountName;

    public WithdrawalRequest() {}

    public WithdrawalRequest(BigDecimal amount, String bankCode, String accountNumber, String accountName) {
        this.amount = amount;
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
    }
}