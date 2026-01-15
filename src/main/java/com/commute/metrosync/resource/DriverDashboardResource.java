package com.commute.metrosync.resource;

import com.commute.metrosync.dto.ErrorResponse;
import com.commute.metrosync.entity.DriverStatsView;
import com.commute.metrosync.entity.Route;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.DriverStatsRepository;
import com.commute.metrosync.repository.RouteRepository;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.service.DriverMetricsService;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDateTime;
import java.util.UUID;

@Path("/driver-dashboard")
@Produces("application/json")
@Consumes("application/json")
public class DriverDashboardResource {

    @Inject
    DriverStatsRepository statsRepository;

    @Inject
    UserRepository userRepository;
    
    @Inject
    RouteRepository routeRepository;
    
    @Inject
    DriverMetricsService metricsService;

    @Inject
    JsonWebToken jwt;

    /**
     * Get comprehensive driver statistics
     */
    @GET
    @Path("/stats")
    @RolesAllowed("DRIVER")
    public Response getDriverStats() {
        try {
            UUID driverId = UUID.fromString(jwt.getSubject());
            
            DriverStatsView stats = statsRepository.findByDriverId(driverId);
            
            if (stats == null) {
                return Response.status(404)
                    .entity(new ErrorResponse("Stats not found for driver"))
                    .build();
            }

            return Response.ok(stats).build();
        } catch (Exception e) {
            Log.error("Error fetching driver stats", e);
            return Response.serverError()
                .entity(new ErrorResponse("Failed to fetch stats"))
                .build();
        }
    }
    
    /**
     * ENHANCED: Refresh/recalculate driver statistics
     * Useful after completing a ride to get updated earnings
     */
    @POST
    @Path("/stats/refresh")
    @RolesAllowed("DRIVER")
    public Response refreshStats() {
        try {
            UUID driverId = UUID.fromString(jwt.getSubject());
            
            // Fetch fresh stats from view
            DriverStatsView stats = statsRepository.findByDriverId(driverId);
            
            if (stats == null) {
                return Response.status(404)
                    .entity(new ErrorResponse("Stats not found for driver"))
                    .build();
            }
            
            Log.info("Refreshed stats for driver: " + driverId);
            return Response.ok(stats).build();
            
        } catch (Exception e) {
            Log.error("Error refreshing driver stats", e);
            return Response.serverError()
                .entity(new ErrorResponse("Failed to refresh stats"))
                .build();
        }
    }

    /**
     * ENHANCED: Update driver status with optional current route
     * Supports: ONLINE, OFFLINE, BUSY
     * When going ONLINE, can specify which route to activate
     */
    @POST
    @Path("/status")
    @RolesAllowed("DRIVER")
    @Transactional
    public Response updateStatus(@Valid StatusUpdateRequest request) {
        try {
            UUID driverId = UUID.fromString(jwt.getSubject());
            
            // 1. Fetch driver
            User driver = userRepository.findById(driverId);
            if (driver == null) {
                return Response.status(404)
                    .entity(new ErrorResponse("Driver not found"))
                    .build();
            }

            // 2. Validate status
            String newStatus = request.getStatus().toUpperCase();
            if (!newStatus.equals("ONLINE") && 
                !newStatus.equals("OFFLINE") && 
                !newStatus.equals("BUSY")) {
                return Response.status(400)
                    .entity(new ErrorResponse("Invalid status. Use ONLINE, OFFLINE, or BUSY"))
                    .build();
            }

            // 3. Update driver status
            driver.setDriverStatus(newStatus);
            
            // 4. Handle route activation when going ONLINE
            if (newStatus.equals("ONLINE") && request.getCurrentRouteId() != null) {
                UUID routeId = UUID.fromString(request.getCurrentRouteId());
                
                // Verify route exists and belongs to driver
                Route route = routeRepository.findByIdOptional(routeId)
                    .orElseThrow(() -> new IllegalArgumentException("Route not found"));
                
                if (!route.getDriverId().equals(driverId)) {
                    return Response.status(400)
                        .entity(new ErrorResponse("You can only activate your own routes"))
                        .build();
                }
                
                // Activate the route
                route.setIsPublished(true);
                route.setIsActive(true);
                
                // Deactivate other routes
                routeRepository.findByDriverId(driverId).forEach(r -> {
                    if (!r.getId().equals(routeId)) {
                        r.setIsPublished(false);
                    }
                });
                
                Log.info(String.format(
                    "Driver %s went ONLINE with route %s", 
                    driverId, routeId
                ));
            }
            
            // 5. Handle going OFFLINE - deactivate all routes
            if (newStatus.equals("OFFLINE")) {
                routeRepository.findByDriverId(driverId).forEach(route -> {
                    route.setIsPublished(false);
                });
                
                Log.info(String.format("Driver %s went OFFLINE", driverId));
            }
            
            // 6. Update last login time
            if (newStatus.equals("ONLINE") && driver.getLastLogin() == null) {
                driver.setLastLogin(LocalDateTime.now());
            }
            
            // 7. Persist changes
            userRepository.persist(driver);
            
            // 8. Return response
            return Response.ok(new StatusUpdateResponse(
                driverId.toString(),
                newStatus,
                LocalDateTime.now(),
                request.getCurrentRouteId(),
                "Driver status updated successfully"
            )).build();

        } catch (IllegalArgumentException e) {
            Log.error("Invalid request", e);
            return Response.status(400)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            Log.error("Error updating status", e);
            return Response.serverError()
                .entity(new ErrorResponse("Failed to update status"))
                .build();
        }
    }
    
    // ==================== DTOs ====================
    
    public static class StatusUpdateRequest {
        @NotNull(message = "Status is required")
        private String status;
        
        private String currentRouteId; // Optional - only needed when going ONLINE
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getCurrentRouteId() { return currentRouteId; }
        public void setCurrentRouteId(String currentRouteId) { 
            this.currentRouteId = currentRouteId; 
        }
    }
    
    public record StatusUpdateResponse(
        String driverId,
        String status,
        LocalDateTime timestamp,
        String currentRouteId,
        String message
    ) {}
}