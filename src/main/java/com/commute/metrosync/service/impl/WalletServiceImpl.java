package com.commute.metrosync.service.impl;

import com.commute.metrosync.service.PaystackService.PaystackInitializeResponse;
import com.commute.metrosync.service.PaystackService.PaystackRecipientResponse;
import com.commute.metrosync.service.PaystackService.PaystackTransferResponse;
import com.commute.metrosync.service.PaystackService.PaystackVerifyResponse;
import com.commute.metrosync.dto.*;
import com.commute.metrosync.service.NotificationService;
import com.commute.metrosync.service.PaystackService;
import com.commute.metrosync.service.WalletService;
import com.commute.metrosync.service.WalletService.BankInfo;
import com.commute.metrosync.dto.request.WithdrawalRequest;
import com.commute.metrosync.dto.PagedResult;
import com.commute.metrosync.entity.Notification;
import com.commute.metrosync.entity.User;
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
    
    // Query wallet table
        String query = """
            SELECT w.id, w.balance, w.ledger_balance, w.is_active, w.is_blocked,
                w.created_at, w.updated_at
            FROM wallets w
            WHERE w.user_id = :userId
            """;
    
        Object[] result = (Object[]) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .getSingleResult();
    
        UUID walletId = (UUID) result[0];
        BigDecimal balance = (BigDecimal) result[1];
        BigDecimal ledgerBalance = (BigDecimal) result[2];
        Boolean isActive = (Boolean) result[3];
        Boolean isBlocked = (Boolean) result[4];
        LocalDateTime createdAt = (LocalDateTime) result[5];
    
    // Get last transaction time
        String lastTxQuery = """
            SELECT MAX(created_at) FROM transactions 
            WHERE user_id = :userId
            """;
        LocalDateTime lastTransactionAt = (LocalDateTime) entityManager
            .createNativeQuery(lastTxQuery)
            .setParameter("userId", userId)
            .getSingleResult();
    
    // FIX: Use record constructor instead of setters
        return new WalletResponse(
            walletId,
            balance,
            ledgerBalance,
            "NGN",
            isActive ? "ACTIVE" : "INACTIVE",
            isBlocked != null && isBlocked,
            createdAt,
            lastTransactionAt
        );
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
            updateTransactionStatus(transactionId, "FAILED");
            throw new RuntimeException("Failed to initialize payment: " + paystackResponse.getErrorMessage());
        }
        
        // FIX: Use record constructor instead of setters
        return new TopupInitializeResponse(
            true,
            paystackResponse.getAuthorizationUrl(),
            reference,
            paystackResponse.getAccessCode()
        );
    }
    
    @Override
    public PaymentVerificationResponse verifyTopup(UUID userId, String reference) {
        logger.info("Verifying topup for user " + userId + ": reference=" + reference);
        
        // Verify with Paystack
        PaystackService.PaystackVerifyResponse paystackResponse = 
            paystackService.verifyTransaction(reference);
        
        // FIX: Use record constructor with all 5 parameters
        if (!paystackResponse.isSuccess()) {
            return new PaymentVerificationResponse(
                false,
                "FAILED",
                BigDecimal.ZERO,
                reference,
                null
            );
        }
        
        if (!"success".equalsIgnoreCase(paystackResponse.getStatus())) {
            return new PaymentVerificationResponse(
                false,
                "FAILED",
                BigDecimal.ZERO,
                reference,
                null
            );
        }
        
        // Find transaction by reference
        String query = "SELECT id FROM transactions WHERE reference = :reference AND user_id = :userId";
        UUID transactionId = (UUID) entityManager.createNativeQuery(query)
            .setParameter("reference", reference)
            .setParameter("userId", userId)
            .getSingleResult();
        
        // Update transaction status
        updateTransactionStatus(transactionId, "COMPLETED");
        
        BigDecimal amount = BigDecimal.valueOf(paystackResponse.getAmount()).divide(BigDecimal.valueOf(100));
        
        // FIX: Create notification directly instead of calling wrong method
        Notification notification = new Notification();
        notification.setUser(entityManager.find(User.class, userId));
        notification.setTitle("Wallet Credited");
        notification.setMessage("Your wallet has been credited with ₦" + amount);
        notification.setType(Notification.NotificationType.WALLET_TOPUP);
        notification.setPriority(Notification.Priority.NORMAL);
        notification.setDeliveryChannels(new String[]{"IN_APP", "PUSH"});
        notification.setActionType("VIEW_WALLET");
        notification.setActionData(Map.of(
            "amount", amount.toString(),
            "reference", reference
        ));
        entityManager.persist(notification);
        
        // FIX: Use record constructor
        return new PaymentVerificationResponse(
            true,
            "SUCCESS",
            amount,
            reference,
            paystackResponse.getPaidAt()
        );
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
                    String query = "SELECT id, user_id FROM transactions WHERE reference = :reference";
                    Object[] result = (Object[]) entityManager.createNativeQuery(query)
                        .setParameter("reference", reference)
                        .getSingleResult();
                    
                    UUID transactionId = (UUID) result[0];
                    UUID userId = (UUID) result[1];
                    
                    updateTransactionStatus(transactionId, "COMPLETED");
                    
                    BigDecimal amount = BigDecimal.valueOf(data.get("amount").asLong())
                        .divide(BigDecimal.valueOf(100));
                    
                    // FIX: Create notification directly
                    Notification notification = new Notification();
                    notification.setUser(entityManager.find(User.class, userId));
                    notification.setTitle("Payment Successful");
                    notification.setMessage("Your payment of ₦" + amount + " was successful");
                    notification.setType(Notification.NotificationType.PAYMENT_RECEIVED);
                    notification.setPriority(Notification.Priority.NORMAL);
                    notification.setDeliveryChannels(new String[]{"IN_APP", "PUSH"});
                    notification.setActionType("VIEW_TRANSACTION");
                    notification.setActionData(Map.of(
                        "amount", amount.toString(),
                        "reference", reference
                    ));
                    entityManager.persist(notification);
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
        
        // Check balance
        if (!hasSufficientBalance(userId, request.getAmount().add(withdrawalFee))) {
            // FIX: Use record constructor instead of .failure()
            return new WithdrawalResponse(
                null,
                "FAILED",
                BigDecimal.ZERO,
                null,
                null,
                LocalDateTime.now()
            );
        }
        
        // Check daily limit
        if (!checkDailyLimit(userId, request.getAmount(), true)) {
            return new WithdrawalResponse(
                null,
                "FAILED",
                BigDecimal.ZERO,
                null,
                null,
                LocalDateTime.now()
            );
        }
        
        // Verify bank account
        String accountName = paystackService.resolveAccountNumber(
            request.getAccountNumber(), 
            request.getBankCode()
        );
        
        if (accountName == null) {
            return new WithdrawalResponse(
                null,
                "FAILED",
                BigDecimal.ZERO,
                null,
                null,
                LocalDateTime.now()
            );
        }
        
        if (!accountName.equalsIgnoreCase(request.getAccountName())) {
            return new WithdrawalResponse(
                null,
                "FAILED",
                BigDecimal.ZERO,
                null,
                null,
                LocalDateTime.now()
            );
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
            return new WithdrawalResponse(
                null,
                "FAILED",
                BigDecimal.ZERO,
                null,
                null,
                LocalDateTime.now()
            );
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
                reference,
                "Wallet withdrawal"
            );
        
        if (!transferResponse.isSuccess()) {
            updateTransactionStatus(transactionId, "FAILED");
            return new WithdrawalResponse(
                null,
                "FAILED",
                BigDecimal.ZERO,
                null,
                null,
                LocalDateTime.now()
            );
        }
        
        // FIX: Use record constructor instead of setters
        return new WithdrawalResponse(
            transactionId,
            "PROCESSING",
            request.getAmount(),
            request.getAccountNumber(),
            request.getBankCode(),
            LocalDateTime.now()
        );
    }
    
    @Override
    public PagedResult<TransactionResponse> getTransactions(
            UUID userId, String type, String category, String status,
            LocalDateTime startDate, LocalDateTime endDate, int page, int size) {
        
        logger.info("Getting transactions for user: " + userId);
        
        StringBuilder queryBuilder = new StringBuilder("""
            SELECT t.id, t.type, t.category, t.amount, t.status, t.description, t.created_at
            FROM transactions t
            WHERE t.user_id = :userId
            """);
        
        if (type != null) queryBuilder.append(" AND t.type = :type");
        if (category != null) queryBuilder.append(" AND t.category = :category");
        if (status != null) queryBuilder.append(" AND t.status = :status");
        if (startDate != null) queryBuilder.append(" AND t.created_at >= :startDate");
        if (endDate != null) queryBuilder.append(" AND t.created_at <= :endDate");
        
        queryBuilder.append(" ORDER BY t.created_at DESC LIMIT :limit OFFSET :offset");
        
        var query = entityManager.createNativeQuery(queryBuilder.toString());
        query.setParameter("userId", userId);
        if (type != null) query.setParameter("type", type);
        if (category != null) query.setParameter("category", category);
        if (status != null) query.setParameter("status", status);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);
        query.setParameter("limit", size);
        query.setParameter("offset", page * size);
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        
        List<TransactionResponse> transactions = results.stream()
            .map(row -> new TransactionResponse(
                (UUID) row[0], (String) row[1], (String) row[2],
                (BigDecimal) row[3], (String) row[4], (String) row[5],
                (LocalDateTime) row[6]
            ))
            .toList();
        
        String countQuery = "SELECT COUNT(*) FROM transactions t WHERE t.user_id = :userId";
        Long total = (Long) entityManager.createNativeQuery(countQuery)
            .setParameter("userId", userId)
            .getSingleResult();
        
        return new PagedResult<>(transactions, total.intValue(), page, size);
    }

    @Override
    public TransactionDetailResponse getTransactionDetails(UUID userId, UUID transactionId) {
        logger.info("Getting transaction details: " + transactionId);
        
        String query = """
            SELECT t.id, t.type, t.category, t.amount, t.status, t.description,
                t.reference, t.booking_id, t.created_at, t.completed_at
            FROM transactions t
            WHERE t.id = :transactionId AND t.user_id = :userId
            """;
        
        Object[] result = (Object[]) entityManager.createNativeQuery(query)
            .setParameter("transactionId", transactionId)
            .setParameter("userId", userId)
            .getSingleResult();
        
        return new TransactionDetailResponse(
            (UUID) result[0], (String) result[1], (String) result[2],
            (BigDecimal) result[3], (String) result[4], (String) result[5],
            (String) result[6], (UUID) result[7], (LocalDateTime) result[8],
            (LocalDateTime) result[9]
        );
    }

    @Override
    public BankListResponse getNigerianBanks() {
        logger.info("Getting Nigerian banks list");
        
        List<BankInfo> banks = paystackService.getNigerianBanks().stream()
            .map(bank -> new BankInfo(bank.name(), bank.code(), "NG"))
            .toList();
        
        return new BankListResponse(banks);
    }

    @Override
    public AccountVerificationResponse verifyBankAccount(String accountNumber, String bankCode) {
        logger.info("Verifying bank account: " + accountNumber);
        
        try {
            String accountName = paystackService.resolveAccountNumber(accountNumber, bankCode);
            
            return new AccountVerificationResponse(
                accountName != null && !accountName.isEmpty(),
                accountName,
                accountNumber,
                bankCode
            );
        } catch (Exception e) {
            logger.warning("Account verification failed: " + e.getMessage());
            return new AccountVerificationResponse(false, null, accountNumber, bankCode);
        }
    }

    // ============================================================
    // 12. ADD MISSING METHOD: getWalletStatus
    // ============================================================
    @Override
    public WalletStatusResponse getWalletStatus(UUID userId) {
        logger.info("Getting wallet status for user: " + userId);
        
        String query = """
            SELECT is_blocked, block_reason, blocked_at 
            FROM wallets 
            WHERE user_id = :userId
            """;
        
        Object[] result = (Object[]) entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .getSingleResult();
        
        Boolean isBlocked = (Boolean) result[0];
        
        return new WalletStatusResponse(
            isBlocked != null && isBlocked ? "BLOCKED" : "ACTIVE",
            isBlocked != null && isBlocked,
            (String) result[1],
            (LocalDateTime) result[2]
        );
    }

    // ============================================================
    // 13. ADD MISSING METHOD: blockWallet
    // ============================================================
    @Override
    public void blockWallet(UUID userId, String reason) {
        logger.info("Blocking wallet for user: " + userId);
        
        entityManager.createNativeQuery("""
            UPDATE wallets 
            SET is_blocked = true, block_reason = :reason, blocked_at = :blockedAt
            WHERE user_id = :userId
            """)
            .setParameter("reason", reason)
            .setParameter("blockedAt", LocalDateTime.now())
            .setParameter("userId", userId)
            .executeUpdate();
    }

    // ============================================================
    // 14. ADD MISSING METHOD: unblockWallet
    // ============================================================
    @Override
    public void unblockWallet(UUID userId) {
        logger.info("Unblocking wallet for user: " + userId);
        
        entityManager.createNativeQuery("""
            UPDATE wallets 
            SET is_blocked = false, block_reason = NULL, blocked_at = NULL
            WHERE user_id = :userId
            """)
            .setParameter("userId", userId)
            .executeUpdate();
    }

    // ============================================================
    // 15. ADD MISSING METHOD: adjustWalletBalance
    // ============================================================
    @Override
    public void adjustWalletBalance(UUID userId, BigDecimal amount, String reason, UUID adminId) {
        logger.info("Adjusting wallet balance for user: " + userId);
        
        createTransaction(
            userId,
            amount.compareTo(BigDecimal.ZERO) > 0 ? "CREDIT" : "DEBIT",
            "ADJUSTMENT",
            amount.abs(),
            BigDecimal.ZERO,
            amount.abs(),
            "Admin adjustment: " + reason,
            "COMPLETED",
            generateTransactionReference(),
            null
        );
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