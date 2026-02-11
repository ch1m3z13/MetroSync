package com.commute.metrosync.service;

import com.commute.metrosync.dto.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Booking Service Interface
 * Defines operations for booking management and trip lifecycle
 */
public interface BookingService {
    
    /**
     * Create a new booking
     * @param riderId ID of the rider making the booking
     * @param request Booking details
     * @return Created booking response
     */
    BookingResponse createBooking(UUID riderId, CreateBookingRequest request);
    
    /**
     * Confirm a booking (driver accepts)
     * @param bookingId Booking ID
     * @param driverId Driver confirming the booking
     * @return Updated booking response
     */
    BookingResponse confirmBooking(UUID bookingId, UUID driverId);
    
    /**
     * Cancel a booking
     * @param bookingId Booking ID
     * @param userId User cancelling (rider or driver)
     * @param reason Cancellation reason
     * @return Updated booking response
     */
    BookingResponse cancelBooking(UUID bookingId, UUID userId, String reason);
    
    /**
     * Start a trip (driver picks up rider)
     * @param bookingId Booking ID
     * @param driverId Driver starting the trip
     * @param safetyPin Safety PIN for verification
     * @return Updated booking response
     */
    BookingResponse startTrip(UUID bookingId, UUID driverId, String safetyPin);
    
    /**
     * Complete a trip (rider dropped off)
     * @param bookingId Booking ID
     * @param driverId Driver completing the trip
     * @return Updated booking response
     */
    BookingResponse completeTrip(UUID bookingId, UUID driverId);
    
    /**
     * Rate a booking
     * @param bookingId Booking ID
     * @param userId User rating (rider or driver)
     * @param rating Rating (1-5)
     * @param review Optional text review
     * @return Updated booking response
     */
    BookingResponse rateBooking(UUID bookingId, UUID userId, int rating, String review);
}

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
}

/**
 * Notification Service Interface
 * Defines operations for notification management
 */
public interface NotificationService {
    
    /**
     * Get notifications
     */
    PagedResult<NotificationResponse> getNotifications(
        UUID userId, boolean unreadOnly, String type, String priority, int page, int size
    );
    
    /**
     * Get notification details
     */
    NotificationResponse getNotification(UUID userId, UUID notificationId);
    
    /**
     * Get unread count
     */
    int getUnreadCount(UUID userId);
    
    /**
     * Mark notification as read
     */
    MarkReadResponse markAsRead(UUID userId, UUID notificationId);
    
    /**
     * Mark all notifications as read
     */
    int markAllAsRead(UUID userId);
    
    /**
     * Mark batch of notifications as read
     */
    int markBatchAsRead(UUID userId, List<UUID> notificationIds);
    
    /**
     * Delete notification
     */
    void deleteNotification(UUID userId, UUID notificationId);
    
    /**
     * Delete all read notifications
     */
    int deleteAllRead(UUID userId);
    
    /**
     * Get notification preferences
     */
    NotificationPreferencesResponse getPreferences(UUID userId);
    
    /**
     * Update notification preferences
     */
    NotificationPreferencesResponse updatePreferences(UUID userId, NotificationPreferencesRequest request);
    
    /**
     * Get notification statistics
     */
    NotificationStatisticsResponse getStatistics(UUID userId);
    
    /**
     * Send notification to a user
     */
    NotificationResponse sendNotification(
        UUID userId, String title, String message, String type, 
        String priority, Map<String, Object> data, String actionUrl
    );
    
    /**
     * Broadcast notification to multiple users
     */
    int broadcastNotification(
        List<UUID> userIds, String title, String message, 
        String type, String priority, Map<String, Object> data
    );
}

/**
 * Verification Service Interface  
 * Defines operations for user verification
 */
public interface VerificationService {
    
    /**
     * Submit identity verification
     */
    IdentityVerificationResponse submitIdentityVerification(
        UUID userId, IdentityVerificationRequest request
    );
    
    /**
     * Approve identity verification (admin)
     */
    void approveIdentityVerification(UUID userId, UUID adminId, String notes);
    
    /**
     * Reject identity verification (admin)
     */
    void rejectIdentityVerification(UUID userId, UUID adminId, String reason);
    
    /**
     * Submit employment verification
     */
    EmploymentVerificationResponse submitEmploymentVerification(
        UUID userId, EmploymentVerificationRequest request
    );
    
    /**
     * Submit driver documents
     */
    DriverDocumentsResponse submitDriverDocuments(
        UUID userId, DriverDocumentsRequest request
    );
    
    /**
     * Approve driver documents (admin)
     */
    void approveDriverDocuments(UUID userId, UUID adminId, String notes);
    
    /**
     * Reject driver documents (admin)
     */
    void rejectDriverDocuments(UUID userId, UUID adminId, String reason);
    
    /**
     * Get verification status
     */
    VerificationStatusResponse getVerificationStatus(UUID userId);
    
    /**
     * Check if user is fully verified
     */
    boolean isUserFullyVerified(UUID userId);
}

/**
 * Auth Service Interface
 * Defines authentication and user management operations
 */
public interface AuthService {
    
    /**
     * Register with OTP
     */
    AuthResponse registerWithOtp(RegisterRequest request);
    
    /**
     * Check if phone number is registered
     */
    boolean isPhoneNumberRegistered(String phoneNumber);
    
    /**
     * Check if email is registered
     */
    boolean isEmailRegistered(String email);
    
    /**
     * Login with credentials
     */
    AuthResponse login(LoginRequest request);
    
    /**
     * Login with OTP
     */
    AuthResponse loginWithOtp(String phoneNumber);
    
    /**
     * Reset password
     */
    void resetPassword(String phoneNumber, String newPassword);
    
    /**
     * Verify phone number
     */
    void verifyPhoneNumber(UUID userId, String phoneNumber);
    
    /**
     * Refresh access token
     */
    RefreshTokenResponse refreshToken(String refreshToken);
    
    /**
     * Logout (invalidate refresh token)
     */
    void logout(String refreshToken);
}

/**
 * OTP Service Interface
 * Defines OTP operations
 */
public interface OtpService {
    
    /**
     * Send OTP
     */
    SendOtpResponse sendOtp(String phoneNumber, String purpose, String ipAddress, String userAgent);
    
    /**
     * Verify OTP
     */
    VerifyOtpResponse verifyOtp(String phoneNumber, String otpCode, String purpose);
}
