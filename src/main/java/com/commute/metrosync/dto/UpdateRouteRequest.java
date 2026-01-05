// ==================== UpdateRouteRequest.java ====================
package com.commute.metrosync.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record UpdateRouteRequest(
    @NotNull UUID driverId,
    String name,
    String description,
    List<CoordinatePair> coordinates
) {}

