// ==================== VirtualStopDTO.java ====================
package com.commute.metrosync.dto;

public record VirtualStopDTO(
    String id,
    String name,
    String description,
    Double latitude,
    Double longitude,
    Integer sequenceOrder,
    Integer timeOffsetMinutes
) {}