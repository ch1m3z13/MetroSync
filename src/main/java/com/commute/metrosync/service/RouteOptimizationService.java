package com.commute.metrosync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OPTIONAL FUTURE ENHANCEMENT
 * 
 * Multi-Passenger Route Optimization Service
 * 
 * Use Case: When driver has MULTIPLE confirmed bookings and wants to 
 * optimize the pickup/dropoff sequence to minimize total trip time.
 * 
 * This is NOT needed for your current fixed-commute model,
 * but could be useful for a "dynamic routing" feature later.
 * 
 * Example Scenario:
 * - Driver commutes Home → Work (fixed endpoints)
 * - Has 3 confirmed passengers with different pickup/dropoff points
 * - Wants optimal sequence: Home → P1 → P2 → D1 → P3 → D2 → D3 → Work
 */
@ApplicationScoped
public class RouteOptimizationService {
    
    @ConfigProperty(name = "mapbox.access.token")
    String mapboxToken;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory;
    
    @Inject
    MapboxDirectionsService directionsService;
    
    public RouteOptimizationService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        this.objectMapper = new ObjectMapper();
        this.geometryFactory = new GeometryFactory();
    }
    
    /**
     * Optimize pickup/dropoff sequence for multiple passengers
     * 
     * IMPORTANT: Only use this if you add a "multi-passenger" feature.
     * Your current fixed-commute model doesn't need this.
     * 
     * @param homeLocation Driver's home (MUST be first)
     * @param workLocation Driver's work (MUST be last)
     * @param waypoints List of pickup/dropoff points to optimize
     * @return Optimized route with ordered waypoints
     */
    public OptimizedRoute optimizeMultiPassengerRoute(
            Coordinate homeLocation,
            Coordinate workLocation,
            List<Waypoint> waypoints) {
        
        try {
            Log.info("Optimizing route for " + waypoints.size() + " waypoints");
            
            // Build Mapbox Optimization API request
            String url = buildOptimizationUrl(homeLocation, workLocation, waypoints);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                Log.error("Mapbox Optimization API error: " + response.statusCode());
                // Fallback: Use original order
                return buildFallbackRoute(homeLocation, workLocation, waypoints);
            }
            
            return parseOptimizationResponse(response.body(), homeLocation, workLocation);
            
        } catch (Exception e) {
            Log.error("Failed to optimize route", e);
            return buildFallbackRoute(homeLocation, workLocation, waypoints);
        }
    }
    
    /**
     * Build Mapbox Optimization API URL
     * 
     * Key parameters:
     * - source=first: Home must be starting point
     * - destination=last: Work must be ending point
     * - roundtrip=false: One-way trip
     */
    private String buildOptimizationUrl(
            Coordinate home,
            Coordinate work,
            List<Waypoint> waypoints) {
        
        StringBuilder coords = new StringBuilder();
        
        // First coordinate: Home (fixed start)
        coords.append(String.format("%f,%f", home.x, home.y));
        
        // Middle coordinates: Waypoints to optimize
        for (Waypoint wp : waypoints) {
            coords.append(";").append(String.format("%f,%f", wp.lng(), wp.lat()));
        }
        
        // Last coordinate: Work (fixed end)
        coords.append(";").append(String.format("%f,%f", work.x, work.y));
        
        return String.format(
            "https://api.mapbox.com/optimized-trips/v1/mapbox/driving/%s?" +
            "source=first&" +              // ✅ Home is fixed start
            "destination=last&" +           // ✅ Work is fixed end
            "roundtrip=false&" +            // ✅ One-way trip
            "geometries=geojson&" +
            "overview=full&" +
            "access_token=%s",
            coords.toString(),
            mapboxToken
        );
    }
    
    /**
     * Parse optimization response
     */
    private OptimizedRoute parseOptimizationResponse(
            String jsonResponse,
            Coordinate home,
            Coordinate work) {
        
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode trips = root.get("trips");
            
            if (trips == null || !trips.isArray() || trips.size() == 0) {
                Log.warn("No optimized trips found");
                return null;
            }
            
            JsonNode trip = trips.get(0);
            
            // Extract optimized geometry
            JsonNode geometryNode = trip.get("geometry");
            LineString optimizedPath = parseGeometry(geometryNode);
            
            // Extract distance and duration
            double distanceKm = trip.get("distance").asDouble() / 1000.0;
            int durationMinutes = trip.get("duration").asInt() / 60;
            
            // Extract waypoint order
            JsonNode waypointOrder = trip.get("waypoint_order");
            List<Integer> order = new ArrayList<>();
            if (waypointOrder != null && waypointOrder.isArray()) {
                waypointOrder.forEach(node -> order.add(node.asInt()));
            }
            
            return new OptimizedRoute(
                optimizedPath,
                distanceKm,
                durationMinutes,
                order
            );
            
        } catch (Exception e) {
            Log.error("Failed to parse optimization response", e);
            return null;
        }
    }
    
    /**
     * Parse GeoJSON geometry
     */
    private LineString parseGeometry(JsonNode geometryNode) {
        JsonNode coordinates = geometryNode.get("coordinates");
        
        List<Coordinate> coords = new ArrayList<>();
        for (JsonNode coord : coordinates) {
            double lon = coord.get(0).asDouble();
            double lat = coord.get(1).asDouble();
            coords.add(new Coordinate(lon, lat));
        }
        
        LineString lineString = geometryFactory.createLineString(
            coords.toArray(new Coordinate[0])
        );
        lineString.setSRID(4326);
        
        return lineString;
    }
    
    /**
     * Fallback: Use original order if optimization fails
     */
    private OptimizedRoute buildFallbackRoute(
            Coordinate home,
            Coordinate work,
            List<Waypoint> waypoints) {
        
        Log.warn("Using fallback route (original order)");
        
        // Just use Mapbox Directions with all points in order
        List<Coordinate> allPoints = new ArrayList<>();
        allPoints.add(home);
        waypoints.forEach(wp -> allPoints.add(new Coordinate(wp.lng(), wp.lat())));
        allPoints.add(work);
        
        // This is a simplified fallback - in production, call Directions API
        List<Integer> originalOrder = new ArrayList<>();
        for (int i = 0; i < waypoints.size(); i++) {
            originalOrder.add(i);
        }
        
        return new OptimizedRoute(
            null, // Would need to call Directions API here
            0.0,
            0,
            originalOrder
        );
    }
    
    // ==================== DTOs ====================
    
    /**
     * Waypoint with type (pickup or dropoff)
     */
    public record Waypoint(
        double lat,
        double lng,
        String type,      // "PICKUP" or "DROPOFF"
        String passengerId
    ) {}
    
    /**
     * Optimized route result
     */
    public record OptimizedRoute(
        LineString geometry,
        double distanceKm,
        int durationMinutes,
        List<Integer> waypointOrder  // Indices showing optimal sequence
    ) {}
}

/**
 * WHEN TO USE THIS:
 * 
 * ✅ Future Feature: "Smart Sequencing"
 * - Driver has 3+ confirmed passengers
 * - System optimizes pickup/dropoff order
 * - Saves driver time and fuel
 * 
 * ❌ Current Use Case: Fixed Commute
 * - Driver goes Home → Work (fixed)
 * - Each passenger books independently
 * - No optimization needed
 * 
 * IMPLEMENTATION PRIORITY: LOW (Future V2 Feature)
 */