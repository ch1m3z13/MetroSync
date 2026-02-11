package com.commute.metrosync.resource;

import com.commute.metrosync.dto.*;
import com.commute.metrosync.service.NotificationService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Notification Resource
 * Handles in-app notifications for users
 * Jakarta EE / JAX-RS implementation
 */
@Path("/api/v1/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Notifications", description = "In-app notification endpoints")
@SecurityRequirement(name = "bearerAuth")
public class NotificationResource {
    
    private static final Logger logger = Logger.getLogger(NotificationResource.class.getName());
    
    @Inject
    private NotificationService notificationService;
    
    @Context
    private SecurityContext securityContext;
    
    @Context
    private UriInfo uriInfo;
    
    // ==================== GET NOTIFICATIONS ====================
    
    @GET
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get notifications", description = "Get paginated list of notifications for the current user")
    public Response getNotifications(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) int size,
            @QueryParam("unreadOnly") @DefaultValue("false") boolean unreadOnly,
            @QueryParam("type") String type,
            @QueryParam("priority") String priority) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " requesting notifications: page=" + page + 
                ", size=" + size + ", unreadOnly=" + unreadOnly);
            
            // Validate pagination
            if (size > 50) {
                size = 50; // Max page size for notifications
            }
            
            PagedResult<NotificationResponse> notifications = notificationService.getNotifications(
                userId,
                unreadOnly,
                type,
                priority,
                page,
                size
            );
            
            // Get unread count
            int unreadCount = notificationService.getUnreadCount(userId);
            
            NotificationPageResponse response = new NotificationPageResponse(
                notifications,
                unreadCount
            );
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error getting notifications: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get notifications"))
                .build();
        }
    }
    
    @GET
    @Path("/{notificationId}")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get notification details", description = "Get details of a specific notification")
    public Response getNotification(@PathParam("notificationId") UUID notificationId) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " requesting notification details: notificationId=" + notificationId);
            
            NotificationResponse response = notificationService.getNotification(userId, notificationId);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Notification not found or unauthorized: " + e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ApiResponse.error("Notification not found"))
                .build();
        } catch (Exception e) {
            logger.severe("Error getting notification: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get notification"))
                .build();
        }
    }
    
    // ==================== UNREAD COUNT ====================
    
    @GET
    @Path("/unread-count")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get unread notification count", description = "Get count of unread notifications for badge display")
    public Response getUnreadCount() {
        
        try {
            UUID userId = getUserId();
            
            int count = notificationService.getUnreadCount(userId);
            
            UnreadCountResponse response = new UnreadCountResponse(count);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error getting unread count: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get unread count"))
                .build();
        }
    }
    
    // ==================== MARK AS READ ====================
    
    @PUT
    @Path("/{notificationId}/read")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Mark notification as read", description = "Mark a specific notification as read")
    public Response markAsRead(@PathParam("notificationId") UUID notificationId) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " marking notification as read: notificationId=" + notificationId);
            
            MarkReadResponse response = notificationService.markAsRead(userId, notificationId);
            
            return Response.ok(ApiResponse.success(response, "Notification marked as read")).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Notification not found or unauthorized: " + e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ApiResponse.error("Notification not found"))
                .build();
        } catch (Exception e) {
            logger.severe("Error marking notification as read: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to mark notification as read"))
                .build();
        }
    }
    
    @PUT
    @Path("/read-all")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Mark all notifications as read", description = "Mark all unread notifications as read")
    public Response markAllAsRead() {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " marking all notifications as read");
            
            int updatedCount = notificationService.markAllAsRead(userId);
            
            MarkAllReadResponse response = new MarkAllReadResponse(updatedCount);
            
            return Response.ok(ApiResponse.success(
                response,
                updatedCount + " notification(s) marked as read"
            )).build();
            
        } catch (Exception e) {
            logger.severe("Error marking all notifications as read: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to mark all notifications as read"))
                .build();
        }
    }
    
    @PUT
    @Path("/read-batch")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Mark multiple notifications as read", description = "Mark multiple notifications as read in batch")
    public Response markBatchAsRead(MarkBatchReadRequest request) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " marking " + request.getNotificationIds().size() + " notifications as read");
            
            int updatedCount = notificationService.markBatchAsRead(userId, request.getNotificationIds());
            
            MarkBatchReadResponse response = new MarkBatchReadResponse(updatedCount);
            
            return Response.ok(ApiResponse.success(
                response,
                updatedCount + " notification(s) marked as read"
            )).build();
            
        } catch (Exception e) {
            logger.severe("Error marking batch notifications as read: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to mark notifications as read"))
                .build();
        }
    }
    
    // ==================== DELETE NOTIFICATIONS ====================
    
    @DELETE
    @Path("/{notificationId}")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Delete notification", description = "Delete a specific notification")
    public Response deleteNotification(@PathParam("notificationId") UUID notificationId) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " deleting notification: notificationId=" + notificationId);
            
            notificationService.deleteNotification(userId, notificationId);
            
            return Response.ok(ApiResponse.success(null, "Notification deleted successfully")).build();
            
        } catch (IllegalArgumentException e) {
            logger.warning("Notification not found or unauthorized: " + e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ApiResponse.error("Notification not found"))
                .build();
        } catch (Exception e) {
            logger.severe("Error deleting notification: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to delete notification"))
                .build();
        }
    }
    
    @DELETE
    @Path("/delete-all-read")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Delete all read notifications", description = "Delete all read notifications for the user")
    public Response deleteAllRead() {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " deleting all read notifications");
            
            int deletedCount = notificationService.deleteAllRead(userId);
            
            DeleteReadResponse response = new DeleteReadResponse(deletedCount);
            
            return Response.ok(ApiResponse.success(
                response,
                deletedCount + " notification(s) deleted"
            )).build();
            
        } catch (Exception e) {
            logger.severe("Error deleting read notifications: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to delete notifications"))
                .build();
        }
    }
    
    // ==================== NOTIFICATION PREFERENCES ====================
    
    @GET
    @Path("/preferences")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get notification preferences", description = "Get user's notification preferences")
    public Response getPreferences() {
        
        try {
            UUID userId = getUserId();
            
            NotificationPreferencesResponse response = notificationService.getPreferences(userId);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error getting notification preferences: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get notification preferences"))
                .build();
        }
    }
    
    @PUT
    @Path("/preferences")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Update notification preferences", description = "Update user's notification preferences")
    public Response updatePreferences(NotificationPreferencesRequest request) {
        
        try {
            UUID userId = getUserId();
            logger.info("User " + userId + " updating notification preferences");
            
            NotificationPreferencesResponse response = notificationService.updatePreferences(userId, request);
            
            return Response.ok(ApiResponse.success(
                response,
                "Notification preferences updated successfully"
            )).build();
            
        } catch (Exception e) {
            logger.severe("Error updating notification preferences: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to update notification preferences"))
                .build();
        }
    }
    
    // ==================== NOTIFICATION STATISTICS ====================
    
    @GET
    @Path("/statistics")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get notification statistics", description = "Get notification statistics for the user")
    public Response getStatistics() {
        
        try {
            UUID userId = getUserId();
            
            NotificationStatisticsResponse response = notificationService.getStatistics(userId);
            
            return Response.ok(ApiResponse.success(response)).build();
            
        } catch (Exception e) {
            logger.severe("Error getting notification statistics: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to get notification statistics"))
                .build();
        }
    }
    
    // ==================== ADMIN ENDPOINTS ====================
    
    @POST
    @Path("/send")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Send notification", description = "Admin endpoint to send a notification to a user")
    public Response sendNotification(SendNotificationRequest request) {
        
        try {
            UUID adminId = getUserId();
            logger.info("Admin " + adminId + " sending notification to user " + request.getUserId());
            
            NotificationResponse response = notificationService.sendNotification(
                request.getUserId(),
                request.getTitle(),
                request.getMessage(),
                request.getType(),
                request.getPriority(),
                request.getData(),
                request.getActionUrl()
            );
            
            return Response.ok(ApiResponse.success(response, "Notification sent successfully")).build();
            
        } catch (Exception e) {
            logger.severe("Error sending notification: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to send notification"))
                .build();
        }
    }
    
    @POST
    @Path("/broadcast")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Broadcast notification", description = "Admin endpoint to send notification to multiple users")
    public Response broadcastNotification(BroadcastNotificationRequest request) {
        
        try {
            UUID adminId = getUserId();
            logger.info("Admin " + adminId + " broadcasting notification to " + 
                request.getUserIds().size() + " users");
            
            int sentCount = notificationService.broadcastNotification(
                request.getUserIds(),
                request.getTitle(),
                request.getMessage(),
                request.getType(),
                request.getPriority(),
                request.getData()
            );
            
            BroadcastResponse response = new BroadcastResponse(sentCount, request.getUserIds().size());
            
            return Response.ok(ApiResponse.success(
                response,
                "Notification sent to " + sentCount + " user(s)"
            )).build();
            
        } catch (Exception e) {
            logger.severe("Error broadcasting notification: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Failed to broadcast notification"))
                .build();
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private UUID getUserId() {
        Principal principal = securityContext.getUserPrincipal();
        if (principal == null) {
            throw new WebApplicationException("Unauthorized", Response.Status.UNAUTHORIZED);
        }
        return UUID.fromString(principal.getName());
    }
}