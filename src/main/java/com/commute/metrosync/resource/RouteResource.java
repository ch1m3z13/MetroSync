package com.commute.metrosync.resource;

import com.commute.metrosync.dto.ErrorResponse;
import com.commute.metrosync.entity.Route;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.entity.VirtualStop;
import com.commute.metrosync.entity.BookingStatus;
import com.commute.metrosync.repository.RouteRepository;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.repository.BookingRepository;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Route Management API
 * 
 * Endpoints:
 * - POST /routes: Create new route (DRIVER only)
 * - GET /routes: List routes (filtered by user role)
 * - GET /routes/:id: Get route details
 * - DELETE /routes/:id: Delete route (DRIVER only, owner only)
 */
@Path("/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Routes", description = "Route management for drivers")
public class RouteResource {
    
    @Inject
    RouteRepository routeRepository;
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    BookingRepository bookingRepository;
    
    @Inject
    JsonWebToken jwt;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    /**
     * POST /routes
     * Create a new route
     * 
     * Authorization: DRIVER role required
     */
    @POST
    @RolesAllowed({"DRIVER"})
    @Transactional
    @Operation(
        summary = "Create new route",
        description = "Driver creates a new route with stops and pricing"
    )
    public Response createRoute(@Valid CreateRouteRequest request) {
        try {
            UUID driverId = UUID.fromString(jwt.getSubject());
            
            Log.info("Creating route for driver: " + driverId);
            
            // Validate driver exists and has DRIVER role
            User driver = userRepository.findByIdOptional(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
            
            if (!driver.isDriver()) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("User does not have DRIVER role"))
                    .build();
            }
            
            // Validate polyline (must be valid encoded string)
            if (request.polyline() == null || request.polyline().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Polyline is required"))
                    .build();
            }
            
            // Decode polyline to LineString geometry
            LineString geometry = decodePolyline(request.polyline());
            
            // Validate stops
            if (request.stops() == null || request.stops().size() < 2) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("At least 1 PICKUP and 1 DROPOFF stop required"))
                    .build();
            }
            
            // Validate at least one pickup and one dropoff
            boolean hasPickup = request.stops().stream()
                .anyMatch(s -> "PICKUP".equals(s.type()));
            boolean hasDropoff = request.stops().stream()
                .anyMatch(s -> "DROPOFF".equals(s.type()));
            
            if (!hasPickup || !hasDropoff) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("At least 1 PICKUP and 1 DROPOFF stop required"))
                    .build();
            }
            
            // Create route
            Route route = new Route();
            route.setName(request.origin() + " → " + request.destination());
            route.setDescription("Route from " + request.origin() + " to " + request.destination());
            route.setGeometry(geometry);
            route.setDriverId(driverId);
            route.setIsActive(true);
            route.setIsPublished(true);
            
            // Calculate distance
            double distanceKm = geometry.getLength() * 111.0; // Rough conversion
            route.setDistanceKm(distanceKm);
            
            // Save route first to get ID
            routeRepository.persist(route);
            routeRepository.flush();
            
            // Create virtual stops
            for (StopDTO stopDto : request.stops()) {
                Point location = geometryFactory.createPoint(
                    new Coordinate(stopDto.longitude(), stopDto.latitude())
                );
                location.setSRID(4326);
                
                VirtualStop stop = new VirtualStop();
                stop.setName(stopDto.name());
                stop.setLocation(location);
                stop.setRoute(route);
                stop.setSequenceOrder(stopDto.sequence());
                stop.setIsActive(true);
                
                route.addVirtualStop(stop);
            }
            
            routeRepository.persist(route);
            
            Log.info("Route created successfully: " + route.getId());
            
            // Create response DTO
            RouteResponse response = toRouteResponse(
                route, 
                request.pricePerSeat(),
                request.availableSeats(),
                request.departureTime()
            );
            
            return Response.status(Response.Status.CREATED)
                .entity(response)
                .build();
            
        } catch (IllegalArgumentException e) {
            Log.error("Invalid route creation request", e);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            Log.error("Failed to create route", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to create route: " + e.getMessage()))
                .build();
        }
    }
    
    /**
     * GET /routes
     * List all routes (filtered by user role)
     * 
     * Filtering Logic:
     * - DRIVER: Returns only their routes
     * - PASSENGER: Returns all ACTIVE routes
     */
    @GET
    @RolesAllowed({"DRIVER", "PASSENGER"})
    @Operation(
        summary = "List routes",
        description = "List routes filtered by user role. Drivers see their routes, passengers see all active routes."
    )
    public Response listRoutes(
            @QueryParam("status") String status,
            @QueryParam("driverId") String driverIdStr) {
        
        try {
            UUID currentUserId = UUID.fromString(jwt.getSubject());
            User currentUser = userRepository.findByIdOptional(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            List<Route> routes;
            
            if (currentUser.isDriver()) {
                // Driver sees only their routes
                routes = routeRepository.findByDriverId(currentUserId);
                
                // Apply status filter if provided
                if (status != null && !status.isEmpty()) {
                    boolean isActive = "ACTIVE".equalsIgnoreCase(status);
                    routes = routes.stream()
                        .filter(r -> r.getIsActive() == isActive && r.getIsPublished() == isActive)
                        .toList();
                }
            } else {
                // Passenger sees all ACTIVE published routes
                if (driverIdStr != null && !driverIdStr.isEmpty()) {
                    UUID driverId = UUID.fromString(driverIdStr);
                    routes = routeRepository.findByDriverId(driverId).stream()
                        .filter(r -> r.getIsActive() && r.getIsPublished())
                        .toList();
                } else {
                    routes = routeRepository.findPublishedRoutes();
                }
            }
            
            // Convert to response DTOs
            List<RouteListItem> responseList = routes.stream()
                .map(this::toRouteListItem)
                .toList();
            
            return Response.ok(responseList).build();
            
        } catch (Exception e) {
            Log.error("Failed to list routes", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to list routes"))
                .build();
        }
    }
    
    /**
     * GET /routes/:id
     * Get details of a specific route
     */
    @GET
    @Path("/{id}")
    @RolesAllowed({"DRIVER", "PASSENGER"})
    @Operation(
        summary = "Get route details",
        description = "Get full details of a specific route including stops"
    )
    public Response getRoute(@PathParam("id") String routeIdStr) {
        try {
            UUID routeId = UUID.fromString(routeIdStr);
            
            Route route = routeRepository.findByIdOptional(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found"));
            
            // Check if route is accessible
            UUID currentUserId = UUID.fromString(jwt.getSubject());
            User currentUser = userRepository.findByIdOptional(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            // If passenger, route must be active and published
            if (!currentUser.isDriver() && (!route.getIsActive() || !route.getIsPublished())) {
                throw new NotFoundException("Route not available");
            }
            
            // Get driver info
            User driver = userRepository.findByIdOptional(route.getDriverId())
                .orElse(null);
            
            RouteDetailsResponse response = toRouteDetailsResponse(route, driver);
            
            return Response.ok(response).build();
            
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            Log.error("Failed to get route details", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to get route details"))
                .build();
        }
    }
    
    /**
     * DELETE /routes/:id
     * Delete/cancel a route
     * 
     * Authorization: DRIVER role required, must be route owner
     * Business Logic:
     * - Only route owner can delete
     * - If bookings exist with IN_PROGRESS status, prevent deletion
     * - Mark associated bookings as CANCELLED
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed({"DRIVER"})
    @Transactional
    @Operation(
        summary = "Delete route",
        description = "Delete/cancel a route. Cannot delete if trips are in progress."
    )
    public Response deleteRoute(@PathParam("id") String routeIdStr) {
        try {
            UUID routeId = UUID.fromString(routeIdStr);
            UUID currentUserId = UUID.fromString(jwt.getSubject());
            
            Route route = routeRepository.findByIdOptional(routeId)
                .orElseThrow(() -> new NotFoundException("Route not found"));
            
            // Verify ownership
            if (!route.getDriverId().equals(currentUserId)) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("You can only delete your own routes"))
                    .build();
            }
            
            // Check for in-progress bookings
            long inProgressCount = bookingRepository.count(
                "route.id = ?1 and status = ?2",
                routeId,
                BookingStatus.IN_PROGRESS
            );
            
            if (inProgressCount > 0) {
                return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("Cannot delete route with trips in progress"))
                    .build();
            }
            
            // Cancel all pending/confirmed bookings
            bookingRepository.list("route.id = ?1 and status in (?2, ?3)",
                routeId,
                BookingStatus.PENDING,
                BookingStatus.CONFIRMED
            ).forEach(booking -> {
                booking.cancel(currentUserId, "Route cancelled by driver");
            });
            
            // Soft delete route
            route.setIsActive(false);
            route.setIsPublished(false);
            routeRepository.persist(route);
            
            Log.info("Route deleted: " + routeId);
            
            return Response.ok(new MessageResponse("Route deleted successfully")).build();
            
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            Log.error("Failed to delete route", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to delete route"))
                .build();
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Decode Google Maps encoded polyline to JTS LineString
     * 
     * Note: This is a simplified decoder. For production, use a library like:
     * - com.google.maps:google-maps-services
     * - org.geolatte:geolatte-geom
     */
    private LineString decodePolyline(String encoded) {
        List<Coordinate> coordinates = new ArrayList<>();
        int index = 0;
        int lat = 0, lng = 0;
        
        while (index < encoded.length()) {
            int result = 0;
            int shift = 0;
            int b;
            
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            
            result = 0;
            shift = 0;
            
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            
            coordinates.add(new Coordinate(
                lng / 1E5,  // longitude
                lat / 1E5   // latitude
            ));
        }
        
        LineString lineString = geometryFactory.createLineString(
            coordinates.toArray(new Coordinate[0])
        );
        lineString.setSRID(4326);
        
        return lineString;
    }
    
    /**
     * Convert Route entity to response DTO
     */
    private RouteResponse toRouteResponse(
            Route route,
            BigDecimal pricePerSeat,
            Integer availableSeats,
            LocalDateTime departureTime) {
        
        List<StopResponse> stops = route.getVirtualStops().stream()
            .map(this::toStopResponse)
            .toList();
        
        return new RouteResponse(
            route.getId().toString(),
            route.getDriverId().toString(),
            route.getName().split(" → ")[0], // origin
            route.getName().split(" → ").length > 1 ? route.getName().split(" → ")[1] : "", // destination
            encodePolyline(route.getGeometry()),
            stops,
            pricePerSeat != null ? pricePerSeat : BigDecimal.ZERO,
            availableSeats != null ? availableSeats : 4,
            departureTime,
            "ACTIVE",
            route.getCreatedAt()
        );
    }
    
    /**
     * Convert to list item DTO
     */
    private RouteListItem toRouteListItem(Route route) {
        return new RouteListItem(
            route.getId().toString(),
            route.getDriverId().toString(),
            route.getName().split(" → ")[0],
            route.getName().split(" → ").length > 1 ? route.getName().split(" → ")[1] : "",
            route.getDistanceKm(),
            route.getVirtualStops().size(),
            route.getIsActive() && route.getIsPublished() ? "ACTIVE" : "INACTIVE"
        );
    }
    
    /**
     * Convert to details response DTO
     */
    private RouteDetailsResponse toRouteDetailsResponse(Route route, User driver) {
        List<StopResponse> stops = route.getVirtualStops().stream()
            .map(this::toStopResponse)
            .toList();
        
        DriverInfo driverInfo = driver != null ? new DriverInfo(
            driver.getFullName(),
            driver.getPhoneNumber(),
            driver.getRating().doubleValue()
        ) : null;
        
        return new RouteDetailsResponse(
            route.getId().toString(),
            route.getDriverId().toString(),
            driverInfo,
            route.getName().split(" → ")[0],
            route.getName().split(" → ").length > 1 ? route.getName().split(" → ")[1] : "",
            route.getDescription(),
            encodePolyline(route.getGeometry()),
            stops,
            route.getDistanceKm(),
            BigDecimal.ZERO, // Would come from pricing logic
            4, // Would come from vehicle capacity
            null, // Would come from schedule
            route.getIsActive() && route.getIsPublished() ? "ACTIVE" : "INACTIVE",
            route.getCreatedAt()
        );
    }
    
    /**
     * Convert VirtualStop to response DTO
     */
    private StopResponse toStopResponse(VirtualStop stop) {
        return new StopResponse(
            stop.getId().toString(),
            stop.getName(),
            stop.getLocation().getY(), // latitude
            stop.getLocation().getX(), // longitude
            "PICKUP", // Would need to track this in VirtualStop entity
            stop.getSequenceOrder()
        );
    }
    
    /**
     * Encode LineString as Google Maps polyline
     * Simplified encoder - use library in production
     */
    private String encodePolyline(LineString geometry) {
        // Simplified - return WKT for now
        // In production, use proper polyline encoding
        return geometry.toText();
    }
    
    // ==================== DTOs ====================
    
    /**
     * POST /routes request
     */
    public record CreateRouteRequest(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotBlank String polyline,
        @NotNull @Size(min = 2) List<StopDTO> stops,
        @NotNull @DecimalMin("0.01") BigDecimal pricePerSeat,
        @NotNull @Min(1) @Max(8) Integer availableSeats,
        LocalDateTime departureTime
    ) {}
    
    public record StopDTO(
        String id,
        @NotBlank String name,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotBlank String type,  // PICKUP or DROPOFF
        @NotNull Integer sequence
    ) {}
    
    /**
     * POST /routes response
     */
    public record RouteResponse(
        String id,
        String driverId,
        String origin,
        String destination,
        String polyline,
        List<StopResponse> stops,
        BigDecimal pricePerSeat,
        Integer availableSeats,
        LocalDateTime departureTime,
        String status,
        LocalDateTime createdAt
    ) {}
    
    public record StopResponse(
        String id,
        String name,
        Double latitude,
        Double longitude,
        String type,
        Integer sequence
    ) {}
    
    /**
     * GET /routes response item
     */
    public record RouteListItem(
        String id,
        String driverId,
        String origin,
        String destination,
        Double distanceKm,
        Integer stopCount,
        String status
    ) {}
    
    /**
     * GET /routes/:id response
     */
    public record RouteDetailsResponse(
        String id,
        String driverId,
        DriverInfo driver,
        String origin,
        String destination,
        String description,
        String polyline,
        List<StopResponse> stops,
        Double distanceKm,
        BigDecimal pricePerSeat,
        Integer availableSeats,
        LocalDateTime departureTime,
        String status,
        LocalDateTime createdAt
    ) {}
    
    public record DriverInfo(
        String fullName,
        String phoneNumber,
        Double rating
    ) {}
    
    public record MessageResponse(String message) {}
}