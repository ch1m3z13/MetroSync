package com.commute.metrosync.service.impl;

import com.commute.metrosync.service.PaystackService.PaystackInitializeResponse;
import com.commute.metrosync.service.PaystackService.PaystackRecipientResponse;
import com.commute.metrosync.service.PaystackService.PaystackTransferResponse;
import com.commute.metrosync.service.PaystackService.PaystackVerifyResponse;
import com.commute.metrosync.dto.*;
import com.commute.metrosync.service.NotificationService;
import com.commute.metrosync.service.PaystackService;
import com.commute.metrosync.service.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Wallet Service Implementation
 * Handles all wallet operations, transactions, and payment processing
 */
@ApplicationScoped
@Transactional
public class WalletServiceImpl implements WalletService {
    
    private static final Logger logger = Logger.getLogger(WalletServiceImpl.class.getName());
    
    @PersistenceContext(unitName = "commuteng-pu")
    private EntityManager entityManager;
    
    @Inject
    private PaystackService paystackService;
    
    @Inject
    private NotificationService notificationService;
    
    @Inject
    private ObjectMapper objectMapper;
    
    @ConfigProperty(name = "wallet.daily.transaction.limit", defaultValue = "100000")
    private BigDecimal dailyTransactionLimit;
    
    @ConfigProperty(name = "wallet.daily.withdrawal.limit", defaultValue = "50000")
    private BigDecimal dailyWithdrawalLimit;
    
    @ConfigProperty(name = "wallet.withdrawal.fee", defaultValue = "100")
    private BigDecimal withdrawalFee;
    
    @Override
    public WalletResponse getWalletBalance(UUID userId) {
        logger.info("Getting wallet balance for user: " + userId);
        
        String query = "SELECT * FROM wallet_overview WHERE user_id = :userId";
        Map<String, Object> result = (Map<String, Object>) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .getSingleResult();
        
        WalletResponse response = new WalletResponse();
        response.setUserId(userId);
        response.setBalance((BigDecimal) result.get("balance"));
        response.setLedgerBalance((BigDecimal) result.get("ledger_balance"));
        response.setCurrency("NGN");
        response.setIsActive((Boolean) result.get("is_active"));
        
        // Set daily limits
        WalletResponse.DailyLimits limits = new WalletResponse.DailyLimits();
        limits.setTransactionLimit(dailyTransactionLimit);
        limits.setWithdrawalLimit(dailyWithdrawalLimit);
        limits.setTodayTransactionTotal((BigDecimal) result.get("today_credits"));
        limits.setTodayWithdrawalTotal((BigDecimal) result.get("today_debits"));
        limits.setRemainingTransactionLimit(
            dailyTransactionLimit.subtract((BigDecimal) result.get("today_credits"))
        );
        limits.setRemainingWithdrawalLimit(
            dailyWithdrawalLimit.subtract((BigDecimal) result.get("today_debits"))
        );
        response.setDailyLimits(limits);
        
        return response;
    }
    
    @Override
    public TopupInitializeResponse initializeTopup(UUID userId, BigDecimal amount, String email) {
        logger.info("Initializing topup for user " + userId + ": amount=" + amount);
        
        // Generate transaction reference
        String reference = generateTransactionReference();
        
        // Create pending transaction
        UUID transactionId = createTransaction(
            userId,
            "CREDIT",
            "TOPUP",
            amount,
            BigDecimal.ZERO,
            amount,
            "Wallet topup",
            "PENDING",
            reference,
            null
        );
        
        // Initialize Paystack payment
        Long amountInKobo = amount.multiply(BigDecimal.valueOf(100)).longValue();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", userId.toString());
        metadata.put("transaction_id", transactionId.toString());
        metadata.put("purpose", "WALLET_TOPUP");
        
        PaystackService.PaystackInitializeResponse paystackResponse = 
            paystackService.initializeTransaction(email, amountInKobo, reference, metadata);
        
        if (!paystackResponse.isSuccess()) {
            // Mark transaction as failed
            updateTransactionStatus(transactionId, "FAILED");
            throw new RuntimeException("Failed to initialize payment: " + paystackResponse.getErrorMessage());
        }
        
        TopupInitializeResponse response = new TopupInitializeResponse();
        response.setTransactionId(transactionId);
        response.setReference(reference);
        response.setAmount(amount);
        response.setPaymentUrl(paystackResponse.getAuthorizationUrl());
        response.setAccessCode(paystackResponse.getAccessCode());
        response.setExpiresAt(LocalDateTime.now().plusHours(1));
        
        return response;
    }
    
