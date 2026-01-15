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
 * REST endpoint for Self-Hosted Nominatim (Address Autocomplete)
 * No API key needed - fully self-hosted
 * 
 * Frontend Integration:
 * - Use for address autocomplete when user types
 * - Use for reverse geocoding GPS coordinates
 */
@Path("/places")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Places", description = "Address search using self-hosted Nominatim")
public class PlacesResource {
    
    @Inject
    NominatimService nominatimService;
    
    /**
     * GET /api/v1/places/autocomplete?input=wuse
     * Address autocomplete for user input
     * 
     * Frontend should:
     * - Debounce input (300ms delay after typing stops)
     * - Show loading indicator
     * - Display results in dropdown
     */
    @GET
    @Path("/autocomplete")
    @PermitAll
    @Operation(
        summary = "Address autocomplete",
        description = "Search for Nigerian addresses as user types. Self-hosted, no API key required."
    )
    public Response getAutocompleteSuggestions(
            @QueryParam("input") @DefaultValue("") String input,
            @QueryParam("limit") @DefaultValue("10") int limit) {
        
        if (input.trim().isEmpty()) {
            return Response.ok(List.of()).build();
        }
        
        // Minimum 2 characters to search
        if (input.trim().length() < 2) {
            return Response.ok(List.of()).build();
        }
        
        List<PlaceSuggestion> suggestions = nominatimService.searchPlaces(input, limit);
        
        return Response.ok(suggestions).build();
    }
    
    /**
     * GET /api/v1/places/reverse-geocode?lat=9.0765&lng=7.3986
     * Convert GPS coordinates to human-readable address
     * 
     * Frontend should:
     * - Call this after getting GPS coordinates
     * - Display the formatted address to user
     * - Save coordinates + formatted address to backend
     */
    @GET
    @Path("/reverse-geocode")
    @PermitAll
    @Operation(
        summary = "Reverse geocode GPS coordinates",
        description = "Convert latitude/longitude to a readable address using self-hosted Nominatim"
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
    
    /**
     * GET /api/v1/places/details/{osmId}?type=way
     * Get detailed information about a selected place
     * 
     * Frontend should:
     * - Call this when user selects an autocomplete suggestion
     * - Extract precise coordinates and full address
     */
    @GET
    @Path("/details/{osmId}")
    @PermitAll
    @Operation(
        summary = "Get place details",
        description = "Get full details for a selected place from autocomplete"
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
    
    private record ErrorResponse(String message) {}
}