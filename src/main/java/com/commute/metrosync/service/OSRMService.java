package com.commute.metrosync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
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
 * FREE Routing Service using OSRM (Open Source Routing Machine)
 * NO API KEY REQUIRED! 🎉
 * 
 * Features:
 * - Turn-by-turn directions
 * - Multiple route alternatives
 * - Distance and duration calculations
 * - Polyline geometry for map display
 */
@ApplicationScoped
public class OSRMService {
    
    private static final String OSRM_BASE_URL = "https://router.project-osrm.org";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory;
    
    public OSRMService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        this.objectMapper = new ObjectMapper();
        this.geometryFactory = new GeometryFactory();
    }
    
    /**
     * Get a single route between two points
     * 
     * @param originLat Origin latitude
     * @param originLng Origin longitude
     * @param destLat Destination latitude
     * @param destLng Destination longitude
     * @return Route with geometry and details
     */
    public RouteResponse getRoute(
            double originLat,
            double originLng,
            double destLat,
            double destLng) {
        
        try {
            String url = buildRouteUrl(originLat, originLng, destLat, destLng, false);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                Log.error("OSRM API error: " + response.statusCode());
                return createStraightLineRoute(originLat, originLng, destLat, destLng);
            }
            
            return parseRouteResponse(response.body());
            
        } catch (Exception e) {
            Log.error("Failed to get route from OSRM", e);
            return createStraightLineRoute(originLat, originLng, destLat, destLng);
        }
    }
    
    /**
     * Get multiple route alternatives
     * 
     * @param originLat Origin latitude
     * @param originLng Origin longitude
     * @param destLat Destination latitude
     * @param destLng Destination longitude
     * @return List of alternative routes
     */
    public List<RouteResponse> getRouteAlternatives(
            double originLat,
            double originLng,
            double destLat,
            double destLng) {
        
        try {
            String url = buildRouteUrl(originLat, originLng, destLat, destLng, true);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                Log.error("OSRM alternatives error: " + response.statusCode());
                return List.of(createStraightLineRoute(originLat, originLng, destLat, destLng));
            }
            
            return parseMultipleRoutes(response.body());
            
        } catch (Exception e) {
            Log.error("Failed to get route alternatives", e);
            return List.of(createStraightLineRoute(originLat, originLng, destLat, destLng));
        }
    }
    
    // ==================== URL BUILDERS ====================
    
    private String buildRouteUrl(
            double originLat,
            double originLng,
            double destLat,
            double destLng,
            boolean alternatives) {
        
        // OSRM uses lon,lat format (opposite of lat,lon)
        StringBuilder url = new StringBuilder(OSRM_BASE_URL);
        url.append("/route/v1/driving/");
        url.append(originLng).append(",").append(originLat).append(";");
        url.append(destLng).append(",").append(destLat);
        url.append("?overview=full");
        url.append("&geometries=geojson");
        url.append("&steps=true");
        
        if (alternatives) {
            url.append("&alternatives=true");
            url.append("&number_of_alternatives=3");
        }
        
        return url.toString();
    }
    
    // ==================== PARSERS ====================
    
    private RouteResponse parseRouteResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            String code = root.get("code").asText();
            if (!"Ok".equals(code)) {
                Log.warn("OSRM response code: " + code);
                return null;
            }
            
            JsonNode routes = root.get("routes");
            if (routes == null || !routes.isArray() || routes.size() == 0) {
                return null;
            }
            
            return parseRoute(routes.get(0));
            
        } catch (Exception e) {
            Log.error("Failed to parse OSRM route", e);
            return null;
        }
    }
    
    private List<RouteResponse> parseMultipleRoutes(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            String code = root.get("code").asText();
            if (!"Ok".equals(code)) {
                Log.warn("OSRM response code: " + code);
                return List.of();
            }
            
            JsonNode routes = root.get("routes");
            if (routes == null || !routes.isArray()) {
                return List.of();
            }
            
            List<RouteResponse> alternatives = new ArrayList<>();
            for (int i = 0; i < routes.size(); i++) {
                RouteResponse route = parseRoute(routes.get(i));
                if (route != null) {
                    alternatives.add(route);
                }
            }
            
            return alternatives;
            
        } catch (Exception e) {
            Log.error("Failed to parse OSRM alternatives", e);
            return List.of();
        }
    }
    
    private RouteResponse parseRoute(JsonNode routeNode) {
        try {
            // Extract distance (in meters)
            double distanceMeters = routeNode.get("distance").asDouble();
            double distanceKm = Math.round(distanceMeters / 10.0) / 100.0; // Round to 2 decimals
            
            // Extract duration (in seconds)
            double durationSeconds = routeNode.get("duration").asDouble();
            int durationMinutes = (int) Math.ceil(durationSeconds / 60.0);
            
            // Extract geometry coordinates
            JsonNode geometry = routeNode.get("geometry");
            JsonNode coordinates = geometry.get("coordinates");
            
            List<Coordinate> coords = new ArrayList<>();
            if (coordinates.isArray()) {
                for (JsonNode coord : coordinates) {
                    double lon = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    coords.add(new Coordinate(lon, lat));
                }
            }
            
            // Create LineString
            LineString lineString = geometryFactory.createLineString(
                coords.toArray(new Coordinate[0])
            );
            lineString.setSRID(4326);
            
            // Extract summary (first and last step names)
            String summary = "Main Route";
            if (routeNode.has("legs") && routeNode.get("legs").isArray()) {
                JsonNode legs = routeNode.get("legs");
                if (legs.size() > 0) {
                    JsonNode firstLeg = legs.get(0);
                    if (firstLeg.has("summary")) {
                        summary = firstLeg.get("summary").asText();
                    }
                }
            }
            
            return new RouteResponse(
                lineString,
                distanceKm,
                durationMinutes,
                summary,
                "", // OSRM doesn't provide encoded polyline, we have the geometry directly
                true
            );
            
        } catch (Exception e) {
            Log.error("Failed to parse route node", e);
            return null;
        }
    }
    
    /**
     * Fallback: Create simple straight-line route
     */
    private RouteResponse createStraightLineRoute(
            double originLat,
            double originLng,
            double destLat,
            double destLng) {
        
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(originLng, originLat),
            new Coordinate(destLng, destLat)
        };
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        // Calculate straight-line distance using Haversine
        double distance = calculateHaversineDistance(originLat, originLng, destLat, destLng);
        int estimatedDuration = (int) (distance * 2); // Assume 30 km/h average
        
        return new RouteResponse(
            geometry,
            Math.round(distance * 100.0) / 100.0,
            estimatedDuration,
            "Direct Route",
            "",
            false
        );
    }
    
    /**
     * Calculate distance between two points using Haversine formula
     */
    private double calculateHaversineDistance(
            double lat1, double lon1,
            double lat2, double lon2) {
        
        final double R = 6371; // Earth's radius in km
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    // ==================== DTO ====================
    
    public record RouteResponse(
        LineString geometry,
        double distanceKm,
        int durationMinutes,
        String summary,
        String encodedPolyline,
        boolean isRealRoute
    ) {}
}