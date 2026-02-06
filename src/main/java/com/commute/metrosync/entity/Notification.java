package com.commute.metrosync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Notification entity - In-app alerts and push notifications
 * Supports multiple delivery channels and deep linking
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user_id", columnList = "user_id"),
    @Index(name = "idx_notifications_booking_id", columnList = "booking_id"),
    @Index(name = "idx_notifications_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_notifications_type", columnList = "type"),
    @Index(name = "idx_notifications_created_at", columnList = "created_at"),
    @Index(name = "idx_notifications_is_read", columnList = "is_read"),
    @Index(name = "idx_notifications_priority", columnList = "priority")
})
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Notification Content
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    // Notification Type
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    // Priority
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private Priority priority = Priority.NORMAL;

    // Related Entities
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    // Delivery Status
    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "is_sent")
    private Boolean isSent = false;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // Delivery Channels
    @Column(name = "delivery_channels")
    private String[] deliveryChannels = {"IN_APP"};  // IN_APP, PUSH, SMS, EMAIL

    // Click Action (Deep linking)
    @Column(name = "action_type", length = 50)
    private String actionType;  // VIEW_BOOKING, VIEW_TRANSACTION, VIEW_PROFILE, etc.

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_data", columnDefinition = "jsonb")
    private Map<String, Object> actionData;

    // Expiry
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // Enums
    public enum NotificationType {
        // Booking related
        BOOKING_CONFIRMED,
        BOOKING_CANCELLED,
        RIDE_STARTED,
        RIDE_COMPLETED,
        DRIVER_ARRIVED,
        
        // Payment related
        PAYMENT_RECEIVED,
        PAYMENT_FAILED,
        WALLET_TOPUP,
        WALLET_WITHDRAWAL,
        
        // Verification related
        VERIFICATION_APPROVED,
        VERIFICATION_REJECTED,
        DOCUMENT_EXPIRING,
        
        // System
        SYSTEM_ANNOUNCEMENT,
        PROMO_OFFER,
        RATING_REQUEST
    }

    public enum Priority {
        LOW, NORMAL, HIGH, URGENT
    }

    // Business Methods
    
    public boolean isRead() {
        return Boolean.TRUE.equals(isRead);
    }

    public boolean isSent() {
        return Boolean.TRUE.equals(isSent);
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean shouldSendPush() {
        if (deliveryChannels == null) return false;
        for (String channel : deliveryChannels) {
            if ("PUSH".equals(channel)) return true;
        }
        return false;
    }

    public boolean shouldSendSMS() {
        if (deliveryChannels == null) return false;
        for (String channel : deliveryChannels) {
            if ("SMS".equals(channel)) return true;
        }
        return false;
    }

    public boolean shouldSendEmail() {
        if (deliveryChannels == null) return false;
        for (String channel : deliveryChannels) {
            if ("EMAIL".equals(channel)) return true;
        }
        return false;
    }

    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    public void markAsSent() {
        this.isSent = true;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * Create a booking notification
     */
    public static Notification createBookingNotification(
        User user,
        Booking booking,
        NotificationType type,
        String title,
        String message
    ) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setBooking(booking);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setActionType("VIEW_BOOKING");
        notification.setActionData(Map.of("bookingId", booking.getId().toString()));
        
        // Set delivery channels based on type
        if (type == NotificationType.RIDE_STARTED || type == NotificationType.DRIVER_ARRIVED) {
            notification.setDeliveryChannels(new String[]{"IN_APP", "PUSH"});
            notification.setPriority(Priority.HIGH);
        } else {
            notification.setDeliveryChannels(new String[]{"IN_APP"});
        }
        
        return notification;
    }

    /**
     * Create a payment notification
     */
    public static Notification createPaymentNotification(
        User user,
        Transaction transaction,
        NotificationType type,
        String title,
        String message
    ) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTransaction(transaction);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setActionType("VIEW_TRANSACTION");
        notification.setActionData(Map.of("transactionId", transaction.getId().toString()));
        notification.setDeliveryChannels(new String[]{"IN_APP", "PUSH"});
        
        return notification;
    }

    // Getters and Setters
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }

    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }

    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public Boolean getIsSent() { return isSent; }
    public void setIsSent(Boolean isSent) { this.isSent = isSent; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public String[] getDeliveryChannels() { return deliveryChannels; }
    public void setDeliveryChannels(String[] deliveryChannels) { 
        this.deliveryChannels = deliveryChannels; 
    }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public Map<String, Object> getActionData() { return actionData; }
    public void setActionData(Map<String, Object> actionData) { this.actionData = actionData; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}