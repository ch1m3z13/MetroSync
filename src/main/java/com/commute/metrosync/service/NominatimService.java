package com.commute.metrosync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

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
 * FREE Geocoding Service using OpenStreetMap Nominatim
 * NO API KEY REQUIRED! 🎉
 * 
 * Features:
 * - Address search (autocomplete)
 * - Reverse geocoding (coordinates → address)
 * - Completely free and open source
 */
@ApplicationScoped
public class NominatimService {
    
    private static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";
    private static final String USER_AGENT = "MetroSyncApp/1.0"; // Required by Nominatim
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public NominatimService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Search for places/addresses (Autocomplete)
     * 
     * @param query Search text (e.g., "police station abuja")
     * @param country Country code to limit results (e.g., "ng" for Nigeria)
     * @param limit Maximum number of results (default: 10)
     * @return List of place suggestions
     */
    public List<PlaceSuggestion> searchPlaces(String query, String country, int limit) {
        try {
            String url = buildSearchUrl(query, country, limit);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                Log.error("Nominatim API error: " + response.statusCode());
                return List.of();
            }
            
            return parseSearchResults(response.body());
            
        } catch (Exception e) {
            Log.error("Failed to search places", e);
            return List.of();
        }
    }
    
    /**
     * Reverse geocode: Convert coordinates to address
     * 
     * @param latitude Latitude
     * @param longitude Longitude
     * @return Place details with address
     */
    public PlaceDetails reverseGeocode(double latitude, double longitude) {
        try {
            String url = buildReverseUrl(latitude, longitude);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                Log.error("Nominatim reverse geocode error: " + response.statusCode());
                return null;
            }
            
            return parseReverseResult(response.body(), latitude, longitude);
            
        } catch (Exception e) {
            Log.error("Failed to reverse geocode", e);
            return null;
        }
    }
    
    /**
     * Get place details by OSM ID
     * 
     * @param osmId OpenStreetMap ID (from search results)
     * @param osmType OSM type (node, way, or relation)
     * @return Detailed place information
     */
    public PlaceDetails getPlaceDetails(String osmId, String osmType) {
        try {
            String url = buildLookupUrl(osmId, osmType);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                Log.error("Nominatim lookup error: " + response.statusCode());
                return null;
            }
            
            return parseLookupResult(response.body());
            
        } catch (Exception e) {
            Log.error("Failed to get place details", e);
            return null;
        }
    }
    
    // ==================== URL BUILDERS ====================
    
    private String buildSearchUrl(String query, String country, int limit) {
        StringBuilder url = new StringBuilder(NOMINATIM_BASE_URL + "/search?");
        url.append("q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        url.append("&format=json");
        url.append("&addressdetails=1");
        url.append("&limit=").append(limit);
        
        if (country != null && !country.isEmpty()) {
            url.append("&countrycodes=").append(country);
        }
        
        return url.toString();
    }
    
    private String buildReverseUrl(double latitude, double longitude) {
        return String.format("%s/reverse?lat=%s&lon=%s&format=json&addressdetails=1",
            NOMINATIM_BASE_URL, latitude, longitude);
    }
    
    private String buildLookupUrl(String osmId, String osmType) {
        return String.format("%s/lookup?osm_ids=%s%s&format=json&addressdetails=1",
            NOMINATIM_BASE_URL, osmType.toUpperCase().charAt(0), osmId);
    }
    
    // ==================== PARSERS ====================
    
    private List<PlaceSuggestion> parseSearchResults(String jsonResponse) {
        try {
            JsonNode results = objectMapper.readTree(jsonResponse);
            List<PlaceSuggestion> suggestions = new ArrayList<>();
            
            if (results.isArray()) {
                for (JsonNode result : results) {
                    String placeId = result.get("place_id").asText();
                    String displayName = result.get("display_name").asText();
                    
                    // Extract main text (name or road)
                    String mainText = displayName.split(",")[0].trim();
                    
                    // Extract secondary text (rest of address)
                    String secondaryText = displayName.contains(",") 
                        ? displayName.substring(displayName.indexOf(",") + 1).trim()
                        : "";
                    
                    double lat = result.get("lat").asDouble();
                    double lon = result.get("lon").asDouble();
                    
                    String osmId = result.get("osm_id").asText();
                    String osmType = result.get("osm_type").asText();
                    
                    suggestions.add(new PlaceSuggestion(
                        placeId,
                        displayName,
                        mainText,
                        secondaryText,
                        lat,
                        lon,
                        osmId,
                        osmType
                    ));
                }
            }
            
            return suggestions;
            
        } catch (Exception e) {
            Log.error("Failed to parse search results", e);
            return List.of();
        }
    }
    
    private PlaceDetails parseReverseResult(String jsonResponse, double lat, double lon) {
        try {
            JsonNode result = objectMapper.readTree(jsonResponse);
            
            if (result.has("error")) {
                Log.warn("Nominatim reverse error: " + result.get("error").asText());
                return null;
            }
            
            String displayName = result.get("display_name").asText();
            
            // Extract name from address components
            String name = displayName;
            if (result.has("address")) {
                JsonNode address = result.get("address");
                if (address.has("road")) {
                    name = address.get("road").asText();
                } else if (address.has("suburb")) {
                    name = address.get("suburb").asText();
                } else if (address.has("neighbourhood")) {
                    name = address.get("neighbourhood").asText();
                }
            }
            
            return new PlaceDetails(
                name,
                displayName,
                lat,
                lon
            );
            
        } catch (Exception e) {
            Log.error("Failed to parse reverse result", e);
            return null;
        }
    }
    
    private PlaceDetails parseLookupResult(String jsonResponse) {
        try {
            JsonNode results = objectMapper.readTree(jsonResponse);
            
            if (!results.isArray() || results.size() == 0) {
                return null;
            }
            
            JsonNode result = results.get(0);
            
            double lat = result.get("lat").asDouble();
            double lon = result.get("lon").asDouble();
            String displayName = result.get("display_name").asText();
            
            String name = displayName.split(",")[0].trim();
            
            return new PlaceDetails(
                name,
                displayName,
                lat,
                lon
            );
            
        } catch (Exception e) {
            Log.error("Failed to parse lookup result", e);
            return null;
        }
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