    @Override
    public PaymentVerificationResponse verifyTopup(UUID userId, String reference) {
        logger.info("Verifying topup for user " + userId + ": reference=" + reference);
        
        // Verify with Paystack
        PaystackService.PaystackVerifyResponse paystackResponse = 
            paystackService.verifyTransaction(reference);
        
        if (!paystackResponse.isSuccess()) {
            return new PaymentVerificationResponse(false, "Payment verification failed");
        }
        
        if (!"success".equalsIgnoreCase(paystackResponse.getStatus())) {
            return new PaymentVerificationResponse(false, "Payment not successful");
        }
        
        // Find transaction by reference
        String query = "SELECT id FROM transactions WHERE reference = :reference AND user_id = :userId";
        UUID transactionId = (UUID) entityManager.createNativeQuery(query)
            .setParameter("reference", reference)
            .setParameter("userId", userId)
            .getSingleResult();
        
        // Update transaction status
        updateTransactionStatus(transactionId, "COMPLETED");
        
        // Wallet balance will be updated by trigger
        
        // Send notification
        BigDecimal amount = BigDecimal.valueOf(paystackResponse.getAmount()).divide(BigDecimal.valueOf(100));
        notificationService.sendNotification(
            userId,
            "Wallet Credited",
            "Your wallet has been credited with ₦" + amount,
            "WALLET_CREDITED",
            "NORMAL",
            Map.of("amount", amount.toString(), "reference", reference),
            "/wallet"
        );
        
        return new PaymentVerificationResponse(true, "Payment verified successfully", amount);
    }
    
