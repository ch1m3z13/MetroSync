package com.commute.metrosync.websocket;

public class LocationMessage {
    public String type; // "DRIVER_UPDATE", "JOIN_ROUTE"
    public String userId; // Driver ID or Rider ID
    public String routeId; 
    public double latitude;
    public double longitude;
    public double heading; // 0-360 degrees (for car icon rotation)
    public double speed;   // m/s

    public LocationMessage() {}

    public LocationMessage(String type, String userId, String routeId, double lat, double lng, double heading) {
        this.type = type;
        this.userId = userId;
        this.routeId = routeId;
        this.latitude = lat;
        this.longitude = lng;
        this.heading = heading;
    }
}