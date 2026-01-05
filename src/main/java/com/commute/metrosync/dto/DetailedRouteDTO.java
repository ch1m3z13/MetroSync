// ==================== DetailedRouteDTO.java ====================
package com.commute.metrosync.dto;

import java.util.List;

public record DetailedRouteDTO(
    String id,
    String name,
    String description,
    Double distanceKm,
    Integer maxDeviationMeters,
    double[][] coordinates,
    List<VirtualStopDTO> stops,
    Boolean isActive,
    Boolean isPublished
) {}