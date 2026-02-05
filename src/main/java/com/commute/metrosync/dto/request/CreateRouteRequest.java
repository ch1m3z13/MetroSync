package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalTime;

@Data
public class CreateRouteRequest {
    
    @NotBlank
    private String fromLocation;

    @NotNull
    private Double fromLatitude;

    @NotNull
    private Double fromLongitude;

    @NotBlank
    private String toLocation;

    @NotNull
    private Double toLatitude;

    @NotNull
    private Double toLongitude;

    @NotNull
    private LocalTime departureTime;

    @Min(1)
    @Max(8)
    private Integer totalSeats;

    @Min(100)
    private Integer pricePerSeat;

    private Boolean recurring = true;

    private int[] daysOfWeek = {1, 2, 3, 4, 5}; // Mon-Fri
}
