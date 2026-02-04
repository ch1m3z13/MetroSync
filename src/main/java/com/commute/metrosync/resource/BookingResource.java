package com.commute.metrosync.resource;

import com.commute.metrosync.dto.ErrorResponse;
import com.commute.metrosync.entity.*;
import com.commute.metrosync.repository.*;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Booking Management API
 * 
 * Endpoints:
 * - POST /bookings: Create booking (PASSENGER only)
 * - GET /bookings: List bookings (filtered by user role)
 * - GET /bookings/:id: Get booking details
 * - PATCH /bookings/:id: Update booking status
 * - POST /bookings/:id/cancel: Cancel booking
 */
@Path("/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Bookings", description = "Booking management for passengers and drivers")
public class BookingResource {
    
    @Inject
    BookingRepository bookingRepository;
    
    @Inject
    RouteRepository routeRepository;
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    JsonWebToken jwt;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    /**
     * POST /bookings
     * Create a new booking
     * 
     * Authorization: PASSENGER role required
     */
    @POST
    @RolesAllowed({"PASSENGER"})
    @Transactional
    @Operation(
        summary = "Create booking",
        description = "Passenger creates a booking for a route"
    )
    public Response createBooking(@Valid CreateBookingRequest request) {
        try {
            UUID passengerId = UUID.fromString(jwt.getSubject());
            
            Log.info("Creating booking for passenger: " + passengerId);
            
            // Validate passenger
            User passenger = userRepository.findByIdOptional(passengerId)
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found"));
            
            if (!passenger.isRider()) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("User does not have PASSENGER role"))
                    .build();
            }
            
