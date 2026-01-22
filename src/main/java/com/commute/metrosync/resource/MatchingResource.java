package com.commute.metrosync.resource;

import com.commute.metrosync.dto.ErrorResponse;
import com.commute.metrosync.service.MatchingService;
import com.commute.metrosync.service.MatchingService.RouteMatchDTO;
import com.commute.metrosync.service.MatchingService.ManifestWaypointDTO;
import com.commute.metrosync.service.MatchingService.ClosestPointDTO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * Corridor-based Matching API for Carpooling
 * 
 * This is what makes MetroSync a CARPOOL app instead of a TAXI app.
 * 
 * Key Endpoints:
 * - POST /match/corridor: Find drivers whose route passes by pickup AND dropoff
 * - GET /match/manifest/{routeVariationId}: Get driver's school-bus style manifest
 * - GET /match/validate: Check if a booking request is feasible
 */
@Path("/match")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Corridor Matching", description = "Carpool-style route matching (NOT taxi-style radius matching)")
public class MatchingResource {

    @Inject
    MatchingService matchingService;

    /**
     * POST /api/v1/match/corridor
     * 
     * Find drivers whose route corridor passes near BOTH pickup and dropoff
     * 
     * This is the CORE DIFFERENCE between carpooling and taxi apps:
     * - Taxi: "Find drivers near me" (radius search)
     * - Carpool: "Find drivers going my way" (corridor search)
     * 
     * Example Request:
     * {
     *   "pickupLatitude": 9.0574,
     *   "pickupLongitude": 7.4905,
     *   "dropoffLatitude": 9.0765,
     *   "dropoffLongitude": 7.4950,
     *   "toleranceMeters": 500
     * }
     * 
     * Example Response:
     * {
     *   "matchCount": 3,
     *   "matches": [
     *     {
     *       "variationId": "...",
     *       "driverId": "...",
     *       "driverName": "Emeka Okafor",
     *       "routeName": "Direct Route (Morning)",
     *       "matchScore": 450.5,
     *       "pickupDistanceM": 200.3,
     *       "dropoffDistanceM": 250.2,
     *       "direction": "TO_WORK",
     *       "estimatedDurationMin": 25
     *     }
     *   ]
     * }
     */
    @POST
    @Path("/corridor")
    @PermitAll
    @Operation(
        summary = "Find corridor matches (Carpool-style)",
        description = "Find drivers whose route passes by BOTH pickup and dropoff locations. " +
                      "This is corridor-based matching, NOT radius-based matching."
    )
    public Response findCorridorMatches(CorridorMatchRequest request) {
        try {
            // Validate coordinates
            validateCoordinates(
                request.pickupLatitude(), 
                request.pickupLongitude(),
                "pickup"
            );
            validateCoordinates(
                request.dropoffLatitude(), 
                request.dropoffLongitude(),
                "dropoff"
            );

            // Validate tolerance
            double tolerance = request.toleranceMeters() != null 
                ? request.toleranceMeters() 
                : 500.0;

            if (tolerance < 100 || tolerance > 2000) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(
                        "Tolerance must be between 100m and 2000m"
                    ))
                    .build();
            }

            // Find corridor matches
            List<RouteMatchDTO> matches = matchingService.findMatches(
                request.pickupLatitude(),
                request.pickupLongitude(),
                request.dropoffLatitude(),
                request.dropoffLongitude(),
                tolerance
            );

            // Build response
            CorridorMatchResponse response = new CorridorMatchResponse(
                matches.size(),
                matches,
                matches.isEmpty() 
                    ? "No drivers found on this corridor. Try increasing tolerance or checking a different route."
                    : String.format("Found %d drivers going your way", matches.size())
            );

