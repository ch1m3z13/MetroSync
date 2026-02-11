package com.commute.metrosync.service.impl;

import com.commute.metrosync.dto.*;
import com.commute.metrosync.entity.*;
import com.commute.metrosync.service.BookingService;
import com.commute.metrosync.service.NotificationService;
import com.commute.metrosync.service.WalletService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

/**
 * Booking Service Implementation
 * Handles booking creation, confirmation, cancellation with integrated wallet payments
 */
@ApplicationScoped
@Transactional
public class BookingServiceImpl implements BookingService {
    
    private static final Logger logger = Logger.getLogger(BookingServiceImpl.class.getName());
    
    @PersistenceContext(unitName = "commuteng-pu")
    private EntityManager entityManager;
    
    @Inject
    private WalletService walletService;
    
    @Inject
    private NotificationService notificationService;
    
    @ConfigProperty(name = "pricing.base.fare", defaultValue = "200")
    private BigDecimal baseFare;
    
    @ConfigProperty(name = "pricing.per.km", defaultValue = "50")
    private BigDecimal perKmRate;
    
    @ConfigProperty(name = "pricing.minimum.fare", defaultValue = "150")
    private BigDecimal minimumFare;
    
    @ConfigProperty(name = "pricing.commission.rate", defaultValue = "0.15")
    private BigDecimal commissionRate;
    
    @ConfigProperty(name = "booking.cancellation.window.hours", defaultValue = "2")
    private int cancellationWindowHours;
    
    @Override
    public BookingResponse createBooking(UUID riderId, CreateBookingRequest request) {
        logger.info("Creating booking for rider: " + riderId);
        
        // Validate route exists and is active
        Route route = entityManager.find(Route.class, request.getRouteId());
        if (route == null || !"ACTIVE".equals(route.getStatus())) {
            throw new IllegalArgumentException("Route not found or inactive");
        }
        
        // Verify driver is fully verified
        if (!isDriverVerified(route.getDriverId())) {
            throw new IllegalArgumentException("Driver is not fully verified");
        }
        
        // Check available seats
        int bookedSeats = getBookedSeatsForRoute(request.getRouteId(), request.getScheduledDate());
        if (bookedSeats + request.getPassengerCount() > route.getAvailableSeats()) {
            throw new IllegalArgumentException("Not enough available seats");
        }
        
        // Calculate fare
        BigDecimal fareAmount = calculateFare(request.getDistanceKm(), request.getPassengerCount());
        
        // Check rider's wallet balance
        if (!walletService.hasSufficientBalance(riderId, fareAmount)) {
            throw new IllegalArgumentException("Insufficient wallet balance");
        }
        
        // Check daily limit
        if (!walletService.checkDailyLimit(riderId, fareAmount, false)) {
            throw new IllegalArgumentException("Daily transaction limit exceeded");
        }
        
        // Create booking
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setRiderId(riderId);
        booking.setRouteId(request.getRouteId());
        booking.setPickupLocation(request.getPickupLocation());
        booking.setDropoffLocation(request.getDropoffLocation());
        booking.setScheduledPickupTime(request.getScheduledPickupTime());
        booking.setPassengerCount(request.getPassengerCount());
        booking.setFareAmount(fareAmount);
        booking.setStatus("PENDING");
        booking.setPaymentStatus("PENDING");
        booking.setCreatedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        
        entityManager.persist(booking);
        entityManager.flush();
        
        // Create pending transaction (wallet hold)
        UUID transactionId = walletService.createPendingTransaction(
            riderId,
            fareAmount,
            "BOOKING_PAYMENT",
            "Booking payment for route: " + route.getName(),
            booking.getId()
        );
        
        booking.setTransactionId(transactionId);
        entityManager.merge(booking);
        
        // Send notification to driver
        notificationService.sendNotification(
            route.getDriverId(),
            "New Booking Request",
            "You have a new booking request from " + getRiderName(riderId),
            "BOOKING_CREATED",
            "NORMAL",
            Map.of("bookingId", booking.getId().toString(), "passengerCount", request.getPassengerCount()),
            "/bookings/" + booking.getId()
        );
        
        logger.info("Booking created successfully: " + booking.getId());
        
        return toBookingResponse(booking, route);
    }
    
