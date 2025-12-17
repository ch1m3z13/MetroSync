package com.commute.metrosync.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import com.commute.metrosync.entity.*;
import com.commute.metrosync.repository.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Business logic for managing bookings and ride workflow.
 */
@ApplicationScoped
public class BookingService {
    
    private static final Logger LOG = Logger.getLogger(BookingService.class.getName());
    private static final BigDecimal BASE_FARE = BigDecimal.valueOf(200.0); // NGN
    private static final BigDecimal RATE_PER_KM = BigDecimal.valueOf(50.0); // NGN per km
    private static final double MAX_PICKUP_DISTANCE_METERS = 500.0;
    
    @Inject
    BookingRepository bookingRepository;
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    RouteRepository routeRepository;
    
    @Inject
    RouteService routeService;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    /**
     * Create a new booking request.
     * Validates locations, calculates fare, and creates booking.
     */
    @Transactional
    public Booking createBooking(CreateBookingRequest request) {
        LOG.info(String.format("Creating booking for rider %s on route %s", 
            request.riderId(), request.routeId()));
        
        // 1. Validate rider exists
        User rider = userRepository.findById(request.riderId())
                .orElseThrow(() -> new IllegalArgumentException("Rider not found"));
        
        if (!rider.isRider()) {
            throw new IllegalArgumentException("User is not registered as a rider");
        }
        
        // 2. Validate route exists and is active
        Route route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));
        
        if (!route.getIsActive() || !route.getIsPublished()) {
            throw new IllegalArgumentException("Route is not available");
        }
        
        // 3. Create geometry points
        Point pickupPoint = createPoint(
            request.pickupLongitude(), 
            request.pickupLatitude()
        );
        Point dropoffPoint = createPoint(
            request.dropoffLongitude(), 
            request.dropoffLatitude()
        );
        
        // 4. Validate pickup/dropoff are near route
        validateLocationNearRoute(route, pickupPoint, "Pickup");
        validateLocationNearRoute(route, dropoffPoint, "Dropoff");
        
        // 5. Calculate fare
        BigDecimal distance = calculateDistanceAlongRoute(pickupPoint, dropoffPoint);
        BigDecimal fare = calculateFare(distance, request.passengerCount());
        
        // 6. Check scheduled time is in future
        if (request.scheduledPickupTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Scheduled time must be in the future");
        }
        
        // 7. Check route capacity (if needed)
        validateCapacity(route, request.scheduledPickupTime(), request.passengerCount());
        
        // 8. Create booking
        Booking booking = new Booking(
            rider,
            route,
            pickupPoint,
            dropoffPoint,
            request.scheduledPickupTime(),
            fare
        );
        
        booking.setPassengerCount(request.passengerCount());
        booking.setDistanceKm(distance);
        booking.setSpecialInstructions(request.specialInstructions());
        
        // Calculate estimated dropoff time (rough estimate: 30 km/h average)
        int estimatedMinutes = distance.multiply(BigDecimal.valueOf(2)).intValue();
        booking.setEstimatedDropoffTime(
            request.scheduledPickupTime().plusMinutes(estimatedMinutes)
        );
        
        // 9. Save booking
        Booking savedBooking = bookingRepository.save(booking);
        
        // 10. Notify driver (TODO: implement notification service)
        LOG.info(String.format("Booking created: %s, Fare: NGN %.2f", 
            savedBooking.getId(), fare));
        
        return savedBooking;
    }
    
    /**
     * Driver confirms a pending booking.
     */
    @Transactional
    public Booking confirmBooking(UUID bookingId, UUID driverId) {
        Booking booking = getBooking(bookingId);
        
        // Verify driver owns the route
        if (!booking.getRoute().getDriverId().equals(driverId)) {
            throw new IllegalArgumentException("Driver does not own this route");
        }
        
        booking.confirm();
        Booking confirmed = bookingRepository.save(booking);
        
        LOG.info(String.format("Booking %s confirmed by driver %s", bookingId, driverId));
        
        // TODO: Notify rider
        return confirmed;
    }
    
    /**
     * Driver starts the ride (picks up passenger).
     */
    @Transactional
    public Booking startRide(UUID bookingId, UUID driverId) {
        Booking booking = getBooking(bookingId);
        
        // Verify driver owns the route
        if (!booking.getRoute().getDriverId().equals(driverId)) {
            throw new IllegalArgumentException("Driver does not own this route");
        }
        
        booking.startRide();
        Booking started = bookingRepository.save(booking);
        
        LOG.info(String.format("Ride started for booking %s", bookingId));
        
        // TODO: Notify rider
        return started;
    }
    
    /**
     * Driver completes the ride.
     */
    @Transactional
    public Booking completeRide(UUID bookingId, UUID driverId) {
        Booking booking = getBooking(bookingId);
        
        // Verify driver owns the route
        if (!booking.getRoute().getDriverId().equals(driverId)) {
            throw new IllegalArgumentException("Driver does not own this route");
        }
        
        booking.complete();
        Booking completed = bookingRepository.save(booking);
        
        LOG.info(String.format("Ride completed for booking %s", bookingId));
        
        // TODO: Request ratings from both parties
        return completed;
    }
    
    /**
     * Cancel a booking (by rider or driver).
     */
    @Transactional
    public Booking cancelBooking(UUID bookingId, UUID userId, String reason) {
        Booking booking = getBooking(bookingId);
        
        // Verify user is authorized to cancel
        boolean isRider = booking.getRider().getId().equals(userId);
        boolean isDriver = booking.getRoute().getDriverId().equals(userId);
        
        if (!isRider && !isDriver) {
            throw new IllegalArgumentException("User not authorized to cancel this booking");
        }
        
        if (!booking.canBeCancelled()) {
            throw new IllegalStateException("Booking cannot be cancelled in current state");
        }
        
        booking.cancel(userId, reason);
        Booking cancelled = bookingRepository.save(booking);
        
        LOG.info(String.format("Booking %s cancelled by user %s", bookingId, userId));
        
        // TODO: Notify other party and apply cancellation policy
        return cancelled;
    }
    
    /**
     * Submit rating after ride completion.
     */
    @Transactional
    public Booking submitRating(UUID bookingId, UUID userId, 
                                int rating, String feedback) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        
        Booking booking = getBooking(bookingId);
        
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Can only rate completed rides");
        }
        
        boolean isRider = booking.getRider().getId().equals(userId);
        boolean isDriver = booking.getRoute().getDriverId().equals(userId);
        
        if (!isRider && !isDriver) {
            throw new IllegalArgumentException("User not part of this booking");
        }
        
        if (isRider) {
            booking.setRiderRating(rating);
            booking.setRiderFeedback(feedback);
            
            // Update driver rating
            User driver = userRepository.findById(booking.getRoute().getDriverId())
                    .orElseThrow();
            driver.updateRating(rating);
            userRepository.save(driver);
        } else {
            booking.setDriverRating(rating);
            booking.setDriverFeedback(feedback);
            
            // Update rider rating
            booking.getRider().updateRating(rating);
            userRepository.save(booking.getRider());
        }
        
        return bookingRepository.save(booking);
    }
    
    /**
     * Get rider's booking history.
     */
    public List<Booking> getRiderBookings(UUID riderId) {
        return bookingRepository.findByRiderId(riderId);
    }
    
    /**
     * Get driver's pending booking requests.
     */
    public List<Booking> getDriverPendingBookings(UUID driverId) {
        return bookingRepository.findPendingBookingsForDriver(driverId);
    }
    
    /**
     * Get active bookings for a route.
     */
    public List<Booking> getRouteActiveBookings(UUID routeId) {
        return bookingRepository.findActiveBookingsByRoute(routeId);
    }
    
    // ==================== Private Helper Methods ====================
    
    private Booking getBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
    }
    
    private Point createPoint(double longitude, double latitude) {
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
    
    private void validateLocationNearRoute(Route route, Point point, String locationType) {
        boolean isNear = routeRepository.isPointNearRoute(
            route.getId(),
            point,
            MAX_PICKUP_DISTANCE_METERS
        );
        
        if (!isNear) {
            throw new IllegalArgumentException(
                String.format("%s location is too far from route (max %.0fm)", 
                    locationType, MAX_PICKUP_DISTANCE_METERS)
            );
        }
    }
    
    private BigDecimal calculateDistanceAlongRoute(Point pickup, Point dropoff) {
        // Simplified - in production, calculate actual distance along route polyline
        double distance = pickup.distance(dropoff) * 111.0; // Rough km conversion
        return BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateFare(BigDecimal distanceKm, int passengerCount) {
        // Fare = Base Fare + (Distance × Rate per KM) × Passenger Count
        BigDecimal distanceFare = distanceKm.multiply(RATE_PER_KM);
        BigDecimal totalFare = BASE_FARE.add(distanceFare);
        totalFare = totalFare.multiply(BigDecimal.valueOf(passengerCount));
        
        return totalFare.setScale(2, RoundingMode.HALF_UP);
    }
    
    private void validateCapacity(Route route, LocalDateTime scheduledTime, 
                                  int requestedSeats) {
        // TODO: Get actual vehicle capacity from route
        // For now, assume max 4 passengers per vehicle
        int maxCapacity = 4;
        
        Long bookedSeats = bookingRepository.countActiveBookingsForRouteOnDate(
            route.getId(), 
            scheduledTime
        );
        
        if (bookedSeats + requestedSeats > maxCapacity) {
            throw new IllegalStateException(
                String.format("Route is full. Available seats: %d, Requested: %d",
                    maxCapacity - bookedSeats, requestedSeats)
            );
        }
    }
    
    // ==================== DTOs ====================
    
    public record CreateBookingRequest(
        UUID riderId,
        UUID routeId,
        double pickupLatitude,
        double pickupLongitude,
        double dropoffLatitude,
        double dropoffLongitude,
        LocalDateTime scheduledPickupTime,
        Integer passengerCount,
        String specialInstructions
    ) {
        public CreateBookingRequest {
            if (passengerCount == null || passengerCount < 1) {
                passengerCount = 1;
            }
        }
    }
}