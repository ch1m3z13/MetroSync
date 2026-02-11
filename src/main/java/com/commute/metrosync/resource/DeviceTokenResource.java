package com.commute.metrosync.resource;

import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.service.FirebaseMessagingService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Device Token Management Resource
 * Handles device token registration and push notification preferences
 */
@Path("/api/device-tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeviceTokenResource {

    private static final Logger LOG = Logger.getLogger(DeviceTokenResource.class);

    @Inject
    UserRepository userRepository;

    @Inject
    FirebaseMessagingService firebaseMessaging;

    @Inject
    JsonWebToken jwt;

    /**
     * Register or update device token
     * 
     * POST /api/device-tokens
     * Body: {
     *   "deviceToken": "fcm-token-here",
     *   "platform": "ANDROID" // or "IOS"
     * }
     */
    @POST
    @RolesAllowed({"RIDER", "DRIVER"})
    @Transactional
    public Response registerDeviceToken(DeviceTokenRequest request) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId);

            if (user == null) {
                return Response.status(404)
                    .entity(Map.of("error", "User not found"))
                    .build();
            }

            // Update device token
            user.setDeviceToken(request.deviceToken());
            user.setDevicePlatform(request.platform());
            user.setDeviceUpdatedAt(LocalDateTime.now());
            user.setPushEnabled(true);

            userRepository.persist(user);

            LOG.infof("Device token registered for user %s: platform=%s", 
                     userId, request.platform());

            return Response.ok(Map.of(
                "success", true,
                "message", "Device token registered successfully"
            )).build();

        } catch (Exception e) {
            LOG.error("Failed to register device token", e);
            return Response.status(500)
                .entity(Map.of("error", "Failed to register device token"))
                .build();
        }
    }

    /**
     * Remove device token (logout)
     * 
     * DELETE /api/device-tokens
     */
    @DELETE
    @RolesAllowed({"RIDER", "DRIVER"})
    @Transactional
    public Response removeDeviceToken() {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId);

            if (user == null) {
                return Response.status(404)
                    .entity(Map.of("error", "User not found"))
                    .build();
            }

            user.setDeviceToken(null);
            user.setDevicePlatform(null);
            user.setPushEnabled(false);

            userRepository.persist(user);

            LOG.infof("Device token removed for user %s", userId);

            return Response.ok(Map.of(
                "success", true,
                "message", "Device token removed successfully"
            )).build();

        } catch (Exception e) {
            LOG.error("Failed to remove device token", e);
            return Response.status(500)
                .entity(Map.of("error", "Failed to remove device token"))
                .build();
        }
    }

    /**
     * Update push notification preferences
     * 
     * PUT /api/device-tokens/preferences
     * Body: {
     *   "pushEnabled": true
     * }
     */
    @PUT
    @Path("/preferences")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Transactional
    public Response updatePushPreferences(PushPreferencesRequest request) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId);

            if (user == null) {
                return Response.status(404)
                    .entity(Map.of("error", "User not found"))
                    .build();
            }

            user.setPushEnabled(request.pushEnabled());
            userRepository.persist(user);

            LOG.infof("Push preferences updated for user %s: enabled=%s", 
                     userId, request.pushEnabled());

            return Response.ok(Map.of(
                "success", true,
                "pushEnabled", user.isPushEnabled()
            )).build();

        } catch (Exception e) {
            LOG.error("Failed to update push preferences", e);
            return Response.status(500)
                .entity(Map.of("error", "Failed to update preferences"))
                .build();
        }
    }

    /**
     * Get current device token info
     * 
     * GET /api/device-tokens
     */
    @GET
    @RolesAllowed({"RIDER", "DRIVER"})
    public Response getDeviceTokenInfo() {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId);

            if (user == null) {
                return Response.status(404)
                    .entity(Map.of("error", "User not found"))
                    .build();
            }

            return Response.ok(Map.of(
                "hasDeviceToken", user.getDeviceToken() != null,
                "platform", user.getDevicePlatform() != null ? user.getDevicePlatform() : "",
                "pushEnabled", user.isPushEnabled(),
                "lastUpdated", user.getDeviceUpdatedAt() != null ? 
                               user.getDeviceUpdatedAt().toString() : null
            )).build();

        } catch (Exception e) {
            LOG.error("Failed to get device token info", e);
            return Response.status(500)
                .entity(Map.of("error", "Failed to get device token info"))
                .build();
        }
    }

    /**
     * Subscribe to topic (for broadcast notifications)
     * 
     * POST /api/device-tokens/topics/{topic}/subscribe
     */
    @POST
    @Path("/topics/{topic}/subscribe")
    @RolesAllowed({"RIDER", "DRIVER"})
    public Response subscribeToTopic(@PathParam("topic") String topic) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId);

            if (user == null || user.getDeviceToken() == null) {
                return Response.status(400)
                    .entity(Map.of("error", "No device token registered"))
                    .build();
            }

            firebaseMessaging.subscribeToTopic(
                List.of(user.getDeviceToken()), 
                topic
            );

            LOG.infof("User %s subscribed to topic: %s", userId, topic);

            return Response.ok(Map.of(
                "success", true,
                "topic", topic
            )).build();

        } catch (Exception e) {
            LOG.error("Failed to subscribe to topic", e);
            return Response.status(500)
                .entity(Map.of("error", "Failed to subscribe to topic"))
                .build();
        }
    }

    /**
     * Unsubscribe from topic
     * 
     * POST /api/device-tokens/topics/{topic}/unsubscribe
     */
    @POST
    @Path("/topics/{topic}/unsubscribe")
    @RolesAllowed({"RIDER", "DRIVER"})
    public Response unsubscribeFromTopic(@PathParam("topic") String topic) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId);

            if (user == null || user.getDeviceToken() == null) {
                return Response.status(400)
                    .entity(Map.of("error", "No device token registered"))
                    .build();
            }

            firebaseMessaging.unsubscribeFromTopic(
                List.of(user.getDeviceToken()), 
                topic
            );

            LOG.infof("User %s unsubscribed from topic: %s", userId, topic);

            return Response.ok(Map.of(
                "success", true,
                "topic", topic
            )).build();

        } catch (Exception e) {
            LOG.error("Failed to unsubscribe from topic", e);
            return Response.status(500)
                .entity(Map.of("error", "Failed to unsubscribe from topic"))
                .build();
        }
    }

    /**
     * Send test notification to current user
     * 
     * POST /api/device-tokens/test
     */
    @POST
    @Path("/test")
    @RolesAllowed({"RIDER", "DRIVER"})
    public Response sendTestNotification() {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId);

            if (user == null || user.getDeviceToken() == null) {
                return Response.status(400)
                    .entity(Map.of("error", "No device token registered"))
                    .build();
            }

            if (!user.isPushEnabled()) {
                return Response.status(400)
                    .entity(Map.of("error", "Push notifications are disabled"))
                    .build();
            }

            String messageId = firebaseMessaging.sendNotification(
                user.getDeviceToken(),
                "Test Notification",
                "This is a test notification from MetroSync!",
                Map.of("test", "true")
            );

            LOG.infof("Test notification sent to user %s: messageId=%s", userId, messageId);

            return Response.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "message", "Test notification sent successfully"
            )).build();

        } catch (Exception e) {
            LOG.error("Failed to send test notification", e);
            return Response.status(500)
                .entity(Map.of("error", "Failed to send test notification: " + e.getMessage()))
                .build();
        }
    }

    // ==================== REQUEST RECORDS ====================

    public record DeviceTokenRequest(
        String deviceToken,
        String platform // ANDROID, IOS, WEB
    ) {}

    public record PushPreferencesRequest(
        boolean pushEnabled
    ) {}
}