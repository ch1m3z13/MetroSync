package com.commute.metrosync.resource;

import com.commute.metrosync.dto.CommuteDTOs.*;
import com.commute.metrosync.dto.ErrorResponse;
import com.commute.metrosync.entity.CommuteDirection;
import com.commute.metrosync.service.EnhancedCommuteService;
import com.commute.metrosync.service.EnhancedCommuteService.CapacityUpdateResponse;
import com.commute.metrosync.service.EnhancedCommuteService.RouteVariationDTO;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * ENHANCED Commute Resource with:
 * ✅ Google Directions API integration
 * ✅ Multiple route variations
 * ✅ Dynamic capacity updates
 * ✅ Smart direction auto-detection
 */
@Path("/drivers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Driver Commute (Enhanced)", description = "Advanced commute management with real routes and auto-detection")
public class EnhancedCommuteResource {
    
    @Inject
    EnhancedCommuteService commuteService;
    
    // ==================== SAVE COMMUTE (Enhanced) ====================
    
    /**
     * POST /api/v1/drivers/commute
     * Save commute with automatic route generation via Google Directions API
     */
    @POST
    @Path("/commute")
    @RolesAllowed("DRIVER")
    @Operation(
        summary = "Save commute with route generation",
        description = "Save home/work addresses and automatically generate route variations using Google Directions API"
    )
    public Response saveCommute(@Valid SaveCommuteRequest request) {
        try {
            Log.info("Received enhanced save commute request for driver: " + request.driverId());
            
            CommuteResponse response = commuteService.saveCommute(request);
            
            return Response.ok(new MessageResponse(
                "Commute saved successfully with route variations"
            )).build();
            
        } catch (IllegalArgumentException e) {
            Log.warn("Invalid commute request: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            Log.error("Error saving commute", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to save commute: " + e.getMessage()))
                    .build();
        }
    }
    
    // ==================== ACTIVATE COMMUTE (Smart Auto-Detection) ====================
    
    /**
     * POST /api/v1/drivers/activate-commute-auto
     * Activate commute with SMART direction detection
     * NEW: Uses time, location, day of week, and historical patterns
     */
    @POST
    @Path("/activate-commute-auto")
    @RolesAllowed("DRIVER")
    @Operation(
        summary = "Activate commute (auto-detect direction)",
        description = "Smart activation using time, location, and historical patterns to determine TO_WORK or TO_HOME"
    )
    public Response activateCommuteAuto(@QueryParam("driverId") String driverIdStr) {
        try {
            UUID driverId = UUID.fromString(driverIdStr);
            
            Log.info("Auto-activating commute for driver: " + driverId);
            
            ActivateCommuteResponse response = commuteService.activateCommuteAuto(driverId);
            
            return Response.ok(response).build();
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.warn("Failed to activate commute: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            Log.error("Error activating commute", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to activate commute"))
                    .build();
        }
    }
    
    /**
     * POST /api/v1/drivers/activate-commute
     * Activate commute with manual direction (original endpoint)
     */
    @POST
    @Path("/activate-commute")
    @RolesAllowed("DRIVER")
    @Operation(
        summary = "Activate commute (manual direction)",
        description = "Activate with explicitly specified direction"
    )
    public Response activateCommute(@Valid ActivateCommuteRequest request) {
        try {
            UUID driverId = UUID.fromString(request.driverId());
            CommuteDirection direction = CommuteDirection.valueOf(request.direction());
            
            ActivateCommuteResponse response = commuteService.activateCommute(
                driverId,
                direction
            );
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            Log.error("Error activating commute", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    // ==================== CAPACITY MANAGEMENT (NEW) ====================
    
    /**
     * PATCH /api/v1/drivers/{driverId}/capacity
     * Update driver's current capacity dynamically
     * NEW: Allows drivers to adjust capacity on-the-fly
     */
    @PATCH
    @Path("/{driverId}/capacity")
    @RolesAllowed("DRIVER")
    @Operation(
        summary = "Update capacity",
        description = "Dynamically update driver's passenger capacity (e.g., when carrying cargo)"
    )
    public Response updateCapacity(
            @PathParam("driverId") String driverIdStr,
            @QueryParam("capacity") @Min(1) @Max(20) int newCapacity) {
        try {
            UUID driverId = UUID.fromString(driverIdStr);
            
            Log.info(String.format("Updating capacity for driver %s to %d", 
                driverId, newCapacity));
            
            CapacityUpdateResponse response = commuteService.updateCapacity(
                driverId,
                newCapacity
            );
            
            return Response.ok(response).build();
            
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            Log.error("Error updating capacity", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to update capacity"))
                    .build();
        }
    }
    
    // ==================== ROUTE VARIATIONS (NEW) ====================
    
    /**
     * GET /api/v1/drivers/{driverId}/route-variations
     * Get all route variations for a driver
     * NEW: Shows all available routes (fast, scenic, traffic-free, etc.)
     */
    @GET
    @Path("/{driverId}/route-variations")
    @RolesAllowed("DRIVER")
    @Operation(
        summary = "Get route variations",
        description = "List all available route alternatives for this driver's commute"
    )
    public Response getRouteVariations(@PathParam("driverId") String driverIdStr) {
        try {
            UUID driverId = UUID.fromString(driverIdStr);
            
            List<RouteVariationDTO> variations = commuteService.getRouteVariations(driverId);
            
            return Response.ok(new RouteVariationsResponse(
                variations.size(),
                variations
            )).build();
            
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            Log.error("Error fetching route variations", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to fetch route variations"))
                    .build();
        }
    }
    
    /**
     * POST /api/v1/drivers/{driverId}/preferred-route
     * Select a route variation as preferred
     * NEW: Allows drivers to choose their favorite route
     */
    @POST
    @Path("/{driverId}/preferred-route")
    @RolesAllowed("DRIVER")
    @Operation(
        summary = "Select preferred route",
        description = "Set a specific route variation as the preferred default"
    )
    public Response selectPreferredRoute(
            @PathParam("driverId") String driverIdStr,
            @QueryParam("variationId") String variationIdStr) {
        try {
            UUID driverId = UUID.fromString(driverIdStr);
            UUID variationId = UUID.fromString(variationIdStr);
            
            commuteService.selectPreferredRoute(driverId, variationId);
            
            return Response.ok(new MessageResponse(
                "Preferred route updated successfully"
            )).build();
            
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            Log.error("Error selecting preferred route", e);
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to update preferred route"))
                    .build();
        }
    }
    
    // ==================== DTOs ====================
    
    private record RouteVariationsResponse(
        int count,
        List<RouteVariationDTO> variations
    ) {}
}