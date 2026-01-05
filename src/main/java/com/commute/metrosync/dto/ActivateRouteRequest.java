// ==================== ActivateRouteRequest.java ====================
package com.commute.metrosync.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ActivateRouteRequest(
    @NotNull UUID driverId
) {}

