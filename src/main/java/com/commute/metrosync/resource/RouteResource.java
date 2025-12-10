package com.commute.metrosync.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.commute.metrosync.entity.Route;
import com.commute.metrosync.service.RouteService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * REST API for route operations.
 * Follows Jakarta REST standards.
 */
@Path("/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Routes", description = "Route management and geospatial matching")
public class RouteResource {
    
    @Inject
    RouteService routeService;
    
    /**
     * Find drivers passing near a location.
     * Primary endpoint for rider-driver matching.
     */
    @GET
    @Path("/nearby")
    @Operation(
        summary = "Find nearby drivers",
        description = "Search for drivers passing within specified radius of a location"
    )
    public Response findNearbyDrivers(
            @QueryParam("lat") 
            @NotNull
            @DecimalMin("-90") @DecimalMax("90")
            @Parameter(description = "Latitude", required = true, example = "9.0765")
            Double latitude,
            
            @QueryParam("lon") 
            @NotNull
            @DecimalMin("-180") @DecimalMax("180")
            @Parameter(description = "Longitude", required = true, example = "7.3986")
            Double longitude,
            
            @QueryParam("radius") 
            @DefaultValue("500")
            @Parameter(description = "Search radius in meters", example = "500")
            Double radiusMeters) {
        
        List<Route> routes = routeService.findNearbyDrivers(
            latitude, 
            longitude, 
            radiusMeters
        );
        
        return Response.ok(routes).build();
    }
    
    /**
     * Find drivers heading towards a destination.
     */
    @GET
    @Path("/heading-to")
    @Operation(summary = "Find drivers heading towards destination")
    public Response findDriversHeadingTo(
            @QueryParam("originLat") @NotNull Double originLat,
            @QueryParam("originLon") @NotNull Double originLon,
            @QueryParam("destLat") @NotNull Double destLat,
            @QueryParam("destLon") @NotNull Double destLon,
            @QueryParam("radius") @DefaultValue("1000") Double radius) {
        
        List<Route> routes = routeService.findDriversHeadingTo(
            originLat, originLon,
            destLat, destLon,
            radius
        );
        
        return Response.ok(routes).build();
    }
    
    /**
     * Create a new route.
     */
    @POST
    @Operation(summary = "Create a new route")
    public Response createRoute(@Valid RouteService.CreateRouteRequest request) {
        Route route = routeService.createRoute(request);
        return Response.status(Response.Status.CREATED)
                .entity(route)
                .build();
    }
    
    /**
     * Validate if a point is a valid pickup location for a route.
     */
    @GET
    @Path("/{routeId}/validate-pickup")
    @Operation(summary = "Validate pickup point")
    public Response validatePickupPoint(
            @PathParam("routeId") UUID routeId,
            @QueryParam("lat") @NotNull Double latitude,
            @QueryParam("lon") @NotNull Double longitude) {
        
        boolean isValid = routeService.isValidPickupPoint(
            routeId, 
            latitude, 
            longitude
        );
        
        return Response.ok(new ValidationResponse(isValid)).build();
    }
    
    record ValidationResponse(boolean isValid) {}
}