// ==================== PublicRouteDTO.java ====================
package com.commute.metrosync.dto;

public record PublicRouteDTO(
    String id,
    String name,
    String description,
    Double distanceKm,
    Integer stopCount,
    Integer maxDeviationMeters,
    double[][] coordinates,
    String startPoint,
    String endPoint
) {}

