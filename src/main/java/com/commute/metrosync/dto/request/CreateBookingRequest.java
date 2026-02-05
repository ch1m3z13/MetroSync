package com.commute.metrosync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateBookingRequest {

    @NotNull(message = "Ride ID is required")
    private Long rideId;

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotNull
    private Double pickupLatitude;

    @NotNull
    private Double pickupLongitude;

    @NotBlank(message = "Dropoff location is required")
    private String dropoffLocation;

    @NotNull
    private Double dropoffLatitude;

    @NotNull
    private Double dropoffLongitude;

    private Integer seatsRequested = 1;
}
