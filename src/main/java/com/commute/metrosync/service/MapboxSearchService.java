package com.commute.metrosync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapbox Search API Service
 * MUCH BETTER than Nominatim for African addresses
 * 
 * Pricing:
 * - FREE: 100,000 requests/month
 * - After: $0.50 per 1,000 requests
 * 
 * Get API Key:
 * 1. Sign up at https://account.mapbox.com/
 * 2. Copy your Access Token
 * 3. Add to application.properties:
 *    mapbox.access.token=pk.eyJ1IjoieW91ci11c2VybmFtZSIsImEiOiJjbHh4eHh4eHgifQ.xxxxx
 */
@ApplicationScoped
public class MapboxSearchService {
    
    @ConfigProperty(name = "mapbox.access.token")
    String mapboxToken;
    
    private static final String MAPBOX_BASE_URL = "https://api.mapbox.com";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public MapboxSearchService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Search for places (Autocomplete)
     * BETTER at finding:
     * - Landmarks ("Police Station Dutse")
     * - POIs ("Jabi Lake Mall")
     * - Informal addresses
     * 
     * @param query User's search text
     * @param limit Max results
     * @return List of suggestions
     */
    public List<PlaceSuggestion> searchPlaces(String query, int limit) {
        try {
            // Mapbox Search Box API (Autocomplete)
            // Biased to Nigeria (bbox parameter)
            String url = buildSearchUrl(query, limit);
            
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
                Log.error("Mapbox API error: " + response.statusCode());
                return List.of();
            }
            
            return parseSearchResults(response.body());
            
        } catch (Exception e) {
            Log.error("Failed to search places with Mapbox", e);
            return List.of();
        }
    }
    
    /**
     * Reverse geocode: Coordinates → Address
     * 
     * @param latitude Latitude
     * @param longitude Longitude
     * @return Place details
     */
    public PlaceDetails reverseGeocode(double latitude, double longitude) {
        try {
            String url = buildReverseUrl(latitude, longitude);
            
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
                Log.error("Mapbox reverse geocode error: " + response.statusCode());
                return null;
            }
            
            return parseReverseResult(response.body());
            
        } catch (Exception e) {
            Log.error("Failed to reverse geocode with Mapbox", e);
            return null;
        }
    }
    
    // ==================== URL BUILDERS ====================
    
    private String buildSearchUrl(String query, int limit) {
        StringBuilder url = new StringBuilder(MAPBOX_BASE_URL);
        url.append("/search/geocode/v6/forward?");
        url.append("q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        url.append("&limit=").append(Math.min(limit, 10));
        
        // Bias results to Nigeria (Bounding Box for Nigeria)
        // Southwest: [2.6, 4.2], Northeast: [14.7, 13.9]
        url.append("&bbox=2.6,4.2,14.7,13.9");
        
        // Prioritize Nigeria
        url.append("&country=ng");
        
        // Include POIs (Points of Interest)
        url.append("&types=poi,address,place,locality,neighborhood");
        
        url.append("&access_token=").append(mapboxToken);
        
        return url.toString();
    }
    
    private String buildReverseUrl(double latitude, double longitude) {
        return String.format(
            "%s/search/geocode/v6/reverse?longitude=%s&latitude=%s&types=poi,address,place&access_token=%s",
            MAPBOX_BASE_URL, longitude, latitude, mapboxToken
        );
    }
    
    // ==================== PARSERS ====================
    
    private List<PlaceSuggestion> parseSearchResults(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode features = root.get("features");
            
            List<PlaceSuggestion> suggestions = new ArrayList<>();
            
            if (features != null && features.isArray()) {
                for (JsonNode feature : features) {
                    JsonNode properties = feature.get("properties");
                    JsonNode geometry = feature.get("geometry");
                    
                    if (properties == null || geometry == null) continue;
                    
                    // Extract place details
                    String name = properties.has("name") 
                        ? properties.get("name").asText() 
                        : "";
                    
                    String fullAddress = properties.has("full_address")
                        ? properties.get("full_address").asText()
                        : properties.has("place_formatted") 
                            ? properties.get("place_formatted").asText()
                            : "";
                    
                    // Extract coordinates
                    JsonNode coordinates = geometry.get("coordinates");
                    double lon = coordinates.get(0).asDouble();
                    double lat = coordinates.get(1).asDouble();
                    
                    // Extract context (district, city, state)
                    String secondaryText = extractSecondaryText(properties);
                    
                    suggestions.add(new PlaceSuggestion(
                        properties.has("mapbox_id") ? properties.get("mapbox_id").asText() : "",
                        fullAddress,
                        name,
                        secondaryText,
                        lat,
                        lon,
                        "", // Mapbox uses different ID system
                        "" 
                    ));
                }
            }
            
            return suggestions;
            
        } catch (Exception e) {
            Log.error("Failed to parse Mapbox search results", e);
            return List.of();
        }
    }
    
    private PlaceDetails parseReverseResult(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode features = root.get("features");
            
            if (features == null || !features.isArray() || features.size() == 0) {
                return null;
            }
            
            JsonNode feature = features.get(0);
            JsonNode properties = feature.get("properties");
            JsonNode geometry = feature.get("geometry");
            
            String name = properties.has("name") 
                ? properties.get("name").asText() 
                : "Location";
            
            String fullAddress = properties.has("full_address")
                ? properties.get("full_address").asText()
                : properties.has("place_formatted")
                    ? properties.get("place_formatted").asText()
                    : "";
            
            JsonNode coordinates = geometry.get("coordinates");
            double lon = coordinates.get(0).asDouble();
            double lat = coordinates.get(1).asDouble();
            
            return new PlaceDetails(
                name,
                fullAddress,
                lat,
                lon
            );
            
        } catch (Exception e) {
            Log.error("Failed to parse Mapbox reverse result", e);
            return null;
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Extract secondary text (neighborhood, district, city)
     */
    private String extractSecondaryText(JsonNode properties) {
        List<String> parts = new ArrayList<>();
        
        // Mapbox context structure
        if (properties.has("context")) {
            JsonNode context = properties.get("context");
            
            if (context.has("neighborhood")) {
                parts.add(context.get("neighborhood").get("name").asText());
            }
            if (context.has("place")) {
                parts.add(context.get("place").get("name").asText());
            }
            if (context.has("region")) {
                parts.add(context.get("region").get("name").asText());
            }
        }
        
        // Fallback to place_formatted
        if (parts.isEmpty() && properties.has("place_formatted")) {
            return properties.get("place_formatted").asText();
        }
        
        return String.join(", ", parts);
    }
    
    // ==================== DTOs ====================
    
    public record PlaceSuggestion(
        String placeId,
        String displayName,
        String mainText,
        String secondaryText,
        double latitude,
        double longitude,
        String osmId,
        String osmType
    ) {}
    
    public record PlaceDetails(
        String name,
        String formattedAddress,
        double latitude,
        double longitude
    ) {}
}