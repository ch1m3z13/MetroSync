package com.commute.metrosync.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.commute.metrosync.entity.Route;
import com.commute.metrosync.entity.VirtualStop;
import com.commute.metrosync.repository.RouteRepository;
import com.commute.metrosync.service.RouteService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for route operations.
 * Public endpoints allow unauthenticated users to browse available routes.
 * Protected endpoints require authentication for route management.
 */
@Path("/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Routes", description = "Route management and geospatial matching")
public class RouteResource {
    
    @Inject
    RouteService routeService;
    
    @Inject
    RouteRepository routeRepository;
    
    /**
     * PUBLIC: Find drivers passing near a location.
     * Primary endpoint for rider-driver matching.
     * No authentication required - allows users to browse before signing up.
     */
    @GET
    @Path("/nearby")
    @PermitAll
    @Operation(
        summary = "Find nearby drivers (Public)",
        description = "Search for drivers passing within specified radius of a location. No authentication required."
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
        
        // Convert to DTOs with limited information for public access
        List<PublicRouteDTO> routeDTOs = routes.stream()
            .map(this::toPublicDTO)
            .collect(Collectors.toList());
        
        return Response.ok(routeDTOs).build();
    }
    
    /**
     * PUBLIC: Find drivers heading towards a destination.
     * No authentication required.
     */
    @GET
    @Path("/heading-to")
    @PermitAll
    @Operation(
        summary = "Find drivers heading towards destination (Public)",
        description = "Search for drivers heading in the direction of your destination. No authentication required."
    )
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
        
        List<PublicRouteDTO> routeDTOs = routes.stream()
            .map(this::toPublicDTO)
            .collect(Collectors.toList());
        
