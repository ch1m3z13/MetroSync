package com.commute.metrosync.dto;

public class StatusUpdateResponse {
    public String driverId;
    public String status;
    public String message;

    public StatusUpdateResponse(String driverId, String status, String message) {
        this.driverId = driverId;
        this.status = status;
        this.message = message;
    }
}