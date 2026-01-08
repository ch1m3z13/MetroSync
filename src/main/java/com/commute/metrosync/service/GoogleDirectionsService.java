package com.commute.metrosync.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for fetching real road routes using Google Directions API
 * Converts straight-line routes into actual drivable paths
 */
@ApplicationScoped
public class GoogleDirectionsService {
    
    @ConfigProperty(name = "google.maps.api.key", defaultValue = "")
    String googleMapsApiKey;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory;
    
    public GoogleDirectionsService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
        this.geometryFactory = new GeometryFactory();
    }
    
    /**
     * Get route with actual road geometry from Google Directions API
     * 
     * @param originLat Origin latitude
     * @param originLng Origin longitude
     * @param destLat Destination latitude
     * @param destLng Destination longitude
     * @param departureTime Optional departure time for traffic data
     * @return Route response with polyline and distance
     */
    public RouteResponse getRoute(
            double originLat, 
            double originLng,
            double destLat, 
            double destLng,
            String departureTime) {
        
        if (googleMapsApiKey.isEmpty()) {
            Log.warn("Google Maps API key not configured. Using straight-line route.");
            return createStraightLineRoute(originLat, originLng, destLat, destLng);
        }
        
        try {
            // Build API URL
            String url = buildDirectionsUrl(
                originLat, originLng, 
                destLat, destLng, 
                departureTime
            );
            
            // Make request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                Log.error("Google Directions API error: " + response.statusCode());
                return createStraightLineRoute(originLat, originLng, destLat, destLng);
            }
            
            // Parse response
            return parseDirectionsResponse(response.body(), originLat, originLng, destLat, destLng);
            
        } catch (Exception e) {
            Log.error("Failed to fetch directions from Google", e);
            // Fallback to straight line
            return createStraightLineRoute(originLat, originLng, destLat, destLng);
        }
    }
    
    /**
     * Get multiple route alternatives
     */
    public List<RouteResponse> getRouteAlternatives(
            double originLat, 
            double originLng,
            double destLat, 
            double destLng) {
        
        if (googleMapsApiKey.isEmpty()) {
            return List.of(createStraightLineRoute(originLat, originLng, destLat, destLng));
        }
        
        try {
            String url = buildDirectionsUrl(originLat, originLng, destLat, destLng, null)
                + "&alternatives=true";  // Request alternatives
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                return List.of(createStraightLineRoute(originLat, originLng, destLat, destLng));
            }
            
            return parseMultipleRoutes(response.body(), originLat, originLng, destLat, destLng);
            
        } catch (Exception e) {
            Log.error("Failed to fetch route alternatives", e);
            return List.of(createStraightLineRoute(originLat, originLng, destLat, destLng));
        }
    }
    
    /**
     * Build Google Directions API URL
     */
    private String buildDirectionsUrl(
            double originLat, double originLng,
            double destLat, double destLng,
            String departureTime) {
        
        StringBuilder url = new StringBuilder("https://maps.googleapis.com/maps/api/directions/json?");
        url.append("origin=").append(originLat).append(",").append(originLng);
        url.append("&destination=").append(destLat).append(",").append(destLng);
        url.append("&mode=driving");
        url.append("&key=").append(googleMapsApiKey);
        
        // Add departure time for traffic-aware routing
        if (departureTime != null && !departureTime.isEmpty()) {
            url.append("&departure_time=").append(URLEncoder.encode(departureTime, StandardCharsets.UTF_8));
        }
        
        return url.toString();
    }
    
    /**
     * Parse Google Directions API response
     */
    private RouteResponse parseDirectionsResponse(
            String jsonResponse,
            double originLat, double originLng,
            double destLat, double destLng) throws Exception {
        
        JsonNode root = objectMapper.readTree(jsonResponse);
        
        // Check status
        String status = root.get("status").asText();
        if (!"OK".equals(status)) {
            Log.warn("Google Directions API status: " + status);
            return createStraightLineRoute(originLat, originLng, destLat, destLng);
        }
        
        // Get first route
        JsonNode route = root.get("routes").get(0);
        JsonNode leg = route.get("legs").get(0);
        
        // Extract distance (in meters)
        double distanceMeters = leg.get("distance").get("value").asDouble();
        double distanceKm = distanceMeters / 1000.0;
        
        // Extract duration (in seconds)
        int durationSeconds = leg.get("duration").get("value").asInt();
        int durationMinutes = durationSeconds / 60;
        
        // Extract polyline
        String encodedPolyline = route.get("overview_polyline").get("points").asText();
        
        // Decode polyline to coordinates
        List<Coordinate> coordinates = decodePolyline(encodedPolyline);
        
        // Create LineString geometry
        LineString geometry = geometryFactory.createLineString(
            coordinates.toArray(new Coordinate[0])
        );
        geometry.setSRID(4326);
        
        // Extract summary (e.g., "Via I-95 N")
        String summary = route.has("summary") ? route.get("summary").asText() : "Main Route";
        
        return new RouteResponse(
            geometry,
            distanceKm,
            durationMinutes,
            summary,
            encodedPolyline,
            true  // isRealRoute
        );
    }
    
    /**
     * Parse multiple route alternatives
     */
    private List<RouteResponse> parseMultipleRoutes(
            String jsonResponse,
            double originLat, double originLng,
            double destLat, double destLng) throws Exception {
        
        JsonNode root = objectMapper.readTree(jsonResponse);
        String status = root.get("status").asText();
        
        if (!"OK".equals(status)) {
            return List.of(createStraightLineRoute(originLat, originLng, destLat, destLng));
        }
        
        JsonNode routes = root.get("routes");
        List<RouteResponse> alternatives = new ArrayList<>();
        
        for (JsonNode route : routes) {
            JsonNode leg = route.get("legs").get(0);
            
            double distanceKm = leg.get("distance").get("value").asDouble() / 1000.0;
            int durationMinutes = leg.get("duration").get("value").asInt() / 60;
            String encodedPolyline = route.get("overview_polyline").get("points").asText();
            String summary = route.has("summary") ? route.get("summary").asText() : "Route " + (alternatives.size() + 1);
            
            List<Coordinate> coordinates = decodePolyline(encodedPolyline);
            LineString geometry = geometryFactory.createLineString(
                coordinates.toArray(new Coordinate[0])
            );
            geometry.setSRID(4326);
            
            alternatives.add(new RouteResponse(
                geometry,
                distanceKm,
                durationMinutes,
                summary,
                encodedPolyline,
                true
            ));
        }
        
        return alternatives;
    }
    
    /**
     * Decode Google's encoded polyline format
     * Algorithm: https://developers.google.com/maps/documentation/utilities/polylinealgorithm
     */
    private List<Coordinate> decodePolyline(String encoded) {
        List<Coordinate> coordinates = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;
        
        while (index < encoded.length()) {
            int b, shift = 0, result = 0;
            
            // Decode latitude
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            
            shift = 0;
            result = 0;
            
            // Decode longitude
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            
            // Add coordinate (lon, lat for PostGIS)
            coordinates.add(new Coordinate(
                lng / 1E5,  // longitude
                lat / 1E5   // latitude
            ));
        }
        
        return coordinates;
    }
    
    /**
     * Fallback: Create simple straight-line route
     */
    private RouteResponse createStraightLineRoute(
            double originLat, double originLng,
            double destLat, double destLng) {
        
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(originLng, originLat),
            new Coordinate(destLng, destLat)
        };
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        // Calculate straight-line distance
        double distance = geometry.getLength() * 111.0; // Rough km per degree
        int estimatedDuration = (int) (distance * 2); // Assume 30 km/h average
        
        return new RouteResponse(
            geometry,
            Math.round(distance * 100.0) / 100.0,
            estimatedDuration,
            "Direct Route",
            "",
            false  // Not a real route
        );
    }
    
    /**
     * Response object containing route data
     */
    public record RouteResponse(
        LineString geometry,
        double distanceKm,
        int durationMinutes,
        String summary,
        String encodedPolyline,
        boolean isRealRoute
    ) {}
}