            return Response.ok(response).build();

        } catch (IllegalArgumentException e) {
            Log.warn("Invalid corridor match request: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            Log.error("Failed to find corridor matches", e);
            return Response.serverError()
                .entity(new ErrorResponse("Failed to find matches: " + e.getMessage()))
                .build();
        }
    }

    /**
     * GET /api/v1/match/manifest/{routeVariationId}
     * 
     * Get driver's manifest (ordered list of pickup/dropoff stops)
     * This is the "school bus route" - tells driver the sequence of stops
     * 
     * Example Response:
     * {
     *   "routeVariationId": "...",
     *   "waypointCount": 7,
     *   "waypoints": [
     *     {
     *       "stopType": "START",
     *       "label": "Home (Start)",
     *       "latitude": 9.0574,
     *       "longitude": 7.4905,
     *       "sequenceOrder": 0.0
     *     },
     *     {
     *       "bookingId": "...",
     *       "stopType": "PICKUP",
     *       "label": "Amina Bello",
     *       "passengerCount": 1,
     *       "latitude": 9.0600,
     *       "longitude": 7.4920,
     *       "sequenceOrder": 0.25
     *     },
     *     ...
     *   ]
     * }
     */
    @GET
    @Path("/manifest/{routeVariationId}")
    @RolesAllowed({"DRIVER"})
    @Operation(
        summary = "Get driver manifest",
        description = "Get ordered list of pickup/dropoff stops for a route (school bus style)"
    )
    public Response getDriverManifest(
            @PathParam("routeVariationId") 
            @Parameter(description = "Route variation ID")
            UUID routeVariationId) {
        
        try {
            List<ManifestWaypointDTO> waypoints = 
                matchingService.generateDriverManifest(routeVariationId);

            ManifestResponse response = new ManifestResponse(
                routeVariationId,
                waypoints.size(),
                waypoints
            );

            return Response.ok(response).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Route variation not found"))
                .build();
        } catch (Exception e) {
            Log.error("Failed to generate manifest", e);
            return Response.serverError()
                .entity(new ErrorResponse("Failed to generate manifest"))
                .build();
        }
    }

    /**
     * GET /api/v1/match/validate
     * 
     * Validate if a booking request is feasible
     * Checks if both pickup and dropoff are on the route corridor
     * 
     * Example: /api/v1/match/validate?routeVariationId=...&pickupLat=9.0574&pickupLng=7.4905&dropoffLat=9.0765&dropoffLng=7.4950
     * 
     * Response:
     * {
     *   "isValid": true,
     *   "message": "Both pickup and dropoff are on the route corridor"
     * }
     */
    @GET
    @Path("/validate")
    @PermitAll
    @Operation(
        summary = "Validate booking request",
        description = "Check if pickup and dropoff locations are on the route corridor"
    )
    public Response validateBooking(
            @QueryParam("routeVariationId") 
            @NotNull 
            UUID routeVariationId,
            
            @QueryParam("pickupLat") 
            @NotNull 
            @DecimalMin("-90") 
            @DecimalMax("90") 
            Double pickupLat,
            
            @QueryParam("pickupLng") 
            @NotNull 
            @DecimalMin("-180") 
            @DecimalMax("180") 
            Double pickupLng,
            
            @QueryParam("dropoffLat") 
            @NotNull 
            @DecimalMin("-90") 
            @DecimalMax("90") 
            Double dropoffLat,
            
            @QueryParam("dropoffLng") 
            @NotNull 
            @DecimalMin("-180") 
            @DecimalMax("180") 
            Double dropoffLng,
            
            @QueryParam("tolerance") 
            @DefaultValue("500") 
            Double tolerance) {

        try {
            boolean isValid = matchingService.validateBookingRequest(
                routeVariationId,
                pickupLat,
                pickupLng,
                dropoffLat,
                dropoffLng,
                tolerance
            );

            String message = isValid
                ? "Both pickup and dropoff are on the route corridor"
                : "Pickup or dropoff is too far from the route, or in wrong order";

            ValidationResponse response = new ValidationResponse(
                isValid,
                message,
                tolerance
            );

            return Response.ok(response).build();

        } catch (Exception e) {
            Log.error("Failed to validate booking request", e);
            return Response.serverError()
                .entity(new ErrorResponse("Validation failed"))
                .build();
        }
    }

    /**
     * GET /api/v1/match/closest-point
     * 
     * Get the closest point on a route to a given location
     * Useful for showing riders where they'll actually be picked up
     * 
     * Example: /api/v1/match/closest-point?routeVariationId=...&lat=9.0574&lng=7.4905
     * 
     * Response:
     * {
     *   "latitude": 9.0576,
     *   "longitude": 7.4907,
     *   "distanceMeters": 45.3
     * }
     */
    @GET
    @Path("/closest-point")
    @PermitAll
    @Operation(
        summary = "Get closest point on route",
        description = "Find the closest point on a driver's route to a given location"
    )
    public Response getClosestPoint(
            @QueryParam("routeVariationId") 
            @NotNull 
            UUID routeVariationId,
            
            @QueryParam("lat") 
            @NotNull 
            @DecimalMin("-90") 
            @DecimalMax("90") 
            Double latitude,
            
            @QueryParam("lng") 
            @NotNull 
            @DecimalMin("-180") 
            @DecimalMax("180") 
            Double longitude) {

        try {
            ClosestPointDTO closestPoint = matchingService.getClosestPointOnRoute(
                routeVariationId,
                latitude,
                longitude
            );

            return Response.ok(closestPoint).build();

        } catch (Exception e) {
            Log.error("Failed to get closest point", e);
            return Response.serverError()
                .entity(new ErrorResponse("Failed to get closest point"))
                .build();
        }
    }

    // ==================== HELPER METHODS ====================

    private void validateCoordinates(
            double latitude, 
            double longitude, 
            String label) {
        
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(
                label + " latitude must be between -90 and 90"
            );
        }
        
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(
                label + " longitude must be between -180 and 180"
            );
        }
    }

    // ==================== DTOs ====================

    /**
     * Request to find corridor matches
     */
    public record CorridorMatchRequest(
        @NotNull 
        @DecimalMin("-90") 
        @DecimalMax("90") 
        Double pickupLatitude,
        
        @NotNull 
        @DecimalMin("-180") 
        @DecimalMax("180") 
        Double pickupLongitude,
        
        @NotNull 
        @DecimalMin("-90") 
        @DecimalMax("90") 
        Double dropoffLatitude,
        
        @NotNull 
        @DecimalMin("-180") 
        @DecimalMax("180") 
        Double dropoffLongitude,
        
        Double toleranceMeters  // Optional, defaults to 500m
    ) {}

    /**
     * Response with corridor matches
     */
    public record CorridorMatchResponse(
        int matchCount,
        List<RouteMatchDTO> matches,
        String message
    ) {}

    /**
     * Driver manifest response
     */
    public record ManifestResponse(
        UUID routeVariationId,
        int waypointCount,
        List<ManifestWaypointDTO> waypoints
    ) {}

    /**
     * Validation response
     */
    public record ValidationResponse(
        boolean isValid,
        String message,
        double toleranceMeters
    ) {}
}