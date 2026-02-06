package com.commute.metrosync.entity;

import jakarta.persistence.*;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wallet entity - User balance and withdrawal limits
 * Links to User table (one-to-one relationship)
 * Balance stored in kobo (1 NGN = 100 kobo)
 */
@Entity
@Table(name = "wallets", indexes = {
    @Index(name = "idx_wallets_user_id", columnList = "user_id"),
    @Index(name = "idx_wallets_status", columnList = "status")
})
public class Wallet extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Balance in kobo (smallest unit, 1 NGN = 100 kobo)
    @Column(name = "balance", nullable = false)
    private Long balance = 0L;

    // Wallet Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private WalletStatus status = WalletStatus.ACTIVE;

    // Daily Withdrawal Limits (in kobo)
    @Column(name = "daily_withdrawal_limit")
    private Long dailyWithdrawalLimit = 50000000L;  // 500,000 NGN in kobo

    @Column(name = "daily_withdrawal_used")
    private Long dailyWithdrawalUsed = 0L;

    @Column(name = "daily_withdrawal_reset_date")
    private LocalDate dailyWithdrawalResetDate = LocalDate.now();

    // Currency
    @Column(name = "currency", length = 3)
    private String currency = "NGN";

    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

    // Enums
    public enum WalletStatus {
        ACTIVE, FROZEN, SUSPENDED
    }

    // Business Methods

    /**
     * Convert kobo to Naira (BigDecimal for precision)
     */
    public BigDecimal getBalanceInNaira() {
        return BigDecimal.valueOf(balance).divide(BigDecimal.valueOf(100));
    }

    /**
     * Convert Naira to kobo
     */
    public static Long toKobo(BigDecimal naira) {
        return naira.multiply(BigDecimal.valueOf(100)).longValue();
    }

    /**
     * Convert kobo to Naira
     */
    public static BigDecimal toNaira(Long kobo) {
        return BigDecimal.valueOf(kobo).divide(BigDecimal.valueOf(100));
    }

    public boolean isActive() {
        return status == WalletStatus.ACTIVE;
    }

    public boolean isFrozen() {
        return status == WalletStatus.FROZEN;
    }

    public boolean isSuspended() {
        return status == WalletStatus.SUSPENDED;
    }

    public boolean hasInsufficientBalance(Long amount) {
        return balance < amount;
    }

    public boolean hasSufficientBalance(Long amount) {
        return balance >= amount;
    }

    /**
     * Check if withdrawal amount exceeds daily limit
     */
    public boolean exceedsDailyLimit(Long amount) {
        // Reset daily limit if it's a new day
        if (dailyWithdrawalResetDate.isBefore(LocalDate.now())) {
            dailyWithdrawalUsed = 0L;
            dailyWithdrawalResetDate = LocalDate.now();
        }
        
        return (dailyWithdrawalUsed + amount) > dailyWithdrawalLimit;
    }

    /**
     * Get remaining daily withdrawal limit
     */
    public Long getRemainingDailyLimit() {
        if (dailyWithdrawalResetDate.isBefore(LocalDate.now())) {
            return dailyWithdrawalLimit;
        }
        return Math.max(0, dailyWithdrawalLimit - dailyWithdrawalUsed);
    }

    public BigDecimal getRemainingDailyLimitInNaira() {
        return toNaira(getRemainingDailyLimit());
    }

    /**
     * Credit wallet (add funds)
     */
    public void credit(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance += amount;
        this.lastTransactionAt = LocalDateTime.now();
    }

    /**
     * Debit wallet (remove funds)
     */
    public void debit(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (hasInsufficientBalance(amount)) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        this.balance -= amount;
        this.lastTransactionAt = LocalDateTime.now();
    }

    /**
     * Track withdrawal for daily limit
     */
    public void trackWithdrawal(Long amount) {
        // Reset if new day
        if (dailyWithdrawalResetDate.isBefore(LocalDate.now())) {
            dailyWithdrawalUsed = 0L;
            dailyWithdrawalResetDate = LocalDate.now();
        }
        dailyWithdrawalUsed += amount;
    }

    // Getters and Setters
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Long getBalance() { return balance; }
    public void setBalance(Long balance) { this.balance = balance; }

    public WalletStatus getStatus() { return status; }
    public void setStatus(WalletStatus status) { this.status = status; }

    public Long getDailyWithdrawalLimit() { return dailyWithdrawalLimit; }
    public void setDailyWithdrawalLimit(Long dailyWithdrawalLimit) { 
        this.dailyWithdrawalLimit = dailyWithdrawalLimit; 
    }

    public Long getDailyWithdrawalUsed() { return dailyWithdrawalUsed; }
    public void setDailyWithdrawalUsed(Long dailyWithdrawalUsed) { 
        this.dailyWithdrawalUsed = dailyWithdrawalUsed; 
    }

    public LocalDate getDailyWithdrawalResetDate() { return dailyWithdrawalResetDate; }
    public void setDailyWithdrawalResetDate(LocalDate dailyWithdrawalResetDate) { 
        this.dailyWithdrawalResetDate = dailyWithdrawalResetDate; 
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getLastTransactionAt() { return lastTransactionAt; }
    public void setLastTransactionAt(LocalDateTime lastTransactionAt) { 
        this.lastTransactionAt = lastTransactionAt; 
    }
}