// ==================== DeactivateRouteRequest.java ====================
package com.commute.metrosync.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DeactivateRouteRequest(
    @NotNull UUID driverId
) {}