        return Response.ok(routeDTOs).build();
    }
    
    /**
     * PUBLIC: Get all published routes.
     * Allows browsing all available routes.
     */
    @GET
    @PermitAll
    @Operation(
        summary = "List all published routes (Public)",
        description = "Get all active and published routes. No authentication required."
    )
    public Response listPublishedRoutes() {
        List<Route> routes = routeRepository.findPublishedRoutes();
        
        List<PublicRouteDTO> routeDTOs = routes.stream()
            .map(this::toPublicDTO)
            .collect(Collectors.toList());
        
        return Response.ok(routeDTOs).build();
    }
    
    /**
     * PUBLIC: Get details of a specific route.
     * Includes virtual stops and route geometry.
     */
    @GET
    @Path("/{routeId}")
    @PermitAll
    @Operation(
        summary = "Get route details (Public)",
        description = "Get detailed information about a specific route including stops. No authentication required."
    )
    public Response getRouteDetails(@PathParam("routeId") UUID routeId) {
        Route route = routeRepository.findByIdOptional(routeId)
            .orElseThrow(() -> new NotFoundException("Route not found"));
        
        if (!route.getIsPublished() || !route.getIsActive()) {
            throw new NotFoundException("Route not available");
        }
        
        return Response.ok(toDetailedDTO(route)).build();
    }
    
    /**
     * PUBLIC: Validate if a point is a valid pickup location for a route.
     * Useful for showing users if they can book from their location.
     */
    @GET
    @Path("/{routeId}/validate-pickup")
    @PermitAll
    @Operation(
        summary = "Validate pickup point (Public)",
        description = "Check if a location is within acceptable distance from the route. No authentication required."
    )
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
    
    /**
     * PROTECTED: Create a new route (Drivers only).
     */
    @POST
    @RolesAllowed({"DRIVER"})
    @Operation(
        summary = "Create a new route (Driver only)",
        description = "Create a new route. Requires driver authentication."
    )
    public Response createRoute(@Valid RouteService.CreateRouteRequest request) {
        Route route = routeService.createRoute(request);
        return Response.status(Response.Status.CREATED)
                .entity(toDetailedDTO(route))
                .build();
    }
    
    /**
     * PROTECTED: Get driver's own routes.
     */
    @GET
    @Path("/my-routes")
    @RolesAllowed({"DRIVER"})
    @Operation(
        summary = "Get driver's routes (Driver only)",
        description = "Get all routes owned by the authenticated driver."
    )
    public Response getMyRoutes(@QueryParam("driverId") UUID driverId) {
        if (driverId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Driver ID is required"))
                .build();
        }
        
        List<Route> routes = routeRepository.findByDriverId(driverId);
        List<DetailedRouteDTO> routeDTOs = routes.stream()
            .map(this::toDetailedDTO)
            .collect(Collectors.toList());
        
        return Response.ok(routeDTOs).build();
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Convert Route to public DTO (limited information for unauthenticated users)
     */
    private PublicRouteDTO toPublicDTO(Route route) {
        return new PublicRouteDTO(
            route.getId().toString(),
            route.getName(),
            route.getDescription(),
            route.getDistanceKm(),
            route.getVirtualStops().size(),
            route.getMaxDeviationMeters(),
            // Convert geometry to GeoJSON coordinates array
            extractCoordinates(route),
            // Get first and last stop names
            getStartStopName(route),
            getEndStopName(route)
        );
    }
    
    /**
     * Convert Route to detailed DTO (includes stops)
     */
    private DetailedRouteDTO toDetailedDTO(Route route) {
        List<VirtualStopDTO> stops = route.getVirtualStops().stream()
            .map(this::toStopDTO)
            .collect(Collectors.toList());
        
        return new DetailedRouteDTO(
            route.getId().toString(),
            route.getName(),
            route.getDescription(),
            route.getDistanceKm(),
            route.getMaxDeviationMeters(),
            extractCoordinates(route),
            stops,
            route.getIsActive(),
            route.getIsPublished()
        );
    }
    
    private VirtualStopDTO toStopDTO(VirtualStop stop) {
        return new VirtualStopDTO(
            stop.getId().toString(),
            stop.getName(),
            stop.getDescription(),
            stop.getLocation().getY(), // latitude
            stop.getLocation().getX(), // longitude
            stop.getSequenceOrder(),
            stop.getTimeOffsetMinutes()
        );
    }
    
    private double[][] extractCoordinates(Route route) {
        org.locationtech.jts.geom.Coordinate[] coords = route.getGeometry().getCoordinates();
        double[][] result = new double[coords.length][2];
        for (int i = 0; i < coords.length; i++) {
            result[i][0] = coords[i].x; // longitude
            result[i][1] = coords[i].y; // latitude
        }
        return result;
    }
    
    private String getStartStopName(Route route) {
        return route.getVirtualStops().isEmpty() ? 
            "Start" : 
            route.getVirtualStops().get(0).getName();
    }
    
    private String getEndStopName(Route route) {
        List<VirtualStop> stops = route.getVirtualStops();
        return stops.isEmpty() ? 
            "End" : 
            stops.get(stops.size() - 1).getName();
    }
    
    // ==================== DTOs ====================
    
    /**
     * Public route DTO - limited information for unauthenticated users
     */
    public record PublicRouteDTO(
        String id,
        String name,
        String description,
        Double distanceKm,
        Integer stopCount,
        Integer maxDeviationMeters,
        double[][] coordinates, // [longitude, latitude] pairs
        String startPoint,
        String endPoint
    ) {}
    
    /**
     * Detailed route DTO - includes all stops
     */
    public record DetailedRouteDTO(
        String id,
        String name,
        String description,
        Double distanceKm,
        Integer maxDeviationMeters,
        double[][] coordinates,
        List<VirtualStopDTO> stops,
        Boolean isActive,
        Boolean isPublished
    ) {}
    
    public record VirtualStopDTO(
        String id,
        String name,
        String description,
        Double latitude,
        Double longitude,
        Integer sequenceOrder,
        Integer timeOffsetMinutes
    ) {}
    
    public record ValidationResponse(boolean isValid) {}
    
    public record ErrorResponse(String message) {}
}