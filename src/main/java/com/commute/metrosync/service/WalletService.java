package com.commute.metrosync.service;

import com.commute.metrosync.dto.PagedResult;
import com.commute.metrosync.dto.request.WithdrawalRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wallet Service Interface
 * Defines operations for wallet and transaction management
 */
public interface WalletService {
    
    /**
     * Get wallet balance and details
     */
    WalletResponse getWalletBalance(UUID userId);
    
    /**
     * Initialize wallet topup via Paystack
     */
    TopupInitializeResponse initializeTopup(UUID userId, BigDecimal amount, String email);
    
    /**
     * Verify topup payment
     */
    PaymentVerificationResponse verifyTopup(UUID userId, String reference);
    
    /**
     * Process Paystack webhook
     */
    void processPaystackWebhook(String payload);
    
    /**
     * Request withdrawal to bank account
     */
    WithdrawalResponse requestWithdrawal(UUID userId, WithdrawalRequest request);
    
    /**
     * Get transaction history
     */
    PagedResult<TransactionResponse> getTransactions(
        UUID userId, String type, String category, String status,
        LocalDateTime startDate, LocalDateTime endDate, int page, int size
    );
    
    /**
     * Get transaction details
     */
    TransactionDetailResponse getTransactionDetails(UUID userId, UUID transactionId);
    
    /**
     * Get transaction summary
     */
    TransactionSummaryResponse getTransactionSummary(
        UUID userId, LocalDateTime startDate, LocalDateTime endDate
    );
    
    /**
     * Get Nigerian banks
     */
    BankListResponse getNigerianBanks();
    
    /**
     * Verify bank account
     */
    AccountVerificationResponse verifyBankAccount(String accountNumber, String bankCode);
    
    /**
     * Get wallet status
     */
    WalletStatusResponse getWalletStatus(UUID userId);
    
    /**
     * Block wallet (admin)
     */
    void blockWallet(UUID userId, String reason);
    
    /**
     * Unblock wallet (admin)
     */
    void unblockWallet(UUID userId);
    
    /**
     * Adjust wallet balance (admin)
     */
    void adjustWalletBalance(UUID userId, BigDecimal amount, String reason, UUID adminId);
    
    // ==================== INTERNAL METHODS ====================
    
    /**
     * Create a pending transaction (for bookings)
     */
    UUID createPendingTransaction(UUID userId, BigDecimal amount, String category, 
                                  String description, UUID bookingId);
    
    /**
     * Complete a pending transaction
     */
    void completeTransaction(UUID transactionId);
    
    /**
     * Cancel a pending transaction
     */
    void cancelTransaction(UUID transactionId);
    
    /**
     * Process refund
     */
    UUID processRefund(UUID userId, BigDecimal amount, String reason, UUID bookingId);
    
    /**
     * Credit wallet (for driver payouts)
     */
    UUID creditWallet(UUID userId, BigDecimal amount, String category, 
                     String description, UUID bookingId);
    
    /**
     * Record commission
     */
    UUID recordCommission(BigDecimal amount, UUID bookingId, UUID driverId);
    
    /**
     * Check if user has sufficient balance
     */
    boolean hasSufficientBalance(UUID userId, BigDecimal amount);
    
    /**
     * Check daily transaction limit
     */
    boolean checkDailyLimit(UUID userId, BigDecimal amount, boolean isWithdrawal);
    
    // ==================== RESPONSE CLASSES ====================
    
    record WalletResponse(
        UUID walletId,
        BigDecimal balance,
        BigDecimal ledgerBalance,
        String currency,
        String status,
        boolean isBlocked,
        LocalDateTime createdAt,
        LocalDateTime lastTransactionAt
    ) {}
    
    record TopupInitializeResponse(
        boolean success,
        String authorizationUrl,
        String reference,
        String accessCode
    ) {}
    
    record PaymentVerificationResponse(
        boolean success,
        String status,
        BigDecimal amount,
        String reference,
        LocalDateTime paidAt
    ) {}
    
    record WithdrawalResponse(
        UUID transactionId,
        String status,
        BigDecimal amount,
        String recipientAccount,
        String recipientBank,
        LocalDateTime requestedAt
    ) {}
    
    record TransactionResponse(
        UUID id,
        String type,
        String category,
        BigDecimal amount,
        String status,
        String description,
        LocalDateTime createdAt
    ) {}
    
    record TransactionDetailResponse(
        UUID id,
        String type,
        String category,
        BigDecimal amount,
        String status,
        String description,
        String reference,
        UUID bookingId,
        LocalDateTime createdAt,
        LocalDateTime completedAt
    ) {}
    
    record TransactionSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        int transactionCount,
        LocalDateTime periodStart,
        LocalDateTime periodEnd
    ) {}
    
    record BankListResponse(
        java.util.List<BankInfo> banks
    ) {}
    
    record BankInfo(
        String name,
        String code,
        String country
    ) {}
    
    record AccountVerificationResponse(
        boolean success,
        String accountName,
        String accountNumber,
        String bankName
    ) {}
    
    record WalletStatusResponse(
        String status,
        boolean isBlocked,
        String blockReason,
        LocalDateTime blockedAt
    ) {}
}