package com.commute.metrosync.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Objects for Driver-related operations
 */
public class DriverDTO {
    
    /**
     * Comprehensive driver dashboard statistics
     */
    public record DriverStats(
        int activePassengers,
        int pendingRequests,
        String nextStopName,
        Integer nextStopEtaMinutes,
        double todaysEarnings,
        int tripsCompleted,
        int passengersTransported,
        int onlineMinutes,
        double acceptanceRate
    ) {}
    
    /**
     * Response for driver status updates
     */
    public record StatusUpdateResponse(
        boolean success,
        String currentStatus,
        LocalDateTime timestamp
    ) {}
    
    /**
     * Active route manifest with all pickup/dropoff points
     */
    public record RouteManifest(
        UUID routeId,
        String routeName,
        List<ManifestPoint> points,
        String polyline
    ) {}
    
    /**
     * Individual point in route manifest (pickup or dropoff)
     */
    public record ManifestPoint(
        UUID bookingId,
        String type, // "pickup" or "dropoff"
        double latitude,
        double longitude,
        String passengerName,
        LocalDateTime scheduledTime,
        int passengerCount
    ) {}
    
    /**
     * Request to update driver status
     */
    public record StatusUpdateRequest(
        String status // "ONLINE" or "OFFLINE"
    ) {}
}