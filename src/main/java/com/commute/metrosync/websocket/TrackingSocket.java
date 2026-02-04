package com.commute.metrosync.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Endpoint for Real-Time Location Tracking
 * 
 * Connection URL: ws://localhost:8081/tracking?routeId={routeId}
 * 
 * Message Flow:
 * 1. Driver → Server: Location updates every 5-10 seconds
 * 2. Server → Passengers: Broadcast driver location
 * 3. Bidirectional: Status updates (trip started, completed, etc.)
 * 
 * Room Structure:
 * - Each route has its own channel
 * - 1 driver + multiple passengers per route
 * - Auto-cleanup on disconnect
 * 
 * Security:
 * - JWT token validation on connection (TODO: implement)
 * - User authorization per route
 * 
 * Features:
 * - Automatic reconnection support
 * - Heartbeat ping/pong every 30 seconds
 * - Message persistence for offline users (TODO)
 */
@ServerEndpoint("/tracking")
@ApplicationScoped
public class TrackingSocket {
    
    @Inject
    ObjectMapper objectMapper;
    
    // Room structure: Map<RouteId, Map<UserId, Session>>
    private final Map<String, Map<String, Session>> routeChannels = new ConcurrentHashMap<>();
    
    // User metadata: Map<SessionId, UserContext>
    private final Map<String, UserContext> userContexts = new ConcurrentHashMap<>();
    
    /**
     * Handle new WebSocket connection
     * 
     * URL Parameters:
     * - routeId: Required - The active route ID
     * - Authorization header: Required - JWT token (TODO: implement)
     */
    @OnOpen
    public void onOpen(
            Session session,
            EndpointConfig config) {
        
        try {
            // Extract routeId from query parameters
            String routeId = extractQueryParam(session, "routeId");
            
            if (routeId == null || routeId.isEmpty()) {
                Log.warn("Connection rejected: Missing routeId parameter");
                closeWithError(session, "Missing routeId query parameter");
                return;
            }
            
            // TODO: Extract and validate JWT token from headers
            // For now, use a dummy userId
            String userId = session.getId(); // Would be extracted from JWT
            String userRole = "DRIVER"; // Would be extracted from JWT
            
            // Create user context
            UserContext context = new UserContext(userId, userRole, routeId, LocalDateTime.now());
            userContexts.put(session.getId(), context);
            
            // Create route channel if it doesn't exist
            routeChannels.computeIfAbsent(routeId, k -> new ConcurrentHashMap<>());
            
            // Add user to route channel
            routeChannels.get(routeId).put(userId, session);
            
            Log.info(String.format(
                "User %s (%s) joined tracking for route: %s",
                userId, userRole, routeId
            ));
            
            // Send connection confirmation
            sendMessage(session, new ConnectionMessage(
                "connected",
                routeId,
                "Successfully connected to route tracking"
            ));
            
            // Notify other users in the channel
            broadcastToRoute(routeId, new StatusMessage(
                "user_joined",
                userId,
                userRole,
                LocalDateTime.now().toString()
            ), userId);
            
        } catch (Exception e) {
            Log.error("Error handling connection", e);
            closeWithError(session, "Connection error");
        }
    }
    
    /**
     * Handle incoming messages
     * 
     * Message Types:
     * 1. location_update: Driver sends location
     * 2. status_update: Status changes (trip started, etc.)
     * 3. ping: Heartbeat message
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            UserContext context = userContexts.get(session.getId());
            
            if (context == null) {
                Log.warn("Message from unknown session: " + session.getId());
                return;
            }
            
            // Parse message as generic JSON
            Map<String, Object> messageData = objectMapper.readValue(message, Map.class);
            String messageType = (String) messageData.get("type");
            
            if (messageType == null) {
                Log.warn("Message without type from user: " + context.userId());
                return;
            }
            
            switch (messageType) {
                case "location_update":
                    handleLocationUpdate(context, messageData, session);
                    break;
                    
                case "status_update":
                    handleStatusUpdate(context, messageData);
                    break;
                    
                case "ping":
                    handlePing(session);
                    break;
                    
                default:
                    Log.warn("Unknown message type: " + messageType);
            }
            
        } catch (Exception e) {
            Log.error("Error processing message", e);
        }
    }
    
    /**
     * Handle WebSocket close
     */
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        UserContext context = userContexts.remove(session.getId());
        