    @Override
    public BookingResponse confirmBooking(UUID bookingId, UUID driverId) {
        logger.info("Driver " + driverId + " confirming booking: " + bookingId);
        
        Booking booking = entityManager.find(Booking.class, bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found");
        }
        
        // Verify driver owns this route
        Route route = entityManager.find(Route.class, booking.getRouteId());
        if (!route.getDriverId().equals(driverId)) {
            throw new IllegalArgumentException("Unauthorized: Driver does not own this route");
        }
        
        if (!"PENDING".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Booking is not in PENDING status");
        }
        
        // Update booking status
        booking.setStatus("CONFIRMED");
        booking.setConfirmedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        
        // Generate safety PIN
        String safetyPin = generateSafetyPin();
        booking.setSafetyPin(safetyPin);
        
        entityManager.merge(booking);
        
        // Process payment (debit rider's wallet)
        walletService.completeTransaction(booking.getTransactionId());
        booking.setPaymentStatus("PAID");
        entityManager.merge(booking);
        
        // Send notifications
        notificationService.sendNotification(
            booking.getRiderId(),
            "Booking Confirmed",
            "Your ride has been confirmed. Safety PIN: " + safetyPin,
            "BOOKING_CONFIRMED",
            "HIGH",
            Map.of("bookingId", bookingId.toString(), "safetyPin", safetyPin),
            "/bookings/" + bookingId
        );
        
        notificationService.sendNotification(
            driverId,
            "Booking Confirmed",
            "You confirmed a booking. Safety PIN: " + safetyPin,
            "BOOKING_CONFIRMED",
            "NORMAL",
            Map.of("bookingId", bookingId.toString(), "safetyPin", safetyPin),
            "/bookings/" + bookingId
        );
        
        logger.info("Booking confirmed successfully: " + bookingId);
        
        return toBookingResponse(booking, route);
    }
    
