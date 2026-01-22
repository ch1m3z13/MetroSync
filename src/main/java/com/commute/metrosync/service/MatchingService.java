package com.commute.metrosync.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Corridor-based Matching Service for Carpooling
 * 
 * KEY DIFFERENCE from Taxi Apps:
 * - Taxi: Find drivers NEAR pickup point (radius search)
 * - Carpool: Find drivers whose ROUTE passes by pickup AND dropoff (corridor search)
 * 
 * This service uses PostGIS ST_DWithin to check if both pickup and dropoff
 * are within tolerance of the driver's ENTIRE route line, not just endpoints.
 */
@ApplicationScoped
public class MatchingService {

    @Inject
    EntityManager em;

    /**
     * Find drivers whose route corridor passes near BOTH pickup and dropoff
     * 
     * Flow:
     * 1. Check if pickup is within 500m of any driver's route LINE
     * 2. Check if dropoff is within 500m of that same route LINE
     * 3. Verify pickup comes BEFORE dropoff on the route (direction matters!)
     * 4. Return sorted by total distance (closest match first)
     * 
     * @param pickupLat Pickup latitude
     * @param pickupLng Pickup longitude
     * @param dropoffLat Dropoff latitude
     * @param dropoffLng Dropoff longitude
     * @param toleranceMeters Max distance from route (default: 500m)
     * @return List of matching routes with distance scores
     */
    @SuppressWarnings("unchecked")
    public List<RouteMatchDTO> findMatches(
            double pickupLat, double pickupLng, 
            double dropoffLat, double dropoffLng,
            Double toleranceMeters) {

        double tolerance = toleranceMeters != null ? toleranceMeters : 500.0;

        Log.info(String.format(
            "Finding corridor matches: pickup=(%.6f, %.6f), dropoff=(%.6f, %.6f), tolerance=%.0fm",
            pickupLat, pickupLng, dropoffLat, dropoffLng, tolerance
        ));

        // Call the PostGIS corridor matching function
        String sql = "SELECT * FROM match_commuter_routes(:pLat, :pLng, :dLat, :dLng, :tolerance)";

        Query query = em.createNativeQuery(sql);
        query.setParameter("pLat", pickupLat);
        query.setParameter("pLng", pickupLng);
        query.setParameter("dLat", dropoffLat);
        query.setParameter("dLng", dropoffLng);
        query.setParameter("tolerance", tolerance);

        List<Object[]> results = query.getResultList();
        List<RouteMatchDTO> matches = new ArrayList<>();

        for (Object[] row : results) {
            // Map SQL result to DTO
            // Columns: variation_id, driver_id, driver_name, route_name, match_score,
            //          pickup_distance_m, dropoff_distance_m, direction, estimated_duration_min
            
            UUID variationId = UUID.fromString(row[0].toString());
            UUID driverId = UUID.fromString(row[1].toString());
            String driverName = (String) row[2];
            String routeName = (String) row[3];
            Double matchScore = ((Number) row[4]).doubleValue();
            Double pickupDistanceM = ((Number) row[5]).doubleValue();
            Double dropoffDistanceM = ((Number) row[6]).doubleValue();
            String direction = (String) row[7];
            Integer estimatedDurationMin = row[8] != null ? ((Number) row[8]).intValue() : null;

            matches.add(new RouteMatchDTO(
                variationId,
                driverId,
                driverName,
                routeName,
                matchScore,
                pickupDistanceM,
                dropoffDistanceM,
                direction,
                estimatedDurationMin
            ));
        }

        Log.info(String.format("Found %d corridor matches", matches.size()));

        return matches;
    }

    /**
     * Overload with default tolerance
     */
    public List<RouteMatchDTO> findMatches(
            double pickupLat, double pickupLng, 
            double dropoffLat, double dropoffLng) {
        return findMatches(pickupLat, pickupLng, dropoffLat, dropoffLng, 500.0);
    }

