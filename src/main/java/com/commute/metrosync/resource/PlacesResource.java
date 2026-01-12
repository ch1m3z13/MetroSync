package com.commute.metrosync.resource;

import com.commute.metrosync.service.NominatimService;
import com.commute.metrosync.service.NominatimService.PlaceDetails;
import com.commute.metrosync.service.NominatimService.PlaceSuggestion;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST endpoint for Google Places API (Autocomplete, Place Details)
 * Acts as a secure proxy - keeps API key on the backend
 */
@Path("/places")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Places", description = "Address autocomplete and place details")
public class PlacesResource {
    
    @Inject
    NominatimService nominatimService;
    
    /**
     * GET /api/v1/places/autocomplete?input=police&country=ng
     * Get address autocomplete suggestions using OpenStreetMap
     */
    @GET
    @Path("/autocomplete")
    @PermitAll
    @Operation(
        summary = "Get address suggestions (FREE - OpenStreetMap)",
        description = "Autocomplete address search using Nominatim (no API key required)"
    )
    public Response getAutocompleteSuggestions(
            @QueryParam("input") @DefaultValue("") String input,
            @QueryParam("country") @DefaultValue("ng") String country,
            @QueryParam("limit") @DefaultValue("10") int limit) {
        
        if (input.trim().isEmpty()) {
            return Response.ok(List.of()).build();
        }
        
        List<PlaceSuggestion> suggestions = nominatimService.searchPlaces(
            input,
            country,
            limit
        );
        
        return Response.ok(suggestions).build();
    }
    
    /**
     * GET /api/v1/places/details/{osmId}?type=way
     * Get detailed information about a place using OSM ID
     */
    @GET
    @Path("/details/{osmId}")
    @PermitAll
    @Operation(
        summary = "Get place details (FREE - OpenStreetMap)",
        description = "Get coordinates and full address for an OpenStreetMap ID"
    )
    public Response getPlaceDetails(
            @PathParam("osmId") String osmId,
            @QueryParam("type") @DefaultValue("way") String osmType) {
        
        PlaceDetails details = nominatimService.getPlaceDetails(osmId, osmType);
        
        if (details == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Place not found"))
                    .build();
        }
        
        return Response.ok(details).build();
    }
    
    /**
     * GET /api/v1/places/reverse-geocode?lat=9.0765&lng=7.3986
     * Convert coordinates to address (reverse geocoding) using OpenStreetMap
     */
    @GET
    @Path("/reverse-geocode")
    @PermitAll
    @Operation(
        summary = "Reverse geocode coordinates (FREE - OpenStreetMap)",
        description = "Convert latitude/longitude to a human-readable address using Nominatim"
    )
    public Response reverseGeocode(
            @QueryParam("lat") Double latitude,
            @QueryParam("lng") Double longitude) {
        
        if (latitude == null || longitude == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Both lat and lng parameters are required"))
                    .build();
        }
        
        // Validate coordinate ranges
        if (latitude < -90 || latitude > 90) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Latitude must be between -90 and 90"))
                    .build();
        }
        
        if (longitude < -180 || longitude > 180) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Longitude must be between -180 and 180"))
                    .build();
        }
        
        PlaceDetails details = nominatimService.reverseGeocode(latitude, longitude);
        
        if (details == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No address found for these coordinates"))
                    .build();
        }
        
        return Response.ok(details).build();
    }
    
    private record ErrorResponse(String message) {}
}