    @Override
    public BookingResponse cancelBooking(UUID bookingId, UUID userId, String reason) {
        logger.info("User " + userId + " cancelling booking: " + bookingId);
        
        Booking booking = entityManager.find(Booking.class, bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found");
        }
        
        Route route = entityManager.find(Route.class, booking.getRouteId());
        
        // Verify user is either rider or driver
        boolean isRider = booking.getRiderId().equals(userId);
        boolean isDriver = route.getDriverId().equals(userId);
        
        if (!isRider && !isDriver) {
            throw new IllegalArgumentException("Unauthorized: User is not part of this booking");
        }
        
        if ("CANCELLED".equals(booking.getStatus()) || "COMPLETED".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Booking cannot be cancelled");
        }
        
        // Check cancellation window for riders
        if (isRider && "CONFIRMED".equals(booking.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cancellationDeadline = booking.getScheduledPickupTime()
                .minusHours(cancellationWindowHours);
            
            if (now.isAfter(cancellationDeadline)) {
                throw new IllegalArgumentException(
                    "Cancellation window expired. Must cancel at least " + 
                    cancellationWindowHours + " hours before pickup"
                );
            }
        }
        
        // Update booking
        booking.setStatus("CANCELLED");
        booking.setCancelledBy(userId);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancellationReason(reason);
        booking.setUpdatedAt(LocalDateTime.now());
        
        entityManager.merge(booking);
        
        // Process refund if payment was made
        if ("PAID".equals(booking.getPaymentStatus())) {
            UUID refundTransactionId = walletService.processRefund(
                booking.getRiderId(),
                booking.getFareAmount(),
                "Booking cancellation: " + reason,
                booking.getId()
            );
            
            booking.setPaymentStatus("REFUNDED");
            entityManager.merge(booking);
            
            logger.info("Refund processed: " + refundTransactionId);
        } else if ("PENDING".equals(booking.getPaymentStatus())) {
            // Release pending transaction
            walletService.cancelTransaction(booking.getTransactionId());
        }
        
        // Send notifications
        UUID otherPartyId = isRider ? route.getDriverId() : booking.getRiderId();
        String cancelledBy = isRider ? "Rider" : "Driver";
        
        notificationService.sendNotification(
            otherPartyId,
            "Booking Cancelled",
            cancelledBy + " cancelled the booking. Reason: " + reason,
            "BOOKING_CANCELLED",
            "HIGH",
            Map.of("bookingId", bookingId.toString(), "reason", reason),
            "/bookings/" + bookingId
        );
        
        logger.info("Booking cancelled successfully: " + bookingId);
        
        return toBookingResponse(booking, route);
    }
    
    @Override
    public BookingResponse startTrip(UUID bookingId, UUID driverId, String safetyPin) {
        logger.info("Driver " + driverId + " starting trip: " + bookingId);
        
        Booking booking = entityManager.find(Booking.class, bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found");
        }
        
        Route route = entityManager.find(Route.class, booking.getRouteId());
        if (!route.getDriverId().equals(driverId)) {
            throw new IllegalArgumentException("Unauthorized: Driver does not own this booking");
        }
        
        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Booking must be CONFIRMED to start trip");
        }
        
        // Verify safety PIN
        if (!safetyPin.equals(booking.getSafetyPin())) {
            throw new IllegalArgumentException("Invalid safety PIN");
        }
        
        // Update booking
        booking.setStatus("IN_PROGRESS");
        booking.setActualPickupTime(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        
        entityManager.merge(booking);
        
        // Send notification to rider
        notificationService.sendNotification(
            booking.getRiderId(),
            "Trip Started",
            "Your trip has started. Have a safe journey!",
            "TRIP_STARTED",
            "NORMAL",
            Map.of("bookingId", bookingId.toString()),
            "/bookings/" + bookingId
        );
        
        logger.info("Trip started successfully: " + bookingId);
        
        return toBookingResponse(booking, route);
    }
    
    @Override
    public BookingResponse completeTrip(UUID bookingId, UUID driverId) {
        logger.info("Driver " + driverId + " completing trip: " + bookingId);
        
        Booking booking = entityManager.find(Booking.class, bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found");
        }
        
        Route route = entityManager.find(Route.class, booking.getRouteId());
        if (!route.getDriverId().equals(driverId)) {
            throw new IllegalArgumentException("Unauthorized: Driver does not own this booking");
        }
        
        if (!"IN_PROGRESS".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Booking must be IN_PROGRESS to complete");
        }
        
        // Update booking
        booking.setStatus("COMPLETED");
        booking.setActualDropoffTime(LocalDateTime.now());
        booking.setCompletedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        
        entityManager.merge(booking);
        
        // Process driver payout
        processDriverPayout(booking, route.getDriverId());
        
        // Send notifications
        notificationService.sendNotification(
            booking.getRiderId(),
            "Trip Completed",
            "Thank you for using CommuteNG! Please rate your experience.",
            "TRIP_COMPLETED",
            "NORMAL",
            Map.of("bookingId", bookingId.toString()),
            "/bookings/" + bookingId + "/rate"
        );
        
        notificationService.sendNotification(
            driverId,
            "Trip Completed",
            "Trip completed successfully. Earnings have been credited to your wallet.",
            "TRIP_COMPLETED",
            "NORMAL",
            Map.of("bookingId", bookingId.toString()),
            "/bookings/" + bookingId
        );
        
        logger.info("Trip completed successfully: " + bookingId);
        
        return toBookingResponse(booking, route);
    }
    
    @Override
    public BookingResponse rateBooking(UUID bookingId, UUID userId, int rating, String review) {
        logger.info("User " + userId + " rating booking: " + bookingId);
        
        Booking booking = entityManager.find(Booking.class, bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found");
        }
        
        Route route = entityManager.find(Route.class, booking.getRouteId());
        
        boolean isRider = booking.getRiderId().equals(userId);
        boolean isDriver = route.getDriverId().equals(userId);
        
        if (!isRider && !isDriver) {
            throw new IllegalArgumentException("Unauthorized: User is not part of this booking");
        }
        
        if (!"COMPLETED".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Can only rate completed bookings");
        }
        
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        
        // Update rating
        if (isRider) {
            if (booking.getRiderRating() != null) {
                throw new IllegalArgumentException("You have already rated this booking");
            }
            booking.setRiderRating(rating);
            booking.setRiderReview(review);
        } else {
            if (booking.getDriverRating() != null) {
                throw new IllegalArgumentException("You have already rated this booking");
            }
            booking.setDriverRating(rating);
            booking.setDriverReview(review);
        }
        
        booking.setUpdatedAt(LocalDateTime.now());
        entityManager.merge(booking);
        
        // Update user's average rating
        updateUserAverageRating(isRider ? route.getDriverId() : booking.getRiderId());
        
        logger.info("Booking rated successfully: " + bookingId);
        
        return toBookingResponse(booking, route);
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    private void processDriverPayout(Booking booking, UUID driverId) {
        logger.info("Processing driver payout for booking: " + booking.getId());
        
        // Calculate commission
        BigDecimal commissionAmount = booking.getFareAmount()
            .multiply(commissionRate)
            .setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal driverEarnings = booking.getFareAmount()
            .subtract(commissionAmount)
            .setScale(2, RoundingMode.HALF_UP);
        
        // Credit driver wallet
        UUID payoutTransactionId = walletService.creditWallet(
            driverId,
            driverEarnings,
            "DRIVER_PAYOUT",
            "Earnings from booking: " + booking.getId(),
            booking.getId()
        );
        
        // Record commission
        UUID commissionTransactionId = walletService.recordCommission(
            commissionAmount,
            booking.getId(),
            driverId
        );
        
        logger.info("Driver payout completed: earnings=" + driverEarnings + 
            ", commission=" + commissionAmount);
    }
    
    private BigDecimal calculateFare(Double distanceKm, int passengerCount) {
        if (distanceKm == null || distanceKm <= 0) {
            distanceKm = 5.0; // Default distance if not provided
        }
        
        BigDecimal distanceFare = perKmRate.multiply(BigDecimal.valueOf(distanceKm));
        BigDecimal totalFare = baseFare.add(distanceFare);
        
        // Apply passenger multiplier
        if (passengerCount > 1) {
            totalFare = totalFare.multiply(BigDecimal.valueOf(passengerCount));
        }
        
        // Ensure minimum fare
        if (totalFare.compareTo(minimumFare) < 0) {
            totalFare = minimumFare;
        }
        
        return totalFare.setScale(2, RoundingMode.HALF_UP);
    }
    
    private String generateSafetyPin() {
        Random random = new Random();
        return String.format("%04d", random.nextInt(10000));
    }
    
    private boolean isDriverVerified(UUID driverId) {
        String query = "SELECT is_user_fully_verified(:driverId)";
        Boolean verified = (Boolean) entityManager.createNativeQuery(query)
            .setParameter("driverId", driverId)
            .getSingleResult();
        
        return Boolean.TRUE.equals(verified);
    }
    
    private int getBookedSeatsForRoute(UUID routeId, LocalDateTime scheduledDate) {
        String query = "SELECT COALESCE(SUM(b.passenger_count), 0) " +
                      "FROM bookings b " +
                      "WHERE b.route_id = :routeId " +
                      "AND DATE(b.scheduled_pickup_time) = DATE(:scheduledDate) " +
                      "AND b.status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')";
        
        Number result = (Number) entityManager.createNativeQuery(query)
            .setParameter("routeId", routeId)
            .setParameter("scheduledDate", scheduledDate)
            .getSingleResult();
        
        return result != null ? result.intValue() : 0;
    }
    
    private String getRiderName(UUID riderId) {
        String query = "SELECT full_name FROM users WHERE id = :riderId";
        return (String) entityManager.createNativeQuery(query)
            .setParameter("riderId", riderId)
            .getSingleResult();
    }
    
    private void updateUserAverageRating(UUID userId) {
        String query = "UPDATE users SET " +
                      "average_rating = (" +
                      "  SELECT AVG(rating) FROM (" +
                      "    SELECT rider_rating as rating FROM bookings WHERE route_id IN (" +
                      "      SELECT id FROM routes WHERE driver_id = :userId" +
                      "    ) AND rider_rating IS NOT NULL" +
                      "    UNION ALL" +
                      "    SELECT driver_rating as rating FROM bookings WHERE rider_id = :userId " +
                      "    AND driver_rating IS NOT NULL" +
                      "  ) ratings" +
                      ") " +
                      "WHERE id = :userId";
        
        entityManager.createNativeQuery(query)
            .setParameter("userId", userId)
            .executeUpdate();
    }
    
    private BookingResponse toBookingResponse(Booking booking, Route route) {
        BookingResponse response = new BookingResponse();
        response.setId(booking.getId());
        response.setRiderId(booking.getRiderId());
        response.setRouteId(booking.getRouteId());
        response.setRouteName(route.getName());
        response.setPickupLocation(booking.getPickupLocation());
        response.setDropoffLocation(booking.getDropoffLocation());
        response.setScheduledPickupTime(booking.getScheduledPickupTime());
        response.setPassengerCount(booking.getPassengerCount());
        response.setFareAmount(booking.getFareAmount());
        response.setStatus(booking.getStatus());
        response.setPaymentStatus(booking.getPaymentStatus());
        response.setSafetyPin(booking.getSafetyPin());
        response.setCreatedAt(booking.getCreatedAt());
        
        return response;
    }
}