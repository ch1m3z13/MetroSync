package com.commute.metrosync.service;

import com.commute.metrosync.entity.CommuteDirection;
import com.commute.metrosync.entity.DriverCommute;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.repository.BookingRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.locationtech.jts.geom.Point;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Smart direction detection service
 * Uses multiple signals to determine if driver should go TO_WORK or TO_HOME
 * 
 * Detection Logic:
 * 1. Current time vs departure/return times (primary)
 * 2. Current location (closer to home or work?)
 * 3. Day of week (weekends = less predictable)
 * 4. Historical patterns (if available)
 */
@ApplicationScoped
public class DirectionDetectorService {
    
    @Inject
    BookingRepository bookingRepository;
    
    /**
     * Detect direction using all available signals
     * 
     * @param commute Driver's commute information
     * @param driver Driver user object
     * @return Detected direction with confidence score
     */
    public DetectionResult detectDirection(DriverCommute commute, User driver) {
        LocalTime now = LocalTime.now();
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        
        Log.info(String.format("Detecting direction for driver at %s on %s", 
            now, dayOfWeek));
        
        // Calculate confidence scores for each direction
        double toWorkScore = calculateToWorkScore(commute, driver, now, dayOfWeek);
        double toHomeScore = calculateToHomeScore(commute, driver, now, dayOfWeek);
        
        Log.info(String.format("Scores - TO_WORK: %.2f, TO_HOME: %.2f", 
            toWorkScore, toHomeScore));
        
        // Determine direction based on higher score
        if (toWorkScore > toHomeScore) {
            return new DetectionResult(
                CommuteDirection.TO_WORK,
                toWorkScore,
                buildReason(toWorkScore, now, commute.getDepartureTime())
            );
        } else {
            return new DetectionResult(
                CommuteDirection.TO_HOME,
                toHomeScore,
                buildReason(toHomeScore, now, commute.getReturnTime())
            );
        }
    }
    
    /**
     * Calculate confidence score for TO_WORK direction (0.0 to 1.0)
     */
    private double calculateToWorkScore(
            DriverCommute commute,
            User driver,
            LocalTime now,
            DayOfWeek dayOfWeek) {
        
        double score = 0.0;
        
        // 1. TIME-BASED SCORING (40% weight)
        double timeScore = calculateTimeScore(
            now, 
            commute.getDepartureTime(),
            2.0  // 2 hour window
        );
        score += timeScore * 0.4;
        
        // 2. LOCATION-BASED SCORING (30% weight)
        if (driver.getCurrentLocation() != null) {
            double locationScore = calculateLocationScore(
                driver.getCurrentLocation(),
                commute.getHomeLocation(),
                commute.getWorkLocation(),
                true  // true = closer to home is better for TO_WORK
            );
            score += locationScore * 0.3;
        }
        
        // 3. DAY OF WEEK SCORING (20% weight)
        double dayScore = calculateDayScore(dayOfWeek, true);
        score += dayScore * 0.2;
        
        // 4. GENERAL TIME WINDOW (10% weight)
        // Morning hours (5 AM - 12 PM) favor TO_WORK
        if (now.isAfter(LocalTime.of(5, 0)) && now.isBefore(LocalTime.NOON)) {
            score += 0.1;
        }
        
        return Math.min(1.0, score);  // Cap at 1.0
    }
    
