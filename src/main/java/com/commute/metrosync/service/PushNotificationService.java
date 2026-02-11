package com.commute.metrosync.service;

import com.commute.metrosync.entity.Notification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Push Notification Service
 * Wrapper around Firebase Messaging Service for app-specific notifications
 */
@ApplicationScoped
public class PushNotificationService {

    private static final Logger LOG = Logger.getLogger(PushNotificationService.class);

    @Inject
    FirebaseMessagingService firebaseMessaging;

    /**
     * Send notification from Notification entity
     */
    public void sendNotification(Notification notification, String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            LOG.warn("Cannot send notification - device token is null or empty");
            return;
        }

        try {
            Map<String, String> data = buildDataPayload(notification);
            
            // Use high-priority for critical notifications
            if (notification.getPriority() == Notification.Priority.CRITICAL || 
                notification.getPriority() == Notification.Priority.HIGH) {
                firebaseMessaging.sendHighPriorityNotification(
                    deviceToken, 
                    notification.getTitle(), 
                    notification.getMessage(), 
                    data
                );
            } else {
                firebaseMessaging.sendNotification(
                    deviceToken, 
                    notification.getTitle(), 
                    notification.getMessage(), 
                    data
                );
            }

            LOG.infof("Push notification sent for notification ID: %s", notification.getId());

        } catch (Exception e) {
            LOG.errorf("Failed to send push notification: %s", e.getMessage());
        }
    }

    /**
     * Send booking notification
     */
    public void sendBookingNotification(String deviceToken, String bookingId, 
                                       String status, String title, String message) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            return;
        }

        try {
            firebaseMessaging.sendBookingNotification(deviceToken, bookingId, status, title, message);
            LOG.infof("Booking notification sent for booking: %s", bookingId);
        } catch (Exception e) {
            LOG.errorf("Failed to send booking notification: %s", e.getMessage());
        }
    }

    /**
     * Send trip status update
     */
    public void sendTripStatusUpdate(String deviceToken, String bookingId, 
                                    String status, String message) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            return;
        }

        try {
            firebaseMessaging.sendTripStatusNotification(deviceToken, bookingId, status, message);
            LOG.infof("Trip status notification sent: %s", status);
        } catch (Exception e) {
            LOG.errorf("Failed to send trip status notification: %s", e.getMessage());
        }
    }

    /**
     * Send payment notification
     */
    public void sendPaymentNotification(String deviceToken, String transactionId, 
                                       String amount, String status) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            return;
        }

        try {
            firebaseMessaging.sendPaymentNotification(deviceToken, transactionId, amount, status);
            LOG.infof("Payment notification sent for transaction: %s", transactionId);
        } catch (Exception e) {
            LOG.errorf("Failed to send payment notification: %s", e.getMessage());
        }
    }

    /**
     * Send driver arrival notification
     */
    public void sendDriverArrivalNotification(String deviceToken, String bookingId, 
                                            String driverName, String vehicleInfo) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            return;
        }

        try {
            String title = "Your Driver Has Arrived!";
            String message = String.format("%s is waiting for you in %s", driverName, vehicleInfo);
            
            Map<String, String> data = new HashMap<>();
            data.put("type", "DRIVER_ARRIVAL");
            data.put("bookingId", bookingId);
            data.put("action", "VIEW_BOOKING");

            firebaseMessaging.sendHighPriorityNotification(deviceToken, title, message, data);
            LOG.infof("Driver arrival notification sent for booking: %s", bookingId);

        } catch (Exception e) {
            LOG.errorf("Failed to send driver arrival notification: %s", e.getMessage());
        }
    }

    /**
     * Send verification status notification
     */
    public void sendVerificationNotification(String deviceToken, String verificationType, 
                                           String status, String message) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            return;
        }

        try {
            String title = status.equals("APPROVED") ? 
                "Verification Approved ✓" : 
                "Verification Update";
            
            Map<String, String> data = new HashMap<>();
            data.put("type", "VERIFICATION");
            data.put("verificationType", verificationType);
            data.put("status", status);
            data.put("action", "VIEW_PROFILE");

            firebaseMessaging.sendNotification(deviceToken, title, message, data);
            LOG.infof("Verification notification sent: %s - %s", verificationType, status);

        } catch (Exception e) {
            LOG.errorf("Failed to send verification notification: %s", e.getMessage());
        }
    }

    /**
     * Send location update (silent data message)
     */
    public void sendLocationUpdate(String deviceToken, String bookingId, 
                                  double latitude, double longitude) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            return;
        }

        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "LOCATION_UPDATE");
            data.put("bookingId", bookingId);
            data.put("latitude", String.valueOf(latitude));
            data.put("longitude", String.valueOf(longitude));

            firebaseMessaging.sendDataMessage(deviceToken, data);

        } catch (Exception e) {
            LOG.errorf("Failed to send location update: %s", e.getMessage());
        }
    }

    /**
     * Build data payload from notification entity
     */
    private Map<String, String> buildDataPayload(Notification notification) {
        Map<String, String> data = new HashMap<>();
        
        data.put("notificationId", notification.getId().toString());
        data.put("type", notification.getType().toString());
        data.put("priority", notification.getPriority().toString());
        
        if (notification.getBookingId() != null) {
            data.put("bookingId", notification.getBookingId().toString());
            data.put("action", "VIEW_BOOKING");
        }
        
        if (notification.getTransactionId() != null) {
            data.put("transactionId", notification.getTransactionId().toString());
            data.put("action", "VIEW_TRANSACTION");
        }
        
        if (notification.getActionUrl() != null) {
            data.put("actionUrl", notification.getActionUrl());
        }
        
        return data;
    }
}