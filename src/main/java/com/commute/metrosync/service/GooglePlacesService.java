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
 * Google Places API Service
 * THE BEST for finding Nigerian landmarks and informal addresses
 * 
 * Pricing (as of 2024):
 * - Autocomplete: $2.83 per 1,000 requests
 * - Place Details: $17 per 1,000 requests
 * - FREE: $200 credit/month
 * 
 * Strategy:
 * 1. Use Google for SEARCH (accurate results)
 * 2. Use Mapbox/OSM for MAP DISPLAY (cheap/free)
 * 
 * Get API Key:
 * 1. https://console.cloud.google.com/
 * 2. Enable "Places API (New)"
 * 3. Create credentials → API Key
 * 4. Restrict to your IP/domain
 * 5. Add to application.properties:
 *    google.places.api.key=AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXX
 */
@ApplicationScoped
public class GooglePlacesService {
    
    @ConfigProperty(name = "google.places.api.key")
    String apiKey;
    
    private static final String PLACES_BASE_URL = "https://places.googleapis.com/v1";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public GooglePlacesService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Autocomplete search using Google Places API (New)
     * BEST at finding:
     * - "Police Station Dutse Makaranta"
     * - "Behind Total Filling Station"
     * - "Opposite Jabi Lake Mall"
     * 
     * @param query User's search text
     * @param limit Max results
     * @return List of suggestions
     */
    public List<PlaceSuggestion> searchPlaces(String query, int limit) {
        try {
            String url = PLACES_BASE_URL + "/places:autocomplete";
            
            // Request body (New Places API uses POST)
            String requestBody = String.format("""
                {
                  "input": "%s",
                  "locationBias": {
                    "rectangle": {
                      "low": {
                        "latitude": 8.0,
                        "longitude": 6.5
                      },
                      "high": {
                        "latitude": 10.0,
                        "longitude": 8.0
                      }
                    }
                  },
                  "includedRegionCodes": ["NG"],
                  "languageCode": "en"
                }
                """, query);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "suggestions.placePrediction.place,suggestions.placePrediction.text")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                Log.error("Google Places API error: " + response.statusCode() + " - " + response.body());
                return List.of();
            }
            
            return parseAutocompleteResults(response.body(), limit);
            
        } catch (Exception e) {
            Log.error("Failed to search with Google Places", e);
            return List.of();
        }
    }
    
    /**
     * Get detailed place information including coordinates
     * 
     * @param placeId Google Place ID from autocomplete
     * @return Place details with lat/lng
     */
    public PlaceDetails getPlaceDetails(String placeId) {
        try {
            String url = PLACES_BASE_URL + "/places/" + placeId;
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "displayName,formattedAddress,location")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                Log.error("Google Place Details error: " + response.statusCode());
                return null;
            }
            
            return parsePlaceDetails(response.body());
            
        } catch (Exception e) {
            Log.error("Failed to get place details", e);
            return null;
        }
    }
    
    /**
     * Reverse geocode using Google Geocoding API
     * 
     * @param latitude Latitude
     * @param longitude Longitude
     * @return Address details
     */
    public PlaceDetails reverseGeocode(double latitude, double longitude) {
        try {
            String url = String.format(
                "https://maps.googleapis.com/maps/api/geocode/json?latlng=%s,%s&key=%s",
                latitude, longitude, apiKey
            );
            
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
                Log.error("Google Reverse Geocode error: " + response.statusCode());
                return null;
            }
            
            return parseGeocodeResult(response.body());
            
        } catch (Exception e) {
            Log.error("Failed to reverse geocode with Google", e);
            return null;
        }
    }
    
    // ==================== PARSERS ====================
    
    private List<PlaceSuggestion> parseAutocompleteResults(String jsonResponse, int limit) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode suggestions = root.get("suggestions");
            
            List<PlaceSuggestion> results = new ArrayList<>();
            
            if (suggestions != null && suggestions.isArray()) {
                int count = 0;
                for (JsonNode suggestion : suggestions) {
                    if (count >= limit) break;
                    
                    JsonNode prediction = suggestion.get("placePrediction");
                    if (prediction == null) continue;
                    
                    // Extract place ID
                    String placeId = prediction.has("place")
                        ? prediction.get("place").asText().replace("places/", "")
                        : "";
                    
                    // Extract text
                    JsonNode text = prediction.get("text");
                    String mainText = text.has("text") ? text.get("text").asText() : "";
                    
                    String secondaryText = "";
                    if (prediction.has("structuredFormat")) {
                        JsonNode structured = prediction.get("structuredFormat");
                        if (structured.has("secondaryText")) {
                            secondaryText = structured.get("secondaryText").get("text").asText();
                        }
                    }
                    
                    results.add(new PlaceSuggestion(
                        placeId,
                        mainText,
                        mainText,
                        secondaryText,
                        0, 0, // Coordinates fetched separately
                        "", ""
                    ));
                    
                    count++;
                }
            }
            
            return results;
            
        } catch (Exception e) {
            Log.error("Failed to parse Google autocomplete results", e);
            return List.of();
        }
    }
    
    private PlaceDetails parsePlaceDetails(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            String name = root.has("displayName") && root.get("displayName").has("text")
                ? root.get("displayName").get("text").asText()
                : "";
            
            String address = root.has("formattedAddress")
                ? root.get("formattedAddress").asText()
                : "";
            
            JsonNode location = root.get("location");
            double lat = location.get("latitude").asDouble();
            double lng = location.get("longitude").asDouble();
            
            return new PlaceDetails(name, address, lat, lng);
            
        } catch (Exception e) {
            Log.error("Failed to parse place details", e);
            return null;
        }
    }
    
    private PlaceDetails parseGeocodeResult(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode results = root.get("results");
            
            if (results == null || !results.isArray() || results.size() == 0) {
                return null;
            }
            
            JsonNode result = results.get(0);
            
            String address = result.get("formatted_address").asText();
            String name = address.split(",")[0].trim();
            
            JsonNode location = result.get("geometry").get("location");
            double lat = location.get("lat").asDouble();
            double lng = location.get("lng").asDouble();
            
            return new PlaceDetails(name, address, lat, lng);
            
        } catch (Exception e) {
            Log.error("Failed to parse geocode result", e);
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