            // Validate route exists and is active
            UUID routeId = UUID.fromString(request.routeId());
            Route route = routeRepository.findByIdOptional(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));
            
            if (!route.getIsActive() || !route.getIsPublished()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Route is not available"))
                    .build();
            }
            
            // Check for duplicate active booking
            long existingBookings = bookingRepository.count(
                "rider.id = ?1 and route.id = ?2 and status in (?3, ?4, ?5)",
                passengerId,
                routeId,
                BookingStatus.PENDING,
                BookingStatus.CONFIRMED,
                BookingStatus.IN_PROGRESS
            );
            
            if (existingBookings > 0) {
                return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("You already have an active booking for this route"))
                    .build();
            }
            
            // Create geometry points for pickup and dropoff
            Point pickupLocation = geometryFactory.createPoint(
                new Coordinate(
                    request.pickupLocation().longitude(),
                    request.pickupLocation().latitude()
                )
            );
            pickupLocation.setSRID(4326);
            
            Point dropoffLocation = geometryFactory.createPoint(
                new Coordinate(
                    request.dropoffLocation().longitude(),
                    request.dropoffLocation().latitude()
                )
            );
            dropoffLocation.setSRID(4326);
            
            // Validate pickup/dropoff are near route
            boolean pickupNearRoute = routeRepository.isPointNearRoute(
                routeId, pickupLocation, 500.0
            );
            boolean dropoffNearRoute = routeRepository.isPointNearRoute(
                routeId, dropoffLocation, 500.0
            );
            
            if (!pickupNearRoute || !dropoffNearRoute) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Pickup or dropoff location is too far from route"))
                    .build();
            }
            
            // Calculate fare (simplified - would use pricing service in production)
            BigDecimal fare = calculateFare(route, pickupLocation, dropoffLocation);
            
            // Create booking
            Booking booking = new Booking();
            booking.setRider(passenger);
            booking.setRoute(route);
            booking.setPickupLocation(pickupLocation);
            booking.setDropoffLocation(dropoffLocation);
            booking.setScheduledPickupTime(LocalDateTime.now().plusHours(1)); // Default
            booking.setFareAmount(fare);
            booking.setPassengerCount(1);
            booking.setStatus(BookingStatus.PENDING);
            
            bookingRepository.persist(booking);
            bookingRepository.flush();
            
            Log.info("Booking created successfully: " + booking.getId());
            
            // Create response
            BookingResponse response = toBookingResponse(booking);
            
            return Response.status(Response.Status.CREATED)
                .entity(response)
                .build();
            
        } catch (IllegalArgumentException e) {
            Log.error("Invalid booking creation request", e);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            Log.error("Failed to create booking", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to create booking: " + e.getMessage()))
                .build();
        }
    }
    
    /**
     * GET /bookings
     * List bookings for current user
     * 
     * Filtering Logic:
     * - PASSENGER: Returns their bookings
     * - DRIVER: Returns bookings for their routes
     */
    @GET
    @RolesAllowed({"PASSENGER", "DRIVER"})
    @Operation(
        summary = "List bookings",
        description = "List bookings filtered by user role. Passengers see their bookings, drivers see bookings for their routes."
    )
    public Response listBookings(@QueryParam("status") String status) {
        try {
            UUID currentUserId = UUID.fromString(jwt.getSubject());
            User currentUser = userRepository.findByIdOptional(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            List<Booking> bookings;
            
            if (currentUser.isRider()) {
                // Passenger sees their bookings
                bookings = bookingRepository.findByRiderId(currentUserId);
            } else if (currentUser.isDriver()) {
                // Driver sees bookings for their routes
                bookings = bookingRepository.findPendingBookingsForDriver(currentUserId);
                
                // Also get confirmed and in-progress bookings
                List<UUID> driverRoutes = routeRepository.findByDriverId(currentUserId)
                    .stream()
                    .map(Route::getId)
                    .toList();
                
                for (UUID routeId : driverRoutes) {
                    bookings.addAll(bookingRepository.findActiveBookingsByRoute(routeId));
                }
            } else {
                bookings = List.of();
            }
            
            // Apply status filter if provided
            if (status != null && !status.isEmpty()) {
                try {
                    BookingStatus filterStatus = BookingStatus.valueOf(status.toUpperCase());
                    bookings = bookings.stream()
                        .filter(b -> b.getStatus() == filterStatus)
                        .toList();
                } catch (IllegalArgumentException e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid status: " + status))
                        .build();
                }
            }
            
            // Convert to response DTOs
            List<BookingListItem> responseList = bookings.stream()
                .map(this::toBookingListItem)
                .toList();
            
            return Response.ok(responseList).build();
            
        } catch (Exception e) {
            Log.error("Failed to list bookings", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to list bookings"))
                .build();
        }
    }
    
    /**
     * GET /bookings/:id
     * Get details of a specific booking
     * 
     * Authorization:
     * - Passenger can view own bookings
     * - Driver can view bookings for their routes
     */
    @GET
    @Path("/{id}")
    @RolesAllowed({"PASSENGER", "DRIVER"})
    @Operation(
        summary = "Get booking details",
        description = "Get full details of a specific booking"
    )
    public Response getBooking(@PathParam("id") String bookingIdStr) {
        try {
            UUID bookingId = UUID.fromString(bookingIdStr);
            UUID currentUserId = UUID.fromString(jwt.getSubject());
            
            Booking booking = bookingRepository.findByIdOptional(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
            
            // Verify authorization
            User currentUser = userRepository.findByIdOptional(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            boolean isPassenger = booking.getRider().getId().equals(currentUserId);
            boolean isDriver = booking.getRoute().getDriverId().equals(currentUserId);
            
            if (!isPassenger && !isDriver) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("You are not authorized to view this booking"))
                    .build();
            }
            
            // Get passenger info if driver is viewing
            PassengerInfo passengerInfo = null;
            if (isDriver) {
                User passenger = booking.getRider();
                passengerInfo = new PassengerInfo(
                    passenger.getFullName(),
                    passenger.getPhoneNumber(),
                    passenger.getRating().doubleValue()
                );
            }
            
            BookingDetailsResponse response = toBookingDetailsResponse(booking, passengerInfo);
            
            return Response.ok(response).build();
            
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            Log.error("Failed to get booking details", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to get booking details"))
                .build();
        }
    }
    
    /**
     * PATCH /bookings/:id
     * Update booking status
     * 
     * Authorization & State Transitions:
     * - DRIVER can: PENDING → CONFIRMED → IN_PROGRESS → COMPLETED
     * - PASSENGER cannot change status after CONFIRMED
     */
    @PATCH
    @Path("/{id}")
    @RolesAllowed({"DRIVER", "PASSENGER"})
    @Transactional
    @Operation(
        summary = "Update booking status",
        description = "Update booking status. Drivers can progress through lifecycle, passengers have limited control."
    )
    public Response updateBookingStatus(
            @PathParam("id") String bookingIdStr,
            @Valid UpdateBookingStatusRequest request) {
        
        try {
            UUID bookingId = UUID.fromString(bookingIdStr);
            UUID currentUserId = UUID.fromString(jwt.getSubject());
            
            Booking booking = bookingRepository.findByIdOptional(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
            
            User currentUser = userRepository.findByIdOptional(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            boolean isDriver = booking.getRoute().getDriverId().equals(currentUserId);
            boolean isPassenger = booking.getRider().getId().equals(currentUserId);
            
            if (!isDriver && !isPassenger) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("You are not authorized to update this booking"))
                    .build();
            }
            
            // Parse new status
            BookingStatus newStatus;
            try {
                newStatus = BookingStatus.valueOf(request.status().toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid status: " + request.status()))
                    .build();
            }
            
            // Validate state transition
            BookingStatus currentStatus = booking.getStatus();
            
            if (isDriver) {
                // Driver can progress through lifecycle
                if (currentStatus == BookingStatus.PENDING && newStatus == BookingStatus.CONFIRMED) {
                    booking.confirm();
                } else if (currentStatus == BookingStatus.CONFIRMED && newStatus == BookingStatus.IN_PROGRESS) {
                    booking.startRide();
                } else if (currentStatus == BookingStatus.IN_PROGRESS && newStatus == BookingStatus.COMPLETED) {
                    booking.complete();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid status transition"))
                        .build();
                }
            } else {
                // Passenger cannot change status after confirmed
                if (currentStatus != BookingStatus.PENDING) {
                    return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErrorResponse("Cannot change booking status after confirmation"))
                        .build();
                }
            }
            
            bookingRepository.persist(booking);
            
            Log.info("Booking status updated: " + bookingId + " -> " + newStatus);
            
            BookingResponse response = toBookingResponse(booking);
            
            return Response.ok(response).build();
            
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            Log.error("Failed to update booking status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to update booking status"))
                .build();
        }
    }
    
    /**
     * POST /bookings/:id/cancel
     * Cancel a booking
     * 
     * Side Effects:
     * - Send notifications
     * - Update route availability
     */
    @POST
    @Path("/{id}/cancel")
    @RolesAllowed({"DRIVER", "PASSENGER"})
    @Transactional
    @Operation(
        summary = "Cancel booking",
        description = "Cancel a booking. Both driver and passenger can cancel."
    )
    public Response cancelBooking(@PathParam("id") String bookingIdStr) {
        try {
            UUID bookingId = UUID.fromString(bookingIdStr);
            UUID currentUserId = UUID.fromString(jwt.getSubject());
            
            Booking booking = bookingRepository.findByIdOptional(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
            
            boolean isDriver = booking.getRoute().getDriverId().equals(currentUserId);
            boolean isPassenger = booking.getRider().getId().equals(currentUserId);
            
            if (!isDriver && !isPassenger) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("You are not authorized to cancel this booking"))
                    .build();
            }
            
            if (!booking.canBeCancelled()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Booking cannot be cancelled in current state"))
                    .build();
            }
            
            String cancellationReason = isDriver 
                ? "Cancelled by driver" 
                : "Cancelled by passenger";
            
            booking.cancel(currentUserId, cancellationReason);
            bookingRepository.persist(booking);
            
            Log.info("Booking cancelled: " + bookingId);
            
            BookingResponse response = toBookingResponse(booking);
            
            return Response.ok(response).build();
            
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            Log.error("Failed to cancel booking", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to cancel booking"))
                .build();
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Calculate fare (simplified)
     */
    private BigDecimal calculateFare(Route route, Point pickup, Point dropoff) {
        // Simplified fare calculation
        // In production, use a proper pricing service
        double distance = pickup.distance(dropoff) * 111.0; // Rough km
        return BigDecimal.valueOf(Math.max(distance * 5.0, 5.0)); // Min fare $5
    }
    
    /**
     * Convert to booking response DTO
     */
    private BookingResponse toBookingResponse(Booking booking) {
        return new BookingResponse(
            booking.getId().toString(),
            booking.getRoute().getId().toString(),
            booking.getRider().getId().toString(),
            booking.getStatus().name(),
            booking.getFareAmount(),
            new LocationDTO(
                booking.getPickupLocation().getY(),
                booking.getPickupLocation().getX(),
                booking.getPickupStop() != null ? booking.getPickupStop().getId().toString() : null
            ),
            new LocationDTO(
                booking.getDropoffLocation().getY(),
                booking.getDropoffLocation().getX(),
                booking.getDropoffStop() != null ? booking.getDropoffStop().getId().toString() : null
            ),
            booking.getCreatedAt()
        );
    }
    
    /**
     * Convert to list item DTO
     */
    private BookingListItem toBookingListItem(Booking booking) {
        RouteInfo routeInfo = new RouteInfo(
            booking.getRoute().getName().split(" → ")[0],
            booking.getRoute().getName().split(" → ").length > 1 ? 
                booking.getRoute().getName().split(" → ")[1] : "",
            "..." // Would need polyline encoding
        );
        
        return new BookingListItem(
            booking.getId().toString(),
            booking.getRoute().getId().toString(),
            routeInfo,
            booking.getRider().getId().toString(),
            booking.getStatus().name(),
            booking.getFareAmount(),
            booking.getScheduledPickupTime(),
            booking.getCreatedAt()
        );
    }
    
    /**
     * Convert to details response DTO
     */
    private BookingDetailsResponse toBookingDetailsResponse(
            Booking booking,
            PassengerInfo passengerInfo) {
        
        return new BookingDetailsResponse(
            booking.getId().toString(),
            booking.getRoute().getId().toString(),
            booking.getRider().getId().toString(),
            passengerInfo,
            booking.getStatus().name(),
            booking.getFareAmount(),
            new LocationDTO(
                booking.getPickupLocation().getY(),
                booking.getPickupLocation().getX(),
                booking.getPickupStop() != null ? booking.getPickupStop().getId().toString() : null
            ),
            new LocationDTO(
                booking.getDropoffLocation().getY(),
                booking.getDropoffLocation().getX(),
                booking.getDropoffStop() != null ? booking.getDropoffStop().getId().toString() : null
            ),
            booking.getScheduledPickupTime(),
            booking.getEstimatedDropoffTime(),
            booking.getCreatedAt(),
            booking.getUpdatedAt()
        );
    }
    
    // ==================== DTOs ====================
    
    /**
     * POST /bookings request
     */
    public record CreateBookingRequest(
        @NotNull String routeId,
        @NotNull LocationRequest pickupLocation,
        @NotNull LocationRequest dropoffLocation
    ) {}
    
    public record LocationRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        String stopId
    ) {}
    
    /**
     * Booking response
     */
    public record BookingResponse(
        String id,
        String routeId,
        String passengerId,
        String status,
        BigDecimal fare,
        LocationDTO pickupLocation,
        LocationDTO dropoffLocation,
        LocalDateTime createdAt
    ) {}
    
    public record LocationDTO(
        Double latitude,
        Double longitude,
        String stopId
    ) {}
    
    /**
     * GET /bookings list item
     */
    public record BookingListItem(
        String id,
        String routeId,
        RouteInfo route,
        String passengerId,
        String status,
        BigDecimal fare,
        LocalDateTime scheduledPickupTime,
        LocalDateTime createdAt
    ) {}
    
    public record RouteInfo(
        String origin,
        String destination,
        String polyline
    ) {}
    
    /**
     * GET /bookings/:id response
     */
    public record BookingDetailsResponse(
        String id,
        String routeId,
        String passengerId,
        PassengerInfo passenger,
        String status,
        BigDecimal fare,
        LocationDTO pickupLocation,
        LocationDTO dropoffLocation,
        LocalDateTime scheduledPickupTime,
        LocalDateTime estimatedDropoffTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}
    
    public record PassengerInfo(
        String fullName,
        String phoneNumber,
        Double rating
    ) {}
    
    /**
     * PATCH /bookings/:id request
     */
    public record UpdateBookingStatusRequest(
        @NotNull String status
    ) {}
}