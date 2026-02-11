package com.commute.metrosync.service;

import com.google.firebase.messaging.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Firebase Cloud Messaging Service
 * Handles push notifications to mobile devices
 */
@ApplicationScoped
public class FirebaseMessagingService {

    private static final Logger LOG = Logger.getLogger(FirebaseMessagingService.class);

    /**
     * Send notification to a single device
     * 
     * @param deviceToken FCM device token
     * @param title Notification title
     * @param body Notification body
     * @param data Custom data payload
     * @return Message ID if successful
     */
    public String sendNotification(String deviceToken, String title, String body, Map<String, String> data) {
        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            // Add custom data if provided
            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            LOG.infof("Successfully sent notification: %s", response);
            return response;

        } catch (FirebaseMessagingException e) {
            LOG.errorf("Error sending notification to %s: %s", maskToken(deviceToken), e.getMessage());
            throw new RuntimeException("Failed to send notification", e);
        }
    }

    /**
     * Send notification with image
     */
    public String sendNotificationWithImage(String deviceToken, String title, String body, 
                                           String imageUrl, Map<String, String> data) {
        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .setImage(imageUrl)
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            LOG.infof("Successfully sent notification with image: %s", response);
            return response;

        } catch (FirebaseMessagingException e) {
            LOG.errorf("Error sending notification: %s", e.getMessage());
            throw new RuntimeException("Failed to send notification with image", e);
        }
    }

    /**
     * Send booking notification
     */
    public String sendBookingNotification(String deviceToken, String bookingId, String status, 
                                         String title, String body) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "BOOKING");
        data.put("bookingId", bookingId);
        data.put("status", status);
        data.put("action", "VIEW_BOOKING");

        return sendNotification(deviceToken, title, body, data);
    }

    /**
     * Send trip status notification
     */
    public String sendTripStatusNotification(String deviceToken, String bookingId, 
                                            String status, String message) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "TRIP_STATUS");
        data.put("bookingId", bookingId);
        data.put("status", status);

        String title = switch (status) {
            case "CONFIRMED" -> "Ride Confirmed!";
            case "DRIVER_ARRIVED" -> "Driver Has Arrived";
            case "IN_PROGRESS" -> "Trip Started";
            case "COMPLETED" -> "Trip Completed";
            case "CANCELLED" -> "Ride Cancelled";
            default -> "Trip Update";
        };

        return sendNotification(deviceToken, title, message, data);
    }

    /**
     * Send payment notification
     */
    public String sendPaymentNotification(String deviceToken, String transactionId, 
                                         String amount, String status) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "PAYMENT");
        data.put("transactionId", transactionId);
        data.put("amount", amount);
        data.put("status", status);

        String title = status.equals("SUCCESS") ? "Payment Successful" : "Payment Update";
        String body = String.format("Your payment of ₦%s was %s", amount, status.toLowerCase());

        return sendNotification(deviceToken, title, body, data);
    }

    /**
     * Send multicast notification to multiple devices
     * 
     * @param deviceTokens List of FCM device tokens
     * @param title Notification title
     * @param body Notification body
     * @param data Custom data payload
     * @return Batch response with success/failure counts
     */
    public BatchResponse sendMulticastNotification(List<String> deviceTokens, String title, 
                                                   String body, Map<String, String> data) {
        try {
            MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                    .addAllTokens(deviceTokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            BatchResponse response = FirebaseMessaging.getInstance()
                    .sendEachForMulticast(messageBuilder.build());

            LOG.infof("Multicast notification sent. Success: %d, Failure: %d", 
                     response.getSuccessCount(), response.getFailureCount());

            // Log failed tokens
            if (response.getFailureCount() > 0) {
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        LOG.warnf("Failed to send to token %s: %s", 
                                 maskToken(deviceTokens.get(i)), 
                                 responses.get(i).getException().getMessage());
                    }
                }
            }

            return response;

        } catch (FirebaseMessagingException e) {
            LOG.errorf("Error sending multicast notification: %s", e.getMessage());
            throw new RuntimeException("Failed to send multicast notification", e);
        }
    }

    /**
     * Send notification to a topic (for broadcast)
     */
    public String sendToTopic(String topic, String title, String body, Map<String, String> data) {
        try {
            Message.Builder messageBuilder = Message.builder()
                    .setTopic(topic)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            LOG.infof("Successfully sent topic notification to '%s': %s", topic, response);
            return response;

        } catch (FirebaseMessagingException e) {
            LOG.errorf("Error sending topic notification: %s", e.getMessage());
            throw new RuntimeException("Failed to send topic notification", e);
        }
    }

    /**
     * Subscribe device tokens to a topic
     */
    public void subscribeToTopic(List<String> deviceTokens, String topic) {
        try {
            TopicManagementResponse response = FirebaseMessaging.getInstance()
                    .subscribeToTopic(deviceTokens, topic);
            
            LOG.infof("Subscribed %d devices to topic '%s'. Failures: %d", 
                     response.getSuccessCount(), topic, response.getFailureCount());

        } catch (FirebaseMessagingException e) {
            LOG.errorf("Error subscribing to topic: %s", e.getMessage());
            throw new RuntimeException("Failed to subscribe to topic", e);
        }
    }

    /**
     * Unsubscribe device tokens from a topic
     */
    public void unsubscribeFromTopic(List<String> deviceTokens, String topic) {
        try {
            TopicManagementResponse response = FirebaseMessaging.getInstance()
                    .unsubscribeFromTopic(deviceTokens, topic);
            
            LOG.infof("Unsubscribed %d devices from topic '%s'. Failures: %d", 
                     response.getSuccessCount(), topic, response.getFailureCount());

        } catch (FirebaseMessagingException e) {
            LOG.errorf("Error unsubscribing from topic: %s", e.getMessage());
            throw new RuntimeException("Failed to unsubscribe from topic", e);
        }
    }

    /**
     * Send silent data-only notification
     * Useful for background updates without alerting user
     */
    public String sendDataMessage(String deviceToken, Map<String, String> data) {
        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .putAllData(data)
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            LOG.infof("Successfully sent data message: %s", response);
            return response;

        } catch (FirebaseMessagingException e) {
            LOG.errorf("Error sending data message: %s", e.getMessage());
            throw new RuntimeException("Failed to send data message", e);
        }
    }

    /**
     * Send high-priority notification for time-sensitive updates
     */
    public String sendHighPriorityNotification(String deviceToken, String title, 
                                              String body, Map<String, String> data) {
        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setSound("default")
                                    .setPriority(AndroidNotification.Priority.HIGH)
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .build())
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            LOG.infof("Successfully sent high-priority notification: %s", response);
            return response;

        } catch (FirebaseMessagingException e) {
            LOG.errorf("Error sending high-priority notification: %s", e.getMessage());
            throw new RuntimeException("Failed to send high-priority notification", e);
        }
    }

    /**
     * Mask device token for logging (security)
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 8);
    }
}