    @Override
    public void processPaystackWebhook(String payload) {
        logger.info("Processing Paystack webhook");
        
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventType = event.get("event").asText();
            
            if ("charge.success".equals(eventType)) {
                JsonNode data = event.get("data");
                String reference = data.get("reference").asText();
                String status = data.get("status").asText();
                
                if ("success".equalsIgnoreCase(status)) {
                    // Find transaction
                    String query = "SELECT id, user_id FROM transactions WHERE reference = :reference";
                    Object[] result = (Object[]) entityManager.createNativeQuery(query)
                        .setParameter("reference", reference)
                        .getSingleResult();
                    
                    UUID transactionId = (UUID) result[0];
                    UUID userId = (UUID) result[1];
                    
                    // Update transaction
                    updateTransactionStatus(transactionId, "COMPLETED");
                    
                    // Send notification
                    BigDecimal amount = BigDecimal.valueOf(data.get("amount").asLong())
                        .divide(BigDecimal.valueOf(100));
                    
                    notificationService.sendNotification(
                        userId,
                        "Payment Successful",
                        "Your payment of ₦" + amount + " was successful",
                        "PAYMENT_SUCCESS",
                        "NORMAL",
                        Map.of("amount", amount.toString(), "reference", reference),
                        "/wallet"
                    );
                }
            }
            
        } catch (Exception e) {
            logger.severe("Error processing webhook: " + e.getMessage());
            throw new RuntimeException("Webhook processing failed", e);
        }
    }
    
    @Override
    public WithdrawalResponse requestWithdrawal(UUID userId, WithdrawalRequest request) {
        logger.info("Processing withdrawal for user " + userId + ": amount=" + request.getAmount());
        
        // Verify OTP (this should call OtpService)
        // For now, assuming OTP is pre-verified
        
        // Check balance
        if (!hasSufficientBalance(userId, request.getAmount().add(withdrawalFee))) {
            return WithdrawalResponse.failure("Insufficient balance");
        }
        
        // Check daily limit
        if (!checkDailyLimit(userId, request.getAmount(), true)) {
            return WithdrawalResponse.failure("Daily withdrawal limit exceeded");
        }
        
        // Verify bank account
        String accountName = paystackService.resolveAccountNumber(
            request.getAccountNumber(), 
            request.getBankCode()
        );
        
        if (accountName == null) {
            return WithdrawalResponse.failure("Invalid bank account");
        }
        
        if (!accountName.equalsIgnoreCase(request.getAccountName())) {
            return WithdrawalResponse.failure("Account name mismatch");
        }
        
        // Create transfer recipient
        PaystackService.PaystackRecipientResponse recipientResponse = 
            paystackService.createTransferRecipient(
                "nuban",
                accountName,
                request.getAccountNumber(),
                request.getBankCode()
            );
        
        if (!recipientResponse.isSuccess()) {
            return WithdrawalResponse.failure("Failed to create recipient: " + recipientResponse.getErrorMessage());
        }
        
        // Generate reference
        String reference = generateTransactionReference();
        
        // Create debit transaction
        UUID transactionId = createTransaction(
            userId,
            "DEBIT",
            "WITHDRAWAL",
            request.getAmount(),
            withdrawalFee,
            request.getAmount().add(withdrawalFee),
            "Withdrawal to " + request.getBankCode(),
            "PROCESSING",
            reference,
            null
        );
        
        // Initiate transfer
        Long amountInKobo = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        
        PaystackService.PaystackTransferResponse transferResponse = 
            paystackService.initiateTransfer(
                amountInKobo,
                recipientResponse.getRecipientCode(),
                request.getNarration() != null ? request.getNarration() : "Withdrawal",
                reference
            );
        
        if (!transferResponse.isSuccess()) {
            updateTransactionStatus(transactionId, "FAILED");
            return WithdrawalResponse.failure("Transfer failed: " + transferResponse.getErrorMessage());
        }
        
        // Update transaction with transfer details
        updateTransactionStatus(transactionId, "PROCESSING");
        
        WithdrawalResponse response = new WithdrawalResponse();
        response.setSuccess(true);
        response.setTransactionId(transactionId);
        response.setReference(reference);
        response.setAmount(request.getAmount());
        response.setFee(withdrawalFee);
        response.setTotalDeduction(request.getAmount().add(withdrawalFee));
        response.setRecipientAccount(request.getAccountNumber());
        response.setRecipientBank(request.getBankCode());
        response.setStatus("PROCESSING");
        response.setEstimatedCompletion("Within 24 hours");
        
        return response;
    }
    
    @Override
    public UUID createPendingTransaction(UUID userId, BigDecimal amount, String category, 
                                        String description, UUID bookingId) {
        logger.info("Creating pending transaction for user " + userId + ": " + category);
        
        String reference = generateTransactionReference();
        
        return createTransaction(
            userId,
            "DEBIT",
            category,
            amount,
            BigDecimal.ZERO,
            amount,
            description,
            "PENDING",
            reference,
            bookingId
        );
    }
    
    @Override
    public void completeTransaction(UUID transactionId) {
        logger.info("Completing transaction: " + transactionId);
        updateTransactionStatus(transactionId, "COMPLETED");
    }
    
    @Override
    public void cancelTransaction(UUID transactionId) {
        logger.info("Cancelling transaction: " + transactionId);
        updateTransactionStatus(transactionId, "CANCELLED");
    }
    
    @Override
    public UUID processRefund(UUID userId, BigDecimal amount, String reason, UUID bookingId) {
        logger.info("Processing refund for user " + userId + ": amount=" + amount);
        
        String reference = generateTransactionReference();
        
        UUID transactionId = createTransaction(
            userId,
            "CREDIT",
            "BOOKING_REFUND",
            amount,
            BigDecimal.ZERO,
            amount,
            "Refund: " + reason,
            "COMPLETED",
            reference,
            bookingId
        );
        
        // Send notification
        notificationService.sendNotification(
            userId,
            "Refund Processed",
            "₦" + amount + " has been refunded to your wallet",
            "WALLET_CREDITED",
            "NORMAL",
            Map.of("amount", amount.toString(), "reason", reason),
            "/wallet"
        );
        
        return transactionId;
    }
    
    @Override
    public UUID creditWallet(UUID userId, BigDecimal amount, String category, 
                            String description, UUID bookingId) {
        logger.info("Crediting wallet for user " + userId + ": amount=" + amount);
        
        String reference = generateTransactionReference();
        
        UUID transactionId = createTransaction(
            userId,
            "CREDIT",
            category,
            amount,
            BigDecimal.ZERO,
            amount,
            description,
            "COMPLETED",
            reference,
            bookingId
        );
        
        // Send notification for driver payouts
        if ("DRIVER_PAYOUT".equals(category)) {
            notificationService.sendNotification(
                userId,
                "Earnings Credited",
                "₦" + amount + " has been credited to your wallet",
                "WALLET_CREDITED",
                "NORMAL",
                Map.of("amount", amount.toString(), "category", category),
                "/wallet"
            );
        }
        
        return transactionId;
    }
    
    @Override
    public UUID recordCommission(BigDecimal amount, UUID bookingId, UUID driverId) {
        logger.info("Recording commission: amount=" + amount + ", booking=" + bookingId);
        
        // Commission goes to platform account (special user ID or null)
        // For now, we'll create a record without debiting any specific user
        
        String reference = generateTransactionReference();
        
        return createTransaction(
            null, // Platform account
            "CREDIT",
            "COMMISSION",
            amount,
            BigDecimal.ZERO,
            amount,
            "Commission from booking: " + bookingId,
            "COMPLETED",
            reference,
            bookingId
        );
    }
    
    @Override
    public boolean hasSufficientBalance(UUID userId, BigDecimal amount) {
        String query = "SELECT has_sufficient_balance(:userId, :amount)";
        Boolean result = (Boolean) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .setParameter("amount", amount)
            .getSingleResult();
        
        return Boolean.TRUE.equals(result);
    }
    
    @Override
    public boolean checkDailyLimit(UUID userId, BigDecimal amount, boolean isWithdrawal) {
        String query = "SELECT check_daily_limit(:userId, :amount, :isWithdrawal)";
        Boolean result = (Boolean) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .setParameter("amount", amount)
            .setParameter("isWithdrawal", isWithdrawal)
            .getSingleResult();
        
        return Boolean.TRUE.equals(result);
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    private UUID createTransaction(UUID userId, String type, String category, 
                                   BigDecimal amount, BigDecimal fee, BigDecimal totalAmount,
                                   String description, String status, String reference,
                                   UUID bookingId) {
        
        UUID transactionId = UUID.randomUUID();
        
        // Get current balance for snapshot
        BigDecimal currentBalance = getCurrentBalance(userId);
        BigDecimal balanceAfter = currentBalance;
        
        if ("COMPLETED".equals(status)) {
            if ("CREDIT".equals(type)) {
                balanceAfter = currentBalance.add(amount);
            } else if ("DEBIT".equals(type)) {
                balanceAfter = currentBalance.subtract(totalAmount);
            }
        }
        
        String insertQuery = "INSERT INTO transactions " +
            "(id, user_id, reference, type, category, amount, fee, total_amount, " +
            "description, status, balance_before, balance_after, related_booking_id, " +
            "created_at, updated_at) " +
            "VALUES (:id, :userId, :reference, :type, :category, :amount, :fee, :totalAmount, " +
            ":description, :status, :balanceBefore, :balanceAfter, :bookingId, " +
            "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        
        entityManager.createNativeQuery(insertQuery)
            .setParameter("id", transactionId)
            .setParameter("userId", userId)
            .setParameter("reference", reference)
            .setParameter("type", type)
            .setParameter("category", category)
            .setParameter("amount", amount)
            .setParameter("fee", fee)
            .setParameter("totalAmount", totalAmount)
            .setParameter("description", description)
            .setParameter("status", status)
            .setParameter("balanceBefore", currentBalance)
            .setParameter("balanceAfter", balanceAfter)
            .setParameter("bookingId", bookingId)
            .executeUpdate();
        
        logger.info("Transaction created: " + transactionId + ", reference=" + reference);
        
        return transactionId;
    }
    
    private void updateTransactionStatus(UUID transactionId, String status) {
        String query = "UPDATE transactions SET status = :status, " +
                      "completed_at = CASE WHEN :status = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at END, " +
                      "updated_at = CURRENT_TIMESTAMP " +
                      "WHERE id = :transactionId";
        
        entityManager.createNativeQuery(query)
            .setParameter("status", status)
            .setParameter("transactionId", transactionId)
            .executeUpdate();
    }
    
    private BigDecimal getCurrentBalance(UUID userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        
        String query = "SELECT get_wallet_balance(:userId)";
        Number result = (Number) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .getSingleResult();
        
        return result != null ? BigDecimal.valueOf(result.doubleValue()) : BigDecimal.ZERO;
    }
    
    private String generateTransactionReference() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "TXN-" + datePart + "-" + randomPart;
    }
}