    /**
     * Get closest point on a driver's route to a given location
     * Useful for showing riders where they'll actually be picked up
     * 
     * @param routeVariationId Route variation ID
     * @param pointLat Point latitude
     * @param pointLng Point longitude
     * @return Closest point on route with distance
     */
    @SuppressWarnings("unchecked")
    public ClosestPointDTO getClosestPointOnRoute(
            UUID routeVariationId,
            double pointLat,
            double pointLng) {

        String sql = """
            SELECT 
                ST_Y(closest_point) as latitude,
                ST_X(closest_point) as longitude,
                ST_Distance(
                    closest_point::geography,
                    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
                ) as distance_meters
            FROM (
                SELECT ST_ClosestPoint(
                    rv.geometry,
                    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
                ) as closest_point
                FROM route_variations rv
                WHERE rv.id = :variationId
            ) sub
            """;

        Query query = em.createNativeQuery(sql);
        query.setParameter("variationId", routeVariationId);
        query.setParameter("lat", pointLat);
        query.setParameter("lng", pointLng);

        Object[] result = (Object[]) query.getSingleResult();

        return new ClosestPointDTO(
            ((Number) result[0]).doubleValue(),  // latitude
            ((Number) result[1]).doubleValue(),  // longitude
            ((Number) result[2]).doubleValue()   // distance_meters
        );
    }

    /**
     * Generate driver manifest (ordered list of pickup/dropoff stops)
     * This is the "school bus route" - tells driver the sequence of stops
     * 
     * @param routeVariationId Active route variation ID
     * @return Ordered list of waypoints
     */
    @SuppressWarnings("unchecked")
    public List<ManifestWaypointDTO> generateDriverManifest(UUID routeVariationId) {
        Log.info("Generating driver manifest for route variation: " + routeVariationId);

        String sql = "SELECT * FROM get_route_manifest(:variationId)";

        Query query = em.createNativeQuery(sql);
        query.setParameter("variationId", routeVariationId);

        List<Object[]> results = query.getResultList();
        List<ManifestWaypointDTO> manifest = new ArrayList<>();

        // Add route start point
        manifest.add(getRouteStartPoint(routeVariationId));

        // Add all pickup/dropoff points in sequence order
        for (Object[] row : results) {
            // Columns: booking_id, stop_type, sequence_order, passenger_name, 
            //          passenger_count, latitude, longitude, scheduled_time
            
            UUID bookingId = UUID.fromString(row[0].toString());
            String stopType = (String) row[1];
            Double sequenceOrder = ((Number) row[2]).doubleValue();
            String passengerName = (String) row[3];
            Integer passengerCount = ((Number) row[4]).intValue();
            Double latitude = ((Number) row[5]).doubleValue();
            Double longitude = ((Number) row[6]).doubleValue();

            manifest.add(new ManifestWaypointDTO(
                bookingId,
                stopType,
                passengerName,
                passengerCount,
                latitude,
                longitude,
                sequenceOrder
            ));
        }

        // Add route end point
        manifest.add(getRouteEndPoint(routeVariationId));

        Log.info(String.format("Generated manifest with %d waypoints", manifest.size()));

        return manifest;
    }

    /**
     * Get route start point (Home or Work depending on direction)
     */
    @SuppressWarnings("unchecked")
    private ManifestWaypointDTO getRouteStartPoint(UUID routeVariationId) {
        String sql = """
            SELECT 
                rv.direction,
                ST_Y(ST_StartPoint(rv.geometry)) as latitude,
                ST_X(ST_StartPoint(rv.geometry)) as longitude
            FROM route_variations rv
            WHERE rv.id = :variationId
            """;

        Query query = em.createNativeQuery(sql);
        query.setParameter("variationId", routeVariationId);

        Object[] result = (Object[]) query.getSingleResult();

        String direction = (String) result[0];
        Double latitude = ((Number) result[1]).doubleValue();
        Double longitude = ((Number) result[2]).doubleValue();

        String startName = "TO_WORK".equals(direction) ? "Home (Start)" : "Work (Start)";

        return new ManifestWaypointDTO(
            null,
            "START",
            startName,
            0,
            latitude,
            longitude,
            0.0
        );
    }

