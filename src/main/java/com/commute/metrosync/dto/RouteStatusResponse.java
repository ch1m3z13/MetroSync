// ==================== RouteStatusResponse.java ====================
package com.commute.metrosync.dto;

public record RouteStatusResponse(
    String routeId,
    String status,
    String message
) {}

