package com.commute.metrosync.service;

import com.commute.metrosync.entity.Notification;
import com.commute.metrosync.repository.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;

/**
 * Push Notification Service using Firebase Cloud Messaging (FCM)
 * Handles sending push notifications to mobile devices
 */
@ApplicationScoped
public class PushNotificationService {

    private static final Logger LOG = Logger.getLogger(PushNotificationService.class);
    private static final String FCM_API_URL = "https://fcm.googleapis.com/fcm/send";

    @ConfigProperty(name = "fcm.server.key")
    String serverKey;

    @ConfigProperty(name = "fcm.enabled", defaultValue = "true")
    boolean fcmEnabled;

    @Inject
    NotificationRepository notificationRepository;

    /**
     * Send push notification to a single device
     */
    public PushNotificationResult sendToDevice(
        String deviceToken,
        String title,
        String body,
        Map<String, String> data
    ) {
        if (!fcmEnabled) {
            LOG.warn("FCM is disabled");
            return new PushNotificationResult(false, 0, 1, "FCM disabled");
        }

        try {
            Map<String, Object> payload = buildFcmPayload(deviceToken, title, body, data, false);
            return sendFcmRequest(payload);
        } catch (Exception e) {
            LOG.error("Error sending push notification", e);
            return new PushNotificationResult(false, 0, 1, e.getMessage());
        }
    }

    /**
     * Send push notification to multiple devices
     */
    public PushNotificationResult sendToDevices(
        List<String> deviceTokens,
        String title,
        String body,
        Map<String, String> data
    ) {
        if (!fcmEnabled) {
            return new PushNotificationResult(false, 0, deviceTokens.size(), "FCM disabled");
        }

        int successCount = 0;
        int failureCount = 0;

        for (String token : deviceTokens) {
            PushNotificationResult result = sendToDevice(token, title, body, data);
            if (result.success) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        return new PushNotificationResult(
            successCount > 0,
            successCount,
            failureCount,
            String.format("Sent to %d/%d devices", successCount, deviceTokens.size())
        );
    }

    /**
     * Send push notification from Notification entity
     */
    public PushNotificationResult sendNotification(Notification notification, String deviceToken) {
        if (!notification.shouldSendPush()) {
            return new PushNotificationResult(false, 0, 0, "Push not enabled for this notification");
        }

        Map<String, String> data = new HashMap<>();
        data.put("notification_id", notification.getId().toString());
        data.put("type", notification.getType().name());
        
        if (notification.getActionType() != null) {
            data.put("action_type", notification.getActionType());
        }
        
        if (notification.getActionData() != null) {
            notification.getActionData().forEach((key, value) -> 
                data.put(key, value.toString())
            );
        }

        PushNotificationResult result = sendToDevice(
            deviceToken,
            notification.getTitle(),
            notification.getMessage(),
            data
        );

        if (result.success) {
            notification.markAsSent();
            notificationRepository.persist(notification);
        }

        return result;
    }

    /**
     * Send notification to topic (for broadcasts)
     */
    public PushNotificationResult sendToTopic(
        String topic,
        String title,
        String body,
        Map<String, String> data
    ) {
        if (!fcmEnabled) {
            return new PushNotificationResult(false, 0, 0, "FCM disabled");
        }

        try {
            Map<String, Object> payload = buildFcmPayload(topic, title, body, data, true);
            return sendFcmRequest(payload);
        } catch (Exception e) {
            LOG.error("Error sending topic notification", e);
            return new PushNotificationResult(false, 0, 0, e.getMessage());
        }
    }

    /**
     * Subscribe device to topic
     */
    public boolean subscribeToTopic(String deviceToken, String topic) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("to", "/topics/" + topic);
            payload.put("registration_tokens", List.of(deviceToken));

            Client client = ClientBuilder.newClient();
            Response response = client.target("https://iid.googleapis.com/iid/v1/batch Add")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "key=" + serverKey)
                .post(Entity.json(payload));

