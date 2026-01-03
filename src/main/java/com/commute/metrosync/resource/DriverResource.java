package com.commute.metrosync.resource;

import com.commute.metrosync.dto.DriverDTO;
import com.commute.metrosync.service.DriverService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/**
 * Driver Dashboard and Management API
 * Provides real-time stats, status management, and operational data
 */
@Path("/api/v1/drivers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Driver Dashboard", description = "Driver home screen and operational APIs")
public class DriverResource {
    
    @Inject
    DriverService driverService;
    
    @Inject
    JsonWebToken jwt;
    
    /**
     * GET /api/v1/drivers/stats
     * Returns comprehensive driver dashboard statistics
     */
    @GET
    @Path("/stats")
    @RolesAllowed({"DRIVER"})
    @Operation(
        summary = "Get driver dashboard statistics",
        description = "Returns real-time stats including active passengers, earnings, and performance metrics"
    )
    public Response getDriverStats() {
        try {
            UUID driverId = getDriverIdFromToken();
            DriverDTO.DriverStats stats = driverService.getDriverStats(driverId);
            return Response.ok(stats).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to retrieve driver stats"))
                    .build();
        }
    }
    
    /**
     * POST /api/v1/drivers/status
     * Toggle driver online/offline status
     */
    @POST
    @Path("/status")
    @RolesAllowed({"DRIVER"})
    @Operation(
        summary = "Update driver status",
        description = "Toggle driver between ONLINE and OFFLINE status"
    )
    public Response updateStatus(@Valid StatusUpdateRequest request) {
        try {
            UUID driverId = getDriverIdFromToken();
            DriverDTO.StatusUpdateResponse response = 
                driverService.updateDriverStatus(driverId, request.status());
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to update status"))
                    .build();
        }
    }
    
    /**
     * GET /api/v1/drivers/manifest/active
     * Future endpoint for route manifest (passenger pickup/drop-off points)
     */
    @GET
    @Path("/manifest/active")
    @RolesAllowed({"DRIVER"})
    @Operation(
        summary = "Get active route manifest",
        description = "Returns passenger pickup and drop-off points for active route"
    )
    public Response getActiveManifest() {
        try {
            UUID driverId = getDriverIdFromToken();
            DriverDTO.RouteManifest manifest = driverService.getActiveManifest(driverId);
            return Response.ok(manifest).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    // Helper method to extract driver ID from JWT token
    private UUID getDriverIdFromToken() {
        String userId = jwt.getSubject();
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("Invalid token: missing user ID");
        }
        return UUID.fromString(userId);
    }
    
    // DTOs
    public record StatusUpdateRequest(
        @NotNull(message = "Status is required")
        String status // "ONLINE" or "OFFLINE"
    ) {
        public StatusUpdateRequest {
            if (status == null || (!status.equals("ONLINE") && !status.equals("OFFLINE"))) {
                throw new IllegalArgumentException("Status must be either 'ONLINE' or 'OFFLINE'");
            }
        }
    }
    
    public record ErrorResponse(String message) {}
}