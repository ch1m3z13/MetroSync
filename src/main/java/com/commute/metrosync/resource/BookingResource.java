package com.commute.metrosync.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import com.commute.metrosync.dto.request.CreateBookingRequest;
import com.commute.metrosync.service.BookingService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Bookings", description = "Ride booking management")
@SecurityRequirement(name = "jwt")
public class BookingResource {

    @Inject
    BookingService bookingService;

    @POST
    @RolesAllowed("RIDER")
    @Operation(summary = "Create booking", description = "Book a seat on a ride")
    public Response createBooking(@Valid CreateBookingRequest request, 
                                 @Context SecurityContext ctx) {
        Long userId = Long.valueOf(ctx.getUserPrincipal().getName());
        return Response.ok(bookingService.createBooking(userId, request)).build();
    }
}