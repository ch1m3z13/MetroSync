package com.commute.metrosync.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
import com.commute.metrosync.dto.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Routes", description = "Route management and geospatial matching")
public class RouteResource {
    
    @Inject
    RouteService routeService;
    
    @Inject
    RouteRepository routeRepository;
    
    // ==================== PUBLIC ENDPOINTS ====================
    
    /**
     * PUBLIC: Find drivers passing near a location
     */
    @GET
    @Path("/nearby")
    @PermitAll
    @Operation(
        summary = "Find nearby drivers (Public)",
        description = "Search for drivers passing within specified radius"
    )
    public Response findNearbyDrivers(
            @QueryParam("lat") @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @QueryParam("lon") @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @QueryParam("radius") @DefaultValue("500") Double radiusMeters) {
        
        List<Route> routes = routeService.findNearbyDrivers(latitude, longitude, radiusMeters);
        List<PublicRouteDTO> routeDTOs = routes.stream()
            .map(this::toPublicDTO)
            .collect(Collectors.toList());
        
        return Response.ok(routeDTOs).build();
    }
    
    /**
     * PUBLIC: Find drivers heading towards a destination
     */
    @GET
    @Path("/heading-to")
    @PermitAll
    @Operation(summary = "Find drivers heading towards destination")
    public Response findDriversHeadingTo(
            @QueryParam("originLat") @NotNull Double originLat,
            @QueryParam("originLon") @NotNull Double originLon,
            @QueryParam("destLat") @NotNull Double destLat,
            @QueryParam("destLon") @NotNull Double destLon,
            @QueryParam("radius") @DefaultValue("1000") Double radius) {
        
        List<Route> routes = routeService.findDriversHeadingTo(
            originLat, originLon, destLat, destLon, radius
        );
        
        return Response.ok(routes.stream().map(this::toPublicDTO).toList()).build();
    }
    
    /**
     * PUBLIC: Get all published routes
     */
    @GET
    @PermitAll
    @Operation(summary = "List all published routes")
    public Response listPublishedRoutes() {
        List<Route> routes = routeRepository.findPublishedRoutes();
        return Response.ok(routes.stream().map(this::toPublicDTO).toList()).build();
    }
    
    /**
     * PUBLIC: Get route details
     */
    @GET
    @Path("/{routeId}")
    @PermitAll
    @Operation(summary = "Get route details")
    public Response getRouteDetails(@PathParam("routeId") UUID routeId) {
        Route route = routeRepository.findByIdOptional(routeId)
            .orElseThrow(() -> new NotFoundException("Route not found"));
        
        if (!route.getIsPublished() || !route.getIsActive()) {
            throw new NotFoundException("Route not available");
        }
        
        return Response.ok(toDetailedDTO(route)).build();
    }
    
    /**
     * PUBLIC: Validate pickup point
     */
    @GET
    @Path("/{routeId}/validate-pickup")
    @PermitAll
    @Operation(summary = "Validate pickup point")
    public Response validatePickupPoint(
            @PathParam("routeId") UUID routeId,
            @QueryParam("lat") @NotNull Double latitude,
            @QueryParam("lon") @NotNull Double longitude) {
        
        boolean isValid = routeService.isValidPickupPoint(routeId, latitude, longitude);
        return Response.ok(new ValidationResponse(isValid)).build();
    }
    
    // ==================== PROTECTED ENDPOINTS (DRIVER ONLY) ====================
    
    /**
     * NEW: Get driver's own routes
     */
    @GET
    @Path("/my-routes")
    @RolesAllowed({"DRIVER"})
    @Operation(
        summary = "Get driver's routes",
        description = "Get all routes owned by the authenticated driver"
    )
    public Response getMyRoutes(@QueryParam("driverId") @NotNull UUID driverId) {
        List<Route> routes = routeRepository.findByDriverId(driverId);
        List<DetailedRouteDTO> routeDTOs = routes.stream()
            .map(this::toDetailedDTO)
            .collect(Collectors.toList());
        
        return Response.ok(routeDTOs).build();
    }
    
