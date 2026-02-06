package com.commute.metrosync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transaction entity - Audit trail for all financial activities
 * Records every wallet credit/debit operation
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_user_id", columnList = "user_id"),
    @Index(name = "idx_transactions_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_transactions_booking_id", columnList = "booking_id"),
    @Index(name = "idx_transactions_reference", columnList = "reference"),
    @Index(name = "idx_transactions_external_reference", columnList = "external_reference"),
    @Index(name = "idx_transactions_type", columnList = "type"),
    @Index(name = "idx_transactions_status", columnList = "status"),
    @Index(name = "idx_transactions_created_at", columnList = "created_at"),
    @Index(name = "idx_transactions_provider", columnList = "payment_provider")
})
public class Transaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    // Transaction Details
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TransactionType type;

    @Column(name = "amount", nullable = false)
    private Long amount;  // In kobo (can be negative for debits)

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    // Reference
    @Column(name = "reference", unique = true, nullable = false, length = 100)
    private String reference;  // Unique transaction reference

    @Column(name = "external_reference", length = 200)
    private String externalReference;  // Paystack/Bank reference

    // Payment Gateway Details
    @Column(name = "payment_provider", length = 50)
    private String paymentProvider;  // PAYSTACK, BANK_TRANSFER, CASH

    @Column(name = "payment_channel", length = 50)
    private String paymentChannel;  // CARD, BANK_TRANSFER, USSD, QR

    // Related Entities
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;  // For ride payments/earnings

    // Balance Snapshots
    @Column(name = "balance_before", nullable = false)
    private Long balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    // Metadata
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Enums
    public enum TransactionType {
        TOP_UP,          // User adds money to wallet
        WITHDRAWAL,      // User withdraws money from wallet
        RIDE_PAYMENT,    // Rider pays for a ride
        RIDE_EARNING,    // Driver earns from a ride
        REFUND,          // Money returned to user
        COMMISSION       // Platform commission deducted
    }

    public enum TransactionStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REVERSED
    }

    // Business Methods
    
    /**
     * Generate unique transaction reference
     */
    public static String generateReference() {
        return String.format("TXN-%s-%s", 
            LocalDateTime.now().toString().replaceAll("[^0-9]", "").substring(0, 14),
            UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );
    }

    public BigDecimal getAmountInNaira() {
        return Wallet.toNaira(amount);
    }

    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }

    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == TransactionStatus.FAILED;
    }

    public boolean isReversed() {
        return status == TransactionStatus.REVERSED;
    }

    public boolean isCredit() {
        return amount > 0;
    }

    public boolean isDebit() {
        return amount < 0;
    }

    public void complete() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = TransactionStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public void reverse() {
        this.status = TransactionStatus.REVERSED;
        this.completedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Wallet getWallet() { return wallet; }
    public void setWallet(Wallet wallet) { this.wallet = wallet; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { 
        this.externalReference = externalReference; 
    }

    public String getPaymentProvider() { return paymentProvider; }
    public void setPaymentProvider(String paymentProvider) { 
        this.paymentProvider = paymentProvider; 
    }

    public String getPaymentChannel() { return paymentChannel; }
    public void setPaymentChannel(String paymentChannel) { 
        this.paymentChannel = paymentChannel; 
    }

    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }

    public Long getBalanceBefore() { return balanceBefore; }
    public void setBalanceBefore(Long balanceBefore) { this.balanceBefore = balanceBefore; }

    public Long getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Long balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}