        if (context != null) {
            // Remove from route channel
            Map<String, Session> channel = routeChannels.get(context.routeId());
            if (channel != null) {
                channel.remove(context.userId());
                
                // Cleanup empty channels
                if (channel.isEmpty()) {
                    routeChannels.remove(context.routeId());
                }
                
                // Notify other users
                broadcastToRoute(context.routeId(), new StatusMessage(
                    "user_left",
                    context.userId(),
                    context.userRole(),
                    LocalDateTime.now().toString()
                ), context.userId());
            }
            
            Log.info(String.format(
                "User %s (%s) left tracking for route: %s. Reason: %s",
                context.userId(), context.userRole(), context.routeId(),
                closeReason.getReasonPhrase()
            ));
        }
    }
    
    /**
     * Handle WebSocket errors
     */
    @OnError
    public void onError(Session session, Throwable error) {
        UserContext context = userContexts.get(session.getId());
        
        if (context != null) {
            Log.error(String.format(
                "WebSocket error for user %s on route %s",
                context.userId(), context.routeId()
            ), error);
        } else {
            Log.error("WebSocket error on unknown session", error);
        }
        
        // Clean up on error
        try {
            session.close(new CloseReason(
                CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                "Internal error"
            ));
        } catch (IOException e) {
            Log.error("Failed to close session after error", e);
        }
    }
    
    // ==================== MESSAGE HANDLERS ====================
    
    /**
     * Handle driver location update
     * Driver sends location every 5-10 seconds
     */
    private void handleLocationUpdate(
            UserContext context,
            Map<String, Object> messageData,
            Session session) {
        
        // Validate that user is driver
        if (!"DRIVER".equals(context.userRole())) {
            sendMessage(session, new ErrorMessage(
                "unauthorized",
                "Only drivers can send location updates"
            ));
            return;
        }
        
        try {
            // Extract location data
            double latitude = ((Number) messageData.get("latitude")).doubleValue();
            double longitude = ((Number) messageData.get("longitude")).doubleValue();
            double bearing = messageData.containsKey("bearing") 
                ? ((Number) messageData.get("bearing")).doubleValue() 
                : 0.0;
            double speed = messageData.containsKey("speed")
                ? ((Number) messageData.get("speed")).doubleValue()
                : 0.0;
            String timestamp = (String) messageData.getOrDefault("timestamp", LocalDateTime.now().toString());
            
            // Broadcast to all passengers on this route
            DriverLocationMessage locationMessage = new DriverLocationMessage(
                "driver_location",
                context.routeId(),
                context.userId(),
                latitude,
                longitude,
                bearing,
                speed,
                timestamp,
                null // ETA calculation would go here
            );
            
            broadcastToRoute(
                context.routeId(),
                locationMessage,
                context.userId() // Don't echo back to driver
            );
            
            // Optionally persist location to LocationTracking table
            // persistLocation(context.routeId(), context.userId(), latitude, longitude, timestamp);
            
        } catch (Exception e) {
            Log.error("Error handling location update", e);
            sendMessage(session, new ErrorMessage(
                "invalid_message",
                "Invalid location data"
            ));
        }
    }
    
    /**
     * Handle trip status updates
     * (trip started, passenger picked up, trip completed, etc.)
     */
    private void handleStatusUpdate(
            UserContext context,
            Map<String, Object> messageData) {
        
        try {
            String bookingId = (String) messageData.get("bookingId");
            String status = (String) messageData.get("status");
            String message = (String) messageData.get("message");
            
            // Broadcast status update to all users on route
            BookingStatusMessage statusMessage = new BookingStatusMessage(
                "booking_status_update",
                bookingId,
                status,
                message,
                LocalDateTime.now().toString()
            );
            
            broadcastToRoute(context.routeId(), statusMessage, null);
            
            Log.info(String.format(
                "Status update for booking %s: %s",
                bookingId, status
            ));
            
        } catch (Exception e) {
            Log.error("Error handling status update", e);
        }
    }
    
    /**
     * Handle heartbeat ping
     * Client sends ping every 30 seconds to keep connection alive
     */
    private void handlePing(Session session) {
        sendMessage(session, new PongMessage("pong", LocalDateTime.now().toString()));
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Broadcast message to all users in a route channel
     * 
     * @param routeId Route channel ID
     * @param message Message to broadcast
     * @param excludeUserId User to exclude (optional, e.g., sender)
     */
    private void broadcastToRoute(String routeId, Object message, String excludeUserId) {
        Map<String, Session> channel = routeChannels.get(routeId);
        
        if (channel == null || channel.isEmpty()) {
            return;
        }
        
        String jsonMessage = serializeMessage(message);
        if (jsonMessage == null) {
            return;
        }
        
        int successCount = 0;
        int failCount = 0;
        
        for (Map.Entry<String, Session> entry : channel.entrySet()) {
            String userId = entry.getKey();
            Session session = entry.getValue();
            
            // Skip excluded user (e.g., don't echo back to sender)
            if (excludeUserId != null && excludeUserId.equals(userId)) {
                continue;
            }
            
            try {
                session.getAsyncRemote().sendText(jsonMessage, result -> {
                    if (result.getException() != null) {
                        Log.error("Failed to send message to user: " + userId, result.getException());
                    }
                });
                successCount++;
            } catch (Exception e) {
                Log.error("Failed to broadcast to user: " + userId, e);
                failCount++;
            }
        }
        
        Log.debug(String.format(
            "Broadcast to route %s: %d sent, %d failed",
            routeId, successCount, failCount
        ));
    }
    
    /**
     * Send message to a specific session
     */
    private void sendMessage(Session session, Object message) {
        String jsonMessage = serializeMessage(message);
        
        if (jsonMessage == null) {
            return;
        }
        
        try {
            session.getAsyncRemote().sendText(jsonMessage, result -> {
                if (result.getException() != null) {
                    Log.error("Failed to send message", result.getException());
                }
            });
        } catch (Exception e) {
            Log.error("Failed to send message to session", e);
        }
    }
    
    /**
     * Serialize message to JSON
     */
    private String serializeMessage(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            Log.error("Failed to serialize message", e);
            return null;
        }
    }
    
    /**
     * Extract query parameter from WebSocket session
     */
    private String extractQueryParam(Session session, String paramName) {
        String queryString = session.getQueryString();
        
        if (queryString == null) {
            return null;
        }
        
        for (String param : queryString.split("&")) {
            String[] parts = param.split("=");
            if (parts.length == 2 && parts[0].equals(paramName)) {
                return parts[1];
            }
        }
        
        return null;
    }
    
    /**
     * Close session with error message
     */
    private void closeWithError(Session session, String errorMessage) {
        try {
            sendMessage(session, new ErrorMessage("connection_error", errorMessage));
            session.close(new CloseReason(
                CloseReason.CloseCodes.VIOLATED_POLICY,
                errorMessage
            ));
        } catch (IOException e) {
            Log.error("Failed to close session with error", e);
        }
    }
    
    // ==================== DTOs ====================
    
    /**
     * User context stored for each session
     */
    private record UserContext(
        String userId,
        String userRole,  // DRIVER or PASSENGER
        String routeId,
        LocalDateTime connectedAt
    ) {}
    
    /**
     * Connection confirmation message
     */
    private record ConnectionMessage(
        String type,
        String routeId,
        String message
    ) {}
    
    /**
     * Driver location broadcast message
     */
    private record DriverLocationMessage(
        String type,
        String routeId,
        String driverId,
        double latitude,
        double longitude,
        double bearing,
        double speed,
        String timestamp,
        String estimatedArrival
    ) {}
    
    /**
     * Booking status update message
     */
    private record BookingStatusMessage(
        String type,
        String bookingId,
        String status,
        String message,
        String timestamp
    ) {}
    
    /**
     * User joined/left status message
     */
    private record StatusMessage(
        String type,
        String userId,
        String userRole,
        String timestamp
    ) {}
    
    /**
     * Error message
     */
    private record ErrorMessage(
        String type,
        String message
    ) {}
    
    /**
     * Pong response to ping
     */
    private record PongMessage(
        String type,
        String timestamp
    ) {}
}