    /**
     * Create a new route
     */
    @POST
    @RolesAllowed({"DRIVER"})
    @Transactional
    @Operation(summary = "Create a new route")
    public Response createRoute(@Valid CreateRouteRequest request) {
        // Convert to simple coordinate DTOs
        List<RouteService.CoordinateDTO> coords = request.coordinates().stream()
            .map(c -> new RouteService.CoordinateDTO(c.latitude(), c.longitude()))
            .toList();
        
        Route route = routeService.createRoute(
            request.name(),
            request.description(),
            coords,
            request.driverId()
        );
        
        return Response.status(Response.Status.CREATED)
                .entity(toDetailedDTO(route))
                .build();
    }
    
    /**
     * NEW: Update existing route
     */
    @PUT
    @Path("/{routeId}")
    @RolesAllowed({"DRIVER"})
    @Transactional
    @Operation(
        summary = "Update route",
        description = "Update an existing route. Driver must own the route."
    )
    public Response updateRoute(
            @PathParam("routeId") UUID routeId,
            @Valid UpdateRouteRequest request) {
        
        try {
            // Convert coordinates if provided
            List<RouteService.CoordinateDTO> coords = null;
            if (request.coordinates() != null) {
                coords = request.coordinates().stream()
                    .map(c -> new RouteService.CoordinateDTO(c.latitude(), c.longitude()))
                    .toList();
            }
            
            Route route = routeService.updateRoute(
                routeId,
                request.driverId(),
                request.name(),
                request.description(),
                coords
            );
            
            return Response.ok(toDetailedDTO(route)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * NEW: Delete route
     */
    @DELETE
    @Path("/{routeId}")
    @RolesAllowed({"DRIVER"})
    @Transactional
    @Operation(
        summary = "Delete route",
        description = "Soft delete a route. Driver must own the route."
    )
    public Response deleteRoute(@PathParam("routeId") UUID routeId) {
        try {
            routeService.deleteRoute(routeId);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * NEW: Activate route (set as driver's active route)
     */
    @POST
    @Path("/{routeId}/activate")
    @RolesAllowed({"DRIVER"})
    @Transactional
    @Operation(
        summary = "Activate route",
        description = "Set this route as the driver's active route for accepting bookings"
    )
    public Response activateRoute(
            @PathParam("routeId") UUID routeId,
            @Valid ActivateRouteRequest request) {
        
        try {
            routeService.activateRoute(routeId, request.driverId());
            return Response.ok(new RouteStatusResponse(
                routeId.toString(),
                "ACTIVE",
                "Route activated successfully"
            )).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * NEW: Deactivate route
     */
    @POST
    @Path("/{routeId}/deactivate")
    @RolesAllowed({"DRIVER"})
    @Transactional
    @Operation(
        summary = "Deactivate route",
        description = "Stop accepting bookings on this route"
    )
    public Response deactivateRoute(
            @PathParam("routeId") UUID routeId,
            @Valid DeactivateRouteRequest request) {
        
        try {
            routeService.deactivateRoute(routeId, request.driverId());
            return Response.ok(new RouteStatusResponse(
                routeId.toString(),
                "INACTIVE",
                "Route deactivated successfully"
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private PublicRouteDTO toPublicDTO(Route route) {
        return new PublicRouteDTO(
            route.getId().toString(),
            route.getName(),
            route.getDescription(),
            route.getDistanceKm(),
            route.getVirtualStops().size(),
            route.getMaxDeviationMeters(),
            extractCoordinates(route),
            getStartStopName(route),
            getEndStopName(route)
        );
    }
    
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
            stop.getLocation().getY(),
            stop.getLocation().getX(),
            stop.getSequenceOrder(),
            stop.getTimeOffsetMinutes()
        );
    }
    
    private double[][] extractCoordinates(Route route) {
        org.locationtech.jts.geom.Coordinate[] coords = route.getGeometry().getCoordinates();
        double[][] result = new double[coords.length][2];
        for (int i = 0; i < coords.length; i++) {
            result[i][0] = coords[i].x;
            result[i][1] = coords[i].y;
        }
        return result;
    }
    
    private String getStartStopName(Route route) {
        return route.getVirtualStops().isEmpty() ? 
            "Start" : route.getVirtualStops().get(0).getName();
    }
    
    private String getEndStopName(Route route) {
        List<VirtualStop> stops = route.getVirtualStops();
        return stops.isEmpty() ? "End" : stops.get(stops.size() - 1).getName();
    }
    
}