package com.commute.metrosync.dto;

import jakarta.validation.constraints.*;

/**
 * DTOs for Driver Commute operations
 */
public class CommuteDTOs {
    
    /**
     * Request to save/update driver's commute information
     */
    public record SaveCommuteRequest(
        @NotNull(message = "Driver ID is required")
        String driverId,
        
        @NotBlank(message = "Home address is required")
        @Size(min = 5, max = 500, message = "Home address must be between 5-500 characters")
        String homeAddress,
        
        @NotNull(message = "Home latitude is required")
        @DecimalMin(value = "-90", message = "Invalid latitude")
        @DecimalMax(value = "90", message = "Invalid latitude")
        Double homeLatitude,
        
        @NotNull(message = "Home longitude is required")
        @DecimalMin(value = "-180", message = "Invalid longitude")
        @DecimalMax(value = "180", message = "Invalid longitude")
        Double homeLongitude,
        
        @NotBlank(message = "Work address is required")
        @Size(min = 5, max = 500, message = "Work address must be between 5-500 characters")
        String workAddress,
        
        @NotNull(message = "Work latitude is required")
        @DecimalMin(value = "-90", message = "Invalid latitude")
        @DecimalMax(value = "90", message = "Invalid latitude")
        Double workLatitude,
        
        @NotNull(message = "Work longitude is required")
        @DecimalMin(value = "-180", message = "Invalid longitude")
        @DecimalMax(value = "180", message = "Invalid longitude")
        Double workLongitude,
        
        @NotBlank(message = "Departure time is required")
        @Pattern(regexp = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Invalid time format. Use HH:mm (e.g., 08:00)")
        String departureTime,
        
        @NotBlank(message = "Return time is required")
        @Pattern(regexp = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Invalid time format. Use HH:mm (e.g., 17:00)")
        String returnTime,
        
        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 20, message = "Capacity cannot exceed 20")
        Integer capacity
    ) {}
    
    /**
     * Response for saved commute
     */
    public record CommuteResponse(
        String driverId,
        String homeAddress,
        Double homeLatitude,
        Double homeLongitude,
        String workAddress,
        Double workLatitude,
        Double workLongitude,
        String departureTime,
        String returnTime,
        Integer capacity,
        Double commuteDistanceKm,
        Boolean isActive
    ) {}
    
    /**
     * Request to activate commute and create route
     */
    public record ActivateCommuteRequest(
        @NotNull(message = "Driver ID is required")
        String driverId,
        
        @NotNull(message = "Direction is required")
        @Pattern(regexp = "TO_WORK|TO_HOME", message = "Direction must be either TO_WORK or TO_HOME")
        String direction  // "TO_WORK" or "TO_HOME"
    ) {}
    
    /**
     * Response after activating commute
     */
    public record ActivateCommuteResponse(
        String routeId,
        String routeName,
        String status,
        String direction,
        String message
    ) {}
    
    /**
     * Simple success response
     */
    public record MessageResponse(
        String message
    ) {}
}