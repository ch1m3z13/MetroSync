package com.commute.metrosync.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Endpoint for Real-Time Tracking.
 * URL: ws://localhost:8081/ws/tracking/{routeId}/{userId}
 */
@ServerEndpoint("/ws/tracking/{routeId}/{userId}")
@ApplicationScoped
public class TrackingSocket {

    @Inject
    ObjectMapper objectMapper;

    // Store sessions grouped by Route ID
    // Map<RouteId, Map<SessionId, Session>>
    private final Map<String, Map<String, Session>> routeSessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("routeId") String routeId, @PathParam("userId") String userId) {
        // Create a room for the route if it doesn't exist
        routeSessions.computeIfAbsent(routeId, k -> new ConcurrentHashMap<>());
        
        // Add user to the route room
        routeSessions.get(routeId).put(session.getId(), session);
        
        Log.info("User " + userId + " joined tracking for route: " + routeId);
    }

    @OnClose
    public void onClose(Session session, @PathParam("routeId") String routeId, @PathParam("userId") String userId) {
        Map<String, Session> sessions = routeSessions.get(routeId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) {
                routeSessions.remove(routeId);
            }
        }
        Log.info("User " + userId + " left tracking for route: " + routeId);
    }

    @OnError
    public void onError(Session session, @PathParam("routeId") String routeId, Throwable throwable) {
        Log.error("WebSocket error on route " + routeId + ": " + throwable.getMessage());
        Map<String, Session> sessions = routeSessions.get(routeId);
        if (sessions != null) {
            sessions.remove(session.getId());
        }
    }

    @OnMessage
    public void onMessage(String message, @PathParam("routeId") String routeId) {
        try {
            // 1. Parse the message to ensure it's valid JSON (Optional validation)
            // We expect the client to send a JSON string matching LocationMessage structure
            // LocationMessage msg = objectMapper.readValue(message, LocationMessage.class);

            // 2. Broadcast to EVERYONE on this route
            broadcastToRoute(routeId, message);

        } catch (Exception e) {
            Log.error("Error processing tracking message", e);
        }
    }

    private void broadcastToRoute(String routeId, String message) {
        Map<String, Session> sessions = routeSessions.get(routeId);
        if (sessions != null) {
            sessions.values().forEach(session -> {
                // Send async to avoid blocking
                session.getAsyncRemote().sendText(message, result -> {
                    if (result.getException() != null) {
                        Log.error("Failed to send message to client", result.getException());
                    }
                });
            });
        }
    }
}