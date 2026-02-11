package com.commute.metrosync.service;

import com.commute.metrosync.entity.Booking;
import com.commute.metrosync.entity.Notification;
import com.commute.metrosync.entity.Transaction;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NotificationService - Orchestrates multi-channel notifications
 * Handles in-app, push, and SMS notifications
 */
@ApplicationScoped
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class);

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    PushNotificationService pushNotificationService;

    @Inject
    TermiiService termiiService;

    /**
     * Send booking notification to user
     */
    @Transactional
    public Notification sendBookingNotification(
        User user,
        Booking booking,
        Notification.NotificationType type,
        String title,
        String message,
        String deviceToken
    ) {
        // Create in-app notification
        Notification notification = Notification.createBookingNotification(
            user, booking, type, title, message
        );
        notificationRepository.persist(notification);

        // Send push notification if enabled
        if (notification.shouldSendPush() && deviceToken != null) {
            pushNotificationService.sendNotification(notification, deviceToken);
        }

        // Send SMS for high-priority notifications
        if (notification.shouldSendSMS()) {
            String smsMessage = String.format("%s: %s", title, message);
            termiiService.sendNotificationSms(user.getPhoneNumber(), smsMessage);
        }

        LOG.infof("Notification sent to user %s: %s", user.getId(), type);
        return notification;
    }

    /**
     * Send payment notification
     */
    @Transactional
    public Notification sendPaymentNotification(
        User user,
        Transaction transaction,
        Notification.NotificationType type,
        String title,
        String message,
        String deviceToken
    ) {
        Notification notification = Notification.createPaymentNotification(
            user, transaction, type, title, message
        );
        notificationRepository.persist(notification);

        // Send push notification
        if (notification.shouldSendPush() && deviceToken != null) {
            pushNotificationService.sendNotification(notification, deviceToken);
        }

        LOG.infof("Payment notification sent to user %s", user.getId());
        return notification;
    }

    /**
     * Send verification notification
     */
    @Transactional
    public Notification sendVerificationNotification(
        User user,
        Notification.NotificationType type,
        String title,
        String message,
        String deviceToken
    ) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setDeliveryChannels(new String[]{"IN_APP", "PUSH"});
        notification.setPriority(Notification.Priority.HIGH);
        
        notificationRepository.persist(notification);

        // Send push notification
        if (deviceToken != null) {
            pushNotificationService.sendNotification(notification, deviceToken);
        }

        LOG.infof("Verification notification sent to user %s", user.getId());
        return notification;
    }

    /**
     * Send system announcement to all users
     */
    @Transactional
    public int sendSystemAnnouncement(String title, String message, String topic) {
        // Send push notification to topic
        pushNotificationService.sendToTopic(topic, title, message, Map.of("type", "announcement"));
        
        LOG.infof("System announcement sent to topic: %s", topic);
        return 1; // Would return count if we persisted per-user notifications
    }

    /**
     * Send document expiry reminder
     */
    @Transactional
    public Notification sendDocumentExpiryReminder(
        User user,
        String documentType,
        LocalDateTime expiryDate,
        String deviceToken
    ) {
        String title = "Document Expiring Soon";
        String message = String.format(
            "Your %s will expire on %s. Please renew it to continue driving.",
            documentType,
            expiryDate.toLocalDate()
        );

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(Notification.NotificationType.DOCUMENT_EXPIRING);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setDeliveryChannels(new String[]{"IN_APP", "PUSH", "SMS"});
        notification.setPriority(Notification.Priority.HIGH);
        notification.setExpiresAt(expiryDate);
        
        notificationRepository.persist(notification);

        // Send multi-channel
        if (deviceToken != null) {
            pushNotificationService.sendNotification(notification, deviceToken);
        }
        termiiService.sendNotificationSms(user.getPhoneNumber(), message);

        LOG.infof("Document expiry reminder sent to user %s", user.getId());
        return notification;
    }

    /**
     * Get user's unread notifications
     */
    public List<Notification> getUnreadNotifications(UUID userId) {
        return notificationRepository.findUnreadByUserId(userId);
    }

    /**
     * Get user's unread notification count
     */
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Mark notification as read
     */
    @Transactional
    public boolean markAsRead(UUID notificationId) {
        return notificationRepository.markAsRead(notificationId);
    }

    /**
     * Mark all notifications as read for user
     */
    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsReadForUser(userId);
    }

    /**
     * Delete expired notifications (cleanup job)
     */
    @Transactional
    public long deleteExpiredNotifications() {
        long deleted = notificationRepository.deleteExpired();
        LOG.infof("Deleted %d expired notifications", deleted);
        return deleted;
    }

    /**
     * Delete old read notifications (cleanup job)
     */
    @Transactional
    public long deleteOldReadNotifications(int daysToKeep) {
        long deleted = notificationRepository.deleteOldRead(daysToKeep);
        LOG.infof("Deleted %d old read notifications", deleted);
        return deleted;
    }

    /**
     * Send unsent push notifications (recovery job)
     */
    @Transactional
    public int sendPendingPushNotifications() {
        List<Notification> pending = notificationRepository.findUnsentPushNotifications();
        int sentCount = 0;

        for (Notification notification : pending) {
            // In production, you'd look up user's device token from a separate table
            // For now, we'll just mark them as attempted
            notification.markAsSent();
            notificationRepository.persist(notification);
            sentCount++;
        }

        LOG.infof("Processed %d pending push notifications", sentCount);
        return sentCount;
    }

    /**
     * Helper: Create notification for booking confirmed
     */
    public Notification notifyBookingConfirmed(User user, Booking booking, String deviceToken) {
        return sendBookingNotification(
            user,
            booking,
            Notification.NotificationType.BOOKING_CONFIRMED,
            "Booking Confirmed",
            String.format("Your ride on %s has been confirmed!", 
                booking.getScheduledPickupTime().toLocalDate()),
            deviceToken
        );
    }

    /**
     * Helper: Create notification for ride started
     */
    public Notification notifyRideStarted(User user, Booking booking, String deviceToken) {
        return sendBookingNotification(
            user,
            booking,
            Notification.NotificationType.RIDE_STARTED,
            "Ride Started",
            "Your ride has started. Have a safe journey!",
            deviceToken
        );
    }

    /**
     * Helper: Create notification for payment received
     */
    public Notification notifyPaymentReceived(User user, Transaction transaction, String deviceToken) {
        return sendPaymentNotification(
            user,
            transaction,
            Notification.NotificationType.PAYMENT_RECEIVED,
            "Payment Received",
            String.format("₦%.2f has been credited to your wallet", 
                transaction.getAmountInNaira()),
            deviceToken
        );
    }

    /**
     * Helper: Create notification for wallet top-up
     */
    public Notification notifyWalletTopUp(User user, Transaction transaction, String deviceToken) {
        return sendPaymentNotification(
            user,
            transaction,
            Notification.NotificationType.WALLET_TOPUP,
            "Wallet Topped Up",
            String.format("Your wallet has been credited with ₦%.2f", 
                transaction.getAmountInNaira()),
            deviceToken
        );
    }
}