            int status = response.getStatus();
            return status == 200;
        } catch (Exception e) {
            LOG.error("Error subscribing to topic", e);
            return false;
        }
    }

    /**
     * Unsubscribe device from topic
     */
    public boolean unsubscribeFromTopic(String deviceToken, String topic) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("to", "/topics/" + topic);
            payload.put("registration_tokens", List.of(deviceToken));

            Client client = ClientBuilder.newClient();
            Response response = client.target("https://iid.googleapis.com/iid/v1/batchRemove")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "key=" + serverKey)
                .post(Entity.json(payload));

            int status = response.getStatus();
            return status == 200;
        } catch (Exception e) {
            LOG.error("Error unsubscribing from topic", e);
            return false;
        }
    }

    /**
     * Send high priority notification (for urgent alerts)
     */
    public PushNotificationResult sendUrgentNotification(
        String deviceToken,
        String title,
        String body,
        Map<String, String> data
    ) {
        Map<String, Object> payload = buildFcmPayload(deviceToken, title, body, data, false);
        payload.put("priority", "high");
        
        // Android-specific options for high priority
        Map<String, Object> android = new HashMap<>();
        android.put("priority", "high");
        payload.put("android", android);
        
        return sendFcmRequest(payload);
    }

    /**
     * Build FCM payload
     */
    private Map<String, Object> buildFcmPayload(
        String target,
        String title,
        String body,
        Map<String, String> data,
        boolean isTopic
    ) {
        Map<String, Object> payload = new HashMap<>();
        
        // Target
        if (isTopic) {
            payload.put("to", "/topics/" + target);
        } else {
            payload.put("to", target);
        }
        
        // Notification
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("body", body);
        notification.put("sound", "default");
        notification.put("badge", "1");
        payload.put("notification", notification);
        
        // Data
        if (data != null && !data.isEmpty()) {
            payload.put("data", data);
        }
        
        return payload;
    }

    /**
     * Send FCM HTTP request
     */
    private PushNotificationResult sendFcmRequest(Map<String, Object> payload) {
        try {
            Client client = ClientBuilder.newClient();
            Response response = client.target(FCM_API_URL)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "key=" + serverKey)
                .header("Content-Type", "application/json")
                .post(Entity.json(payload));

            int status = response.getStatus();
            Map<String, Object> responseData = response.readEntity(Map.class);

            if (status == 200) {
                Integer success = (Integer) responseData.getOrDefault("success", 0);
                Integer failure = (Integer) responseData.getOrDefault("failure", 0);
                
                LOG.infof("FCM notification sent. Success: %d, Failure: %d", success, failure);
                return new PushNotificationResult(
                    success > 0,
                    success,
                    failure,
                    "Notification sent"
                );
            } else {
                LOG.errorf("FCM request failed. Status: %d, Response: %s", status, responseData);
                return new PushNotificationResult(false, 0, 1, "FCM request failed");
            }
        } catch (Exception e) {
            LOG.error("Error in FCM request", e);
            return new PushNotificationResult(false, 0, 1, e.getMessage());
        }
    }

    /**
     * Build notification data for booking updates
     */
    public static Map<String, String> buildBookingNotificationData(UUID bookingId, String action) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "booking");
        data.put("booking_id", bookingId.toString());
        data.put("action", action);
        return data;
    }

    /**
     * Build notification data for payment updates
     */
    public static Map<String, String> buildPaymentNotificationData(UUID transactionId, String status) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "payment");
        data.put("transaction_id", transactionId.toString());
        data.put("status", status);
        return data;
    }

    /**
     * Build notification data for driver updates
     */
    public static Map<String, String> buildDriverNotificationData(UUID driverId, String status) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "driver");
        data.put("driver_id", driverId.toString());
        data.put("status", status);
        return data;
    }

    // Result class
    public static class PushNotificationResult {
        public final boolean success;
        public final int successCount;
        public final int failureCount;
        public final String message;

        public PushNotificationResult(boolean success, int successCount, int failureCount, String message) {
            this.success = success;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.message = message;
        }

        public int getTotalCount() {
            return successCount + failureCount;
        }

        public double getSuccessRate() {
            int total = getTotalCount();
            return total > 0 ? (successCount * 100.0) / total : 0.0;
        }
    }
}