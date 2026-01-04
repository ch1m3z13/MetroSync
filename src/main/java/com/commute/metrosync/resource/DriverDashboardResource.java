package com.commute.metrosync.resource;

import com.commute.metrosync.dto.ErrorResponse;
import com.commute.metrosync.dto.StatusUpdateRequest;
import com.commute.metrosync.dto.StatusUpdateResponse;
import com.commute.metrosync.entity.DriverStatsView;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.DriverStatsRepository;
import com.commute.metrosync.repository.UserRepository;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/drivers")
@Produces("application/json")
@Consumes("application/json")
public class DriverDashboardResource {

    @Inject
    DriverStatsRepository statsRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/stats")
    @RolesAllowed("DRIVER")
    public Response getDriverStats() {
        try {
            UUID driverId = UUID.fromString(jwt.getSubject());
            
            DriverStatsView stats = statsRepository.findByDriverId(driverId);
            
            if (stats == null) {
                // If stats view returns null, it might be a new driver with no stats yet.
                // We should probably return an empty/default object or 404.
                return Response.status(404).entity(new ErrorResponse("Stats not found for driver")).build();
            }

            return Response.ok(stats).build();
        } catch (Exception e) {
            Log.error("Error fetching driver stats", e);
            return Response.serverError().entity(new ErrorResponse("Failed to fetch stats")).build();
        }
    }

    @POST
    @Path("/status")
    @RolesAllowed("DRIVER")
    @Transactional
    public Response updateStatus(StatusUpdateRequest request) {
        try {
            UUID driverId = UUID.fromString(jwt.getSubject());
            
            // 1. Fetch User (Driver)
            User driver = userRepository.findById(driverId);
            if (driver == null) {
                return Response.status(404).entity(new ErrorResponse("Driver not found")).build();
            }

            // 2. Validate Status
            String newStatus = request.getStatus().toUpperCase();
            if (!newStatus.equals("ONLINE") && !newStatus.equals("OFFLINE") && !newStatus.equals("BUSY")) {
                 return Response.status(400).entity(new ErrorResponse("Invalid status. Use ONLINE, OFFLINE, or BUSY")).build();
            }

            // 3. Update Status (Requires adding 'driverStatus' field to User entity, see note below)
            // Ideally, update the User entity to include: 
            // @Column(name = "driver_status") public String driverStatus;
            
            // For now, we use a native query if the entity isn't updated yet, 
            // OR ideally you update User.java to have this field.
            userRepository.update("driverStatus = ?1 where id = ?2", newStatus, driverId);
            
            // 4. Return Response
            return Response.ok(new StatusUpdateResponse(
                driverId.toString(),
                newStatus,
                "Driver status updated to " + newStatus
            )).build();

        } catch (Exception e) {
            Log.error("Error updating status", e);
            return Response.serverError().entity(new ErrorResponse("Failed to update status")).build();
        }
    }
}