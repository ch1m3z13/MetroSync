package com.commute.metrosync.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTOs for Corridor-based Route Matching
 * These are specific to the carpool matching system
 */
public class RouteMatchDTOs {

    /**
     * Detailed route match with driver and vehicle info
     * Used in rider app to show available drivers
     */
    public record DetailedRouteMatch(
        // Route info
        UUID variationId,
        String routeName,
        String direction,
        
        // Driver info
        UUID driverId,
        String driverName,
        Double driverRating,
        Integer totalRatings,
        String profileImageUrl,
        
        // Vehicle info
        String vehicleMake,
        String vehicleModel,
        String vehicleColor,
        Integer vehicleCapacity,
        Integer availableSeats,
        
        // Match quality
        Double matchScore,
        Double pickupDistanceM,
        Double dropoffDistanceM,
        
        // Trip estimates
        Integer estimatedDurationMin,
        Double estimatedFare,
        
        // Pickup details
        PickupDetails pickupDetails,
        DropoffDetails dropoffDetails
    ) {
        public boolean isExcellentMatch() {
            return matchScore < 500; // Less than 500m total deviation
        }
        
        public boolean isGoodMatch() {
            return matchScore < 1000; // Less than 1km total deviation
        }
        
        public String getMatchQuality() {
            if (isExcellentMatch()) return "EXCELLENT";
            if (isGoodMatch()) return "GOOD";
            return "FAIR";
        }
    }

    /**
     * Pickup point details for rider
     */
    public record PickupDetails(
        Double requestedLatitude,
        Double requestedLongitude,
        Double actualLatitude,      // Closest point on route
        Double actualLongitude,
        Double walkDistanceMeters,
        String instructions         // e.g., "Walk 50m north to pickup point"
    ) {}

    /**
     * Dropoff point details for rider
     */
    public record DropoffDetails(
        Double requestedLatitude,
        Double requestedLongitude,
        Double actualLatitude,      // Closest point on route
        Double actualLongitude,
        Double walkDistanceMeters,
        String instructions
    ) {}

    /**
     * Waypoint in driver's manifest
     * Used to build "school bus" style navigation
     */
    public record WaypointDTO(
        String type,              // "START", "PICKUP", "DROPOFF", "END"
        String label,             // Display name
        Double latitude,
        Double longitude,
        Integer passengerCount,   // For capacity tracking
        UUID bookingId           // null for START/END
    ) {}

    /**
     * Full driver manifest with navigation details
     */
    public record DriverManifest(
        UUID routeVariationId,
        String routeName,
        List<WaypointDTO> waypoints,
        int totalStops,
        int totalPassengers,
        Double totalDistanceKm,
        Integer estimatedDurationMin,
        String encodedPolyline    // For map display
    ) {}

    /**
     * Booking validation result
     */
    public record BookingValidation(
        boolean isValid,
        String message,
        ValidationDetails details
    ) {}

    /**
     * Detailed validation information
     */
    public record ValidationDetails(
        boolean pickupOnRoute,
        boolean dropoffOnRoute,
        boolean correctDirection,
        Double pickupDistanceM,
        Double dropoffDistanceM,
        Double maxToleranceM
    ) {}

    /**
     * Real-time capacity information
     */
    public record CapacityInfo(
        int totalSeats,
        int bookedSeats,
        int availableSeats,
        boolean canAccommodate,
        String message
    ) {}

    /**
     * Route segment for detailed navigation
     */
    public record RouteSegment(
        int segmentIndex,
        String fromLabel,
        String toLabel,
        Double distanceKm,
        Integer durationMin,
        List<Coordinate> coordinates
    ) {}

    /**
     * Simple coordinate
     */
    public record Coordinate(
        Double latitude,
        Double longitude
    ) {}

    /**
     * Fare breakdown
     */
    public record FareBreakdown(
        Double baseFare,
        Double distanceFare,
        Double passengerMultiplier,
        Double totalFare,
        String currency
    ) {
        public FareBreakdown {
            if (currency == null) currency = "NGN";
        }
    }

    /**
     * Match suggestions for rider (when no exact matches found)
     */
    public record MatchSuggestions(
        String message,
        List<AlternativeRoute> alternatives
    ) {}

    /**
     * Alternative route suggestion
     */
    public record AlternativeRoute(
        UUID variationId,
        String suggestion,
        Double additionalWalkM,
        String direction
    ) {}
}