    /**
     * Get route end point
     */
    @SuppressWarnings("unchecked")
    private ManifestWaypointDTO getRouteEndPoint(UUID routeVariationId) {
        String sql = """
            SELECT 
                rv.direction,
                ST_Y(ST_EndPoint(rv.geometry)) as latitude,
                ST_X(ST_EndPoint(rv.geometry)) as longitude
            FROM route_variations rv
            WHERE rv.id = :variationId
            """;

        Query query = em.createNativeQuery(sql);
        query.setParameter("variationId", routeVariationId);

        Object[] result = (Object[]) query.getSingleResult();

        String direction = (String) result[0];
        Double latitude = ((Number) result[1]).doubleValue();
        Double longitude = ((Number) result[2]).doubleValue();

        String endName = "TO_WORK".equals(direction) ? "Work (End)" : "Home (End)";

        return new ManifestWaypointDTO(
            null,
            "END",
            endName,
            0,
            latitude,
            longitude,
            1.0
        );
    }

    /**
     * Validate if a booking request is feasible (both points are on route)
     * This is called BEFORE creating a booking to prevent invalid bookings
     */
    public boolean validateBookingRequest(
            UUID routeVariationId,
            double pickupLat,
            double pickupLng,
            double dropoffLat,
            double dropoffLng,
            double toleranceMeters) {

        String sql = """
            SELECT 
                CASE 
                    WHEN 
                        ST_DWithin(
                            rv.geometry::geography,
                            ST_SetSRID(ST_MakePoint(:pLng, :pLat), 4326)::geography,
                            :tolerance
                        )
                        AND ST_DWithin(
                            rv.geometry::geography,
                            ST_SetSRID(ST_MakePoint(:dLng, :dLat), 4326)::geography,
                            :tolerance
                        )
                        AND ST_LineLocatePoint(rv.geometry, ST_SetSRID(ST_MakePoint(:pLng, :pLat), 4326)) 
                            < ST_LineLocatePoint(rv.geometry, ST_SetSRID(ST_MakePoint(:dLng, :dLat), 4326))
                    THEN true
                    ELSE false
                END as is_valid
            FROM route_variations rv
            WHERE rv.id = :variationId
            """;

        Query query = em.createNativeQuery(sql);
        query.setParameter("variationId", routeVariationId);
        query.setParameter("pLat", pickupLat);
        query.setParameter("pLng", pickupLng);
        query.setParameter("dLat", dropoffLat);
        query.setParameter("dLng", dropoffLng);
        query.setParameter("tolerance", toleranceMeters);

        return (Boolean) query.getSingleResult();
    }

    // ==================== DTOs ====================

    /**
     * Route match result from corridor search
     */
    public record RouteMatchDTO(
        UUID variationId,
        UUID driverId,
        String driverName,
        String routeName,
        Double matchScore,           // Total distance (lower is better)
        Double pickupDistanceM,      // Distance from pickup to route
        Double dropoffDistanceM,     // Distance from dropoff to route
        String direction,            // TO_WORK or TO_HOME
        Integer estimatedDurationMin // Estimated trip duration
    ) {
        public boolean isGoodMatch() {
            return matchScore < 1000; // Less than 1km total deviation
        }
    }

    /**
     * Closest point on route (for showing riders where they'll be picked up)
     */
    public record ClosestPointDTO(
        Double latitude,
        Double longitude,
        Double distanceMeters
    ) {}

    /**
     * Waypoint in driver manifest (school bus style routing)
     */
    public record ManifestWaypointDTO(
        UUID bookingId,         // null for START/END points
        String stopType,        // "START", "PICKUP", "DROPOFF", "END"
        String label,           // "Pick: John Doe" or "Drop: Jane Smith"
        Integer passengerCount, // Number of passengers
        Double latitude,
        Double longitude,
        Double sequenceOrder    // 0.0 to 1.0 along route
    ) {}
}