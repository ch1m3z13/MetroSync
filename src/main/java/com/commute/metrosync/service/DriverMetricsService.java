package com.commute.metrosync.service;

import com.commute.metrosync.entity.Booking;
import com.commute.metrosync.entity.Route;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.DriverStatisticsRepository;
import com.commute.metrosync.repository.RouteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for calculating driver metrics and statistics
 * Separates calculation logic from business logic
 */
@ApplicationScoped
public class DriverMetricsService {
    
    @Inject
    DriverStatisticsRepository statisticsRepository;
    
    @Inject
    RouteRepository routeRepository;
    
    /**
     * Calculate active passenger count
     */
    public int calculateActivePassengers(List<UUID> routeIds) {
        return statisticsRepository.countActivePassengers(routeIds);
    }
    
    /**
     * Calculate pending request count
     */
    public int calculatePendingRequests(List<UUID> routeIds) {
        return statisticsRepository.countPendingRequests(routeIds);
    }
    
    /**
     * Calculate next stop information
     */
    public NextStopInfo calculateNextStop(List<UUID> routeIds) {
        Booking nextBooking = statisticsRepository.findNextUpcomingBooking(routeIds);
        
        if (nextBooking == null) {
            return new NextStopInfo(null, null);
        }
        
        String stopName = nextBooking.getPickupStop() != null 
            ? nextBooking.getPickupStop().getName()
            : "Pickup Location";
        
        int etaMinutes = (int) Duration.between(
            LocalDateTime.now(),
            nextBooking.getScheduledPickupTime()
        ).toMinutes();
        
        etaMinutes = Math.max(0, etaMinutes);
        
        return new NextStopInfo(stopName, etaMinutes);
    }
    
    /**
     * Calculate today's performance metrics
     */
    public TodaysMetrics calculateTodaysMetrics(List<UUID> routeIds) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        
        List<Booking> todaysBookings = statisticsRepository
                .findCompletedBookings(routeIds, startOfDay, endOfDay);
        
        double earnings = todaysBookings.stream()
                .map(Booking::getFareAmount)
                .map(BigDecimal::doubleValue)
                .reduce(0.0, Double::sum);
        
        int tripsCompleted = todaysBookings.size();
        
        int passengersTransported = todaysBookings.stream()
                .map(Booking::getPassengerCount)
                .reduce(0, Integer::sum);
        
        return new TodaysMetrics(earnings, tripsCompleted, passengersTransported);
    }
    
    /**
     * Calculate session metrics (online time and acceptance rate)
     */
    public SessionMetrics calculateSessionMetrics(User driver) {
        // Calculate online minutes since last login
        int onlineMinutes = 0;
        if (driver.getIsActive() && driver.getLastLogin() != null) {
            onlineMinutes = (int) Duration.between(
                driver.getLastLogin(),
                LocalDateTime.now()
            ).toMinutes();
        }
        
        // Calculate acceptance rate
        List<Route> routes = routeRepository.findByDriverId(driver.getId());
        List<UUID> routeIds = routes.stream()
                .map(Route::getId)
                .collect(Collectors.toList());
        
        double acceptanceRate = calculateAcceptanceRate(routeIds);
        
        return new SessionMetrics(onlineMinutes, acceptanceRate);
    }
    
    /**
     * Calculate driver acceptance rate for today
     */
    private double calculateAcceptanceRate(List<UUID> routeIds) {
        if (routeIds.isEmpty()) {
            return 0.0;
        }
        
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        
        long totalRequests = statisticsRepository.countTotalRequests(routeIds, startOfDay);
        long acceptedRequests = statisticsRepository.countAcceptedRequests(routeIds, startOfDay);
        
        if (totalRequests == 0) {
            return 0.0;
        }
        
        return acceptedRequests / (double) totalRequests;
    }
    
    /**
     * Get route IDs for a driver
     */
    public List<UUID> getDriverRouteIds(UUID driverId) {
        return routeRepository.findByDriverId(driverId)
                .stream()
                .map(Route::getId)
                .collect(Collectors.toList());
    }
    
    // ==================== Internal DTOs ====================
    
    public record NextStopInfo(String name, Integer etaMinutes) {}
    
    public record TodaysMetrics(
        double earnings,
        int tripsCompleted,
        int passengersTransported
    ) {}
    
    public record SessionMetrics(int onlineMinutes, double acceptanceRate) {}
}