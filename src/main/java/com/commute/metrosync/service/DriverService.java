package com.commute.metrosync.service;

import com.commute.metrosync.dto.DriverDTO;
import com.commute.metrosync.entity.*;
import com.commute.metrosync.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Business logic for driver dashboard and operations
 * Orchestrates between repositories and metrics service
 */
@ApplicationScoped
public class DriverService {
    
    private static final Logger LOG = Logger.getLogger(DriverService.class.getName());
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    RouteRepository routeRepository;
    
    @Inject
    BookingRepository bookingRepository;
    
    @Inject
    DriverMetricsService metricsService;
    
    /**
     * Get comprehensive driver statistics for dashboard
     */
    @Transactional
    public DriverDTO.DriverStats getDriverStats(UUID driverId) {
        LOG.info("Fetching stats for driver: " + driverId);
        
        User driver = validateDriver(driverId);
        List<UUID> routeIds = metricsService.getDriverRouteIds(driverId);
        
        // Calculate all metrics
        int activePassengers = metricsService.calculateActivePassengers(routeIds);
        int pendingRequests = metricsService.calculatePendingRequests(routeIds);
        
        DriverMetricsService.NextStopInfo nextStop = metricsService.calculateNextStop(routeIds);
        DriverMetricsService.TodaysMetrics todaysMetrics = metricsService.calculateTodaysMetrics(routeIds);
        DriverMetricsService.SessionMetrics sessionMetrics = metricsService.calculateSessionMetrics(driver);
        
        return new DriverDTO.DriverStats(
            activePassengers,
            pendingRequests,
            nextStop.name(),
            nextStop.etaMinutes(),
            todaysMetrics.earnings(),
            todaysMetrics.tripsCompleted(),
            todaysMetrics.passengersTransported(),
            sessionMetrics.onlineMinutes(),
            sessionMetrics.acceptanceRate()
        );
    }
    
    /**
     * Update driver online/offline status
     */
    @Transactional
    public DriverDTO.StatusUpdateResponse updateDriverStatus(UUID driverId, String status) {
        LOG.info(String.format("Updating driver %s status to: %s", driverId, status));
        
        User driver = validateDriver(driverId);
        boolean isOnline = "ONLINE".equals(status);
        
        driver.setIsActive(isOnline);
        
        if (isOnline && driver.getLastLogin() == null) {
            driver.setLastLogin(LocalDateTime.now());
        }
        
        userRepository.persist(driver);
        
        return new DriverDTO.StatusUpdateResponse(
            true,
            status,
            LocalDateTime.now()
        );
    }
    
    /**
     * Get active route manifest with passenger pickup/drop-off points
     */
    @Transactional
    public DriverDTO.RouteManifest getActiveManifest(UUID driverId) {
        validateDriver(driverId);
        
        Route activeRoute = findActiveRoute(driverId);
        List<Booking> activeBookings = bookingRepository.findActiveBookingsByRoute(activeRoute.getId());
        
        List<DriverDTO.ManifestPoint> manifestPoints = buildManifestPoints(activeBookings);
        
        return new DriverDTO.RouteManifest(
            activeRoute.getId(),
            activeRoute.getName(),
            manifestPoints,
            getRoutePolyline(activeRoute)
        );
    }
    
    // ==================== Private Helper Methods ====================
    
    /**
     * Validate that user exists and is a driver
     */
    private User validateDriver(UUID driverId) {
        User driver = userRepository.findByIdOptional(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        
        if (!driver.isDriver()) {
            throw new IllegalArgumentException("User is not registered as a driver");
        }
        
        return driver;
    }
    
    /**
     * Find driver's active route
     */
    private Route findActiveRoute(UUID driverId) {
        List<Route> routes = routeRepository.findByDriverId(driverId);
        
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("No routes found for driver");
        }
        
        return routes.stream()
                .filter(Route::getIsActive)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No active route found"));
    }
    
    /**
     * Build manifest points from bookings
     */
    private List<DriverDTO.ManifestPoint> buildManifestPoints(List<Booking> bookings) {
        List<DriverDTO.ManifestPoint> points = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .map(this::createPickupPoint)
                .collect(Collectors.toList());
        
        List<DriverDTO.ManifestPoint> dropoffPoints = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.IN_PROGRESS)
                .map(this::createDropoffPoint)
                .collect(Collectors.toList());
        
        points.addAll(dropoffPoints);
        
        return points;
    }
    
    /**
     * Create pickup manifest point from booking
     */
    private DriverDTO.ManifestPoint createPickupPoint(Booking booking) {
        return new DriverDTO.ManifestPoint(
            booking.getId(),
            "pickup",
            booking.getPickupLocation().getY(), // latitude
            booking.getPickupLocation().getX(), // longitude
            booking.getRider().getFullName(),
            booking.getScheduledPickupTime(),
            booking.getPassengerCount()
        );
    }
    
    /**
     * Create dropoff manifest point from booking
     */
    private DriverDTO.ManifestPoint createDropoffPoint(Booking booking) {
        return new DriverDTO.ManifestPoint(
            booking.getId(),
            "dropoff",
            booking.getDropoffLocation().getY(),
            booking.getDropoffLocation().getX(),
            booking.getRider().getFullName(),
            booking.getEstimatedDropoffTime(),
            booking.getPassengerCount()
        );
    }
    
    /**
     * Get route polyline for map display
     */
    private String getRoutePolyline(Route route) {
        // TODO: Convert LineString to Google Maps polyline format
        // For now, return empty string (implement when Google Maps integration is added)
        return "";
    }
}