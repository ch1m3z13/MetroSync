package com.commute.metrosync.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import com.commute.metrosync.entity.*;
import com.commute.metrosync.repository.*;
import com.commute.metrosync.service.BookingService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Path("/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Bookings", description = "Ride booking and management")
public class BookingResource {
    
    @Inject
    BookingService bookingService;
    
    @Inject
    BookingRepository bookingRepository;
    
    /**
     * Create a new booking (Rider only).
     */
    @POST
    @RolesAllowed({"RIDER"})
    @Operation(summary = "Create a new booking", description = "Request a ride on a specific route")
    public Response createBooking(@Valid CreateBookingDTO request) {
        try {
            BookingService.CreateBookingRequest serviceRequest = 
                new BookingService.CreateBookingRequest(
                    request.riderId,
                    request.routeId,
                    request.pickupLatitude,
                    request.pickupLongitude,
                    request.dropoffLatitude,
                    request.dropoffLongitude,
                    request.scheduledPickupTime,
                    request.passengerCount,
                    request.specialInstructions
                );
            
            Booking booking = bookingService.createBooking(serviceRequest);
            return Response.status(Response.Status.CREATED)
                    .entity(toDTO(booking))
                    .build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Get booking details.
     */
    @GET
    @Path("/{bookingId}")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Get booking details")
    public Response getBooking(@PathParam("bookingId") UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .map(booking -> Response.ok(toDTO(booking)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
    
    /**
     * Confirm a booking (Driver only).
     */
    @POST
    @Path("/{bookingId}/confirm")
    @RolesAllowed({"DRIVER"})
    @Operation(summary = "Confirm a booking", description = "Driver confirms they will provide the ride")
    public Response confirmBooking(
            @PathParam("bookingId") UUID bookingId,
            @Context SecurityContext securityContext) {
        
        try {
            UUID driverId = getUserIdFromContext(securityContext);
            Booking booking = bookingService.confirmBooking(bookingId, driverId);
            return Response.ok(toDTO(booking)).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Start a ride (Driver only).
     */
    @POST
    @Path("/{bookingId}/start")
    @RolesAllowed({"DRIVER"})
    @Operation(summary = "Start a ride", description = "Mark that passenger has been picked up")
    public Response startRide(
            @PathParam("bookingId") UUID bookingId,
            @Context SecurityContext securityContext) {
        
        try {
            UUID driverId = getUserIdFromContext(securityContext);
            Booking booking = bookingService.startRide(bookingId, driverId);
            return Response.ok(toDTO(booking)).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Complete a ride (Driver only).
     */
    @POST
    @Path("/{bookingId}/complete")
    @RolesAllowed({"DRIVER"})
    @Operation(summary = "Complete a ride", description = "Mark that passenger has been dropped off")
    public Response completeRide(
            @PathParam("bookingId") UUID bookingId,
            @Context SecurityContext securityContext) {
        
        try {
            UUID driverId = getUserIdFromContext(securityContext);
            Booking booking = bookingService.completeRide(bookingId, driverId);
            return Response.ok(toDTO(booking)).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Cancel a booking (Rider or Driver).
     */
    @POST
    @Path("/{bookingId}/cancel")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Cancel a booking")
    public Response cancelBooking(
            @PathParam("bookingId") UUID bookingId,
            @Valid CancelBookingDTO request,
            @Context SecurityContext securityContext) {
        
        try {
            UUID userId = getUserIdFromContext(securityContext);
            Booking booking = bookingService.cancelBooking(
                bookingId, 
                userId, 
                request.reason
            );
            return Response.ok(toDTO(booking)).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Submit rating for completed ride.
     */
    @POST
    @Path("/{bookingId}/rate")
    @RolesAllowed({"RIDER", "DRIVER"})
    @Operation(summary = "Rate a completed ride")
    public Response rateRide(
            @PathParam("bookingId") UUID bookingId,
            @Valid RatingDTO request,
            @Context SecurityContext securityContext) {
        
        try {
            UUID userId = getUserIdFromContext(securityContext);
            Booking booking = bookingService.submitRating(
                bookingId,
                userId,
                request.rating,
                request.feedback
            );
            return Response.ok(toDTO(booking)).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Get rider's booking history.
     */
    @GET
    @Path("/rider/{riderId}")
    @RolesAllowed({"RIDER"})
    @Operation(summary = "Get rider's booking history")
    public Response getRiderBookings(@PathParam("riderId") UUID riderId) {
        List<Booking> bookings = bookingService.getRiderBookings(riderId);
        return Response.ok(bookings.stream().map(this::toDTO).toList()).build();
    }
    
    /**
     * Get driver's pending booking requests.
     */
    @GET
    @Path("/driver/{driverId}/pending")
    @RolesAllowed({"DRIVER"})
    @Operation(summary = "Get driver's pending booking requests")
    public Response getDriverPendingBookings(@PathParam("driverId") UUID driverId) {
        List<Booking> bookings = bookingService.getDriverPendingBookings(driverId);
        return Response.ok(bookings.stream().map(this::toDTO).toList()).build();
    }
    
    /**
     * Get active bookings for a route.
     */
    @GET
    @Path("/route/{routeId}/active")
    @RolesAllowed({"DRIVER"})
    @Operation(summary = "Get active bookings for a route")
    public Response getRouteActiveBookings(@PathParam("routeId") UUID routeId) {
        List<Booking> bookings = bookingService.getRouteActiveBookings(routeId);
        return Response.ok(bookings.stream().map(this::toDTO).toList()).build();
    }
    
    // Helper methods
    private UUID getUserIdFromContext(SecurityContext context) {
        // In production, extract from JWT token
        // For now, return a dummy UUID
        return UUID.randomUUID();
    }
    
    private BookingDTO toDTO(Booking booking) {
        return new BookingDTO(
            booking.getId(),
            booking.getRider().getId(),
            booking.getRoute().getId(),
            booking.getPickupLocation().getY(),
            booking.getPickupLocation().getX(),
            booking.getDropoffLocation().getY(),
            booking.getDropoffLocation().getX(),
            booking.getStatus().name(),
            booking.getScheduledPickupTime(),
            booking.getEstimatedDropoffTime(),
            booking.getPassengerCount(),
            booking.getFareAmount(),
            booking.getDistanceKm(),
            booking.getSpecialInstructions(),
            booking.getRiderRating(),
            booking.getDriverRating()
        );
    }
    
    // DTOs
    public record CreateBookingDTO(
        @NotNull UUID riderId,
        @NotNull UUID routeId,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double pickupLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double pickupLongitude,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double dropoffLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double dropoffLongitude,
        @NotNull @Future LocalDateTime scheduledPickupTime,
        @Min(1) @Max(10) Integer passengerCount,
        @Size(max = 500) String specialInstructions
    ) {}
    
    public record CancelBookingDTO(
        @NotNull @Size(min = 5, max = 500) String reason
    ) {}
    
    public record RatingDTO(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 1000) String feedback
    ) {}
    
    public record BookingDTO(
        UUID id,
        UUID riderId,
        UUID routeId,
        Double pickupLatitude,
        Double pickupLongitude,
        Double dropoffLatitude,
        Double dropoffLongitude,
        String status,
        LocalDateTime scheduledPickupTime,
        LocalDateTime estimatedDropoffTime,
        Integer passengerCount,
        java.math.BigDecimal fareAmount,
        java.math.BigDecimal distanceKm,
        String specialInstructions,
        Integer riderRating,
        Integer driverRating
    ) {}
    
    public record ErrorResponse(String message) {}
}