    /**
     * Calculate confidence score for TO_HOME direction (0.0 to 1.0)
     */
    private double calculateToHomeScore(
            DriverCommute commute,
            User driver,
            LocalTime now,
            DayOfWeek dayOfWeek) {
        
        double score = 0.0;
        
        // 1. TIME-BASED SCORING (40% weight)
        double timeScore = calculateTimeScore(
            now,
            commute.getReturnTime(),
            2.0  // 2 hour window
        );
        score += timeScore * 0.4;
        
        // 2. LOCATION-BASED SCORING (30% weight)
        if (driver.getCurrentLocation() != null) {
            double locationScore = calculateLocationScore(
                driver.getCurrentLocation(),
                commute.getHomeLocation(),
                commute.getWorkLocation(),
                false  // false = closer to work is better for TO_HOME
            );
            score += locationScore * 0.3;
        }
        
        // 3. DAY OF WEEK SCORING (20% weight)
        double dayScore = calculateDayScore(dayOfWeek, false);
        score += dayScore * 0.2;
        
        // 4. GENERAL TIME WINDOW (10% weight)
        // Afternoon/evening hours (12 PM - 11 PM) favor TO_HOME
        if (now.isAfter(LocalTime.NOON) && now.isBefore(LocalTime.of(23, 0))) {
            score += 0.1;
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Calculate score based on proximity to scheduled time
     * Returns 0.0 to 1.0, with 1.0 being exact match
     */
    private double calculateTimeScore(
            LocalTime current,
            LocalTime scheduled,
            double windowHours) {
        
        // Calculate minutes difference
        int currentMinutes = current.getHour() * 60 + current.getMinute();
        int scheduledMinutes = scheduled.getHour() * 60 + scheduled.getMinute();
        
        int diff = Math.abs(currentMinutes - scheduledMinutes);
        
        // Handle wrap-around (e.g., 23:30 to 00:30)
        if (diff > 720) {  // More than 12 hours
            diff = 1440 - diff;  // Use shorter distance
        }
        
        double windowMinutes = windowHours * 60;
        
        // Score decreases linearly as we move away from scheduled time
        if (diff <= windowMinutes) {
            return 1.0 - (diff / windowMinutes);
        }
        
        return 0.0;
    }
    
    /**
     * Calculate score based on current location
     * Returns 0.0 to 1.0
     */
    private double calculateLocationScore(
            Point currentLocation,
            Point homeLocation,
            Point workLocation,
            boolean favorHome) {
        
        try {
            // Calculate distances (in degrees, approximate)
            double distanceToHome = currentLocation.distance(homeLocation);
            double distanceToWork = currentLocation.distance(workLocation);
            
            // Normalize distances
            double totalDistance = distanceToHome + distanceToWork;
            if (totalDistance < 0.0001) {  // Very close to both
                return 0.5;
            }
            
            // Calculate score based on which is closer
            if (favorHome) {
                // TO_WORK: Being closer to home is good
                return 1.0 - (distanceToHome / totalDistance);
            } else {
                // TO_HOME: Being closer to work is good
                return 1.0 - (distanceToWork / totalDistance);
            }
            
        } catch (Exception e) {
            Log.warn("Failed to calculate location score", e);
            return 0.5;  // Neutral score on error
        }
    }
    
    /**
     * Calculate score based on day of week
     */
    private double calculateDayScore(DayOfWeek dayOfWeek, boolean isToWork) {
        // Weekdays have more predictable patterns
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return 0.3;  // Lower confidence on weekends
        }
        
        // Friday evening = likely going home
        if (dayOfWeek == DayOfWeek.FRIDAY && !isToWork) {
            return 1.0;
        }
        
        // Monday morning = likely going to work
        if (dayOfWeek == DayOfWeek.MONDAY && isToWork) {
            return 1.0;
        }
        
        return 0.7;  // Normal weekday confidence
    }
    
    /**
     * Build human-readable reason for detection
     */
    private String buildReason(double score, LocalTime now, LocalTime scheduledTime) {
        if (score > 0.8) {
            return String.format("High confidence: Current time (%s) matches scheduled time (%s)",
                now.toString(), scheduledTime.toString());
        } else if (score > 0.6) {
            return String.format("Moderate confidence: Close to scheduled time (%s)",
                scheduledTime.toString());
        } else {
            return String.format("Low confidence: Please verify direction manually");
        }
    }
    
    /**
     * Simple time-based detection (fallback)
     */
    public CommuteDirection detectDirectionSimple(DriverCommute commute) {
        LocalTime now = LocalTime.now();
        LocalTime midpoint = calculateMidpoint(
            commute.getDepartureTime(), 
            commute.getReturnTime()
        );
        
        // Before midpoint = TO_WORK, after midpoint = TO_HOME
        return now.isBefore(midpoint) ? CommuteDirection.TO_WORK : CommuteDirection.TO_HOME;
    }
    
    /**
     * Calculate midpoint between two times
     */
    private LocalTime calculateMidpoint(LocalTime time1, LocalTime time2) {
        int minutes1 = time1.getHour() * 60 + time1.getMinute();
        int minutes2 = time2.getHour() * 60 + time2.getMinute();
        
        int midpointMinutes = (minutes1 + minutes2) / 2;
        
        return LocalTime.of(midpointMinutes / 60, midpointMinutes % 60);
    }
    
    /**
     * Detection result with confidence score
     */
    public record DetectionResult(
        CommuteDirection direction,
        double confidence,
        String reason
    ) {
        public boolean isHighConfidence() {
            return confidence > 0.8;
        }
        
        public boolean isLowConfidence() {
            return confidence < 0.5;
        }
    }
}