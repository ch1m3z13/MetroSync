// ==================== CoordinatePair.java ====================
package com.commute.metrosync.dto;

import jakarta.validation.constraints.NotNull;

public record CoordinatePair(
    @NotNull Double latitude,
    @NotNull Double longitude
) {}

