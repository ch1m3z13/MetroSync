// ==================== CreateRouteRequest.java ====================
package com.commute.metrosync.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateRouteRequest(
    @NotNull String name,
    String description,
    @NotNull List<CoordinatePair> coordinates,
    @NotNull UUID driverId
) {}

