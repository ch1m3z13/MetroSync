package com.commute.metrosync.dto;

public class StatusUpdateRequest {
    private String status;
    // Driver ID is extracted from JWT, so we don't strictly need it in the body 
    // for security reasons, but we can keep it if the frontend sends it.
    private String driverId; 

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
}