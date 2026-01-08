package com.commute.metrosync.service;

import com.commute.metrosync.dto.CommuteDTOs.*;
import com.commute.metrosync.entity.*;
import com.commute.metrosync.repository.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for managing driver commutes and auto-generating routes
 */
@ApplicationScoped
public class CommuteService {
    
    @Inject
    CommuteRepository commuteRepository;
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    RouteRepository routeRepository;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    /**
     * Save or update driver's commute information
     */
    @Transactional
    public CommuteResponse saveCommute(SaveCommuteRequest request) {
        Log.info("Saving commute for driver: " + request.driverId());
        
        // 1. Validate driver exists and is actually a driver
        UUID driverId = UUID.fromString(request.driverId());
        User driver = userRepository.findByIdOptional(driverId)
            .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        
        if (!driver.isDriver()) {
            throw new IllegalArgumentException("User is not registered as a driver");
        }
        
        // 2. Create geometry points
        Point homeLocation = createPoint(request.homeLongitude(), request.homeLatitude());
        Point workLocation = createPoint(request.workLongitude(), request.workLatitude());
        
        // 3. Parse times
        LocalTime departureTime = LocalTime.parse(request.departureTime());
        LocalTime returnTime = LocalTime.parse(request.returnTime());
        
        // 4. Validate times make sense
        if (returnTime.isBefore(departureTime)) {
            throw new IllegalArgumentException(
                "Return time must be after departure time"
            );
        }
        
        // 5. Find or create commute record
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElse(new DriverCommute(
                driver,
                request.homeAddress(),
                homeLocation,
                request.workAddress(),
                workLocation,
                departureTime,
                returnTime,
                request.capacity()
            ));
        
        // 6. Update fields if existing
        if (commute.getId() != null) {
            commute.setHomeAddress(request.homeAddress());
            commute.setHomeLocation(homeLocation);
            commute.setWorkAddress(request.workAddress());
            commute.setWorkLocation(workLocation);
            commute.setDepartureTime(departureTime);
            commute.setReturnTime(returnTime);
            commute.setCapacity(request.capacity());
        }
        
        // 7. Calculate distance
        double distanceKm = calculateDistance(homeLocation, workLocation);
        commute.setCommuteDistanceKm(distanceKm);
        
        // 8. Save
        commuteRepository.persist(commute);
        
        Log.info(String.format(
            "Commute saved: %.2f km, %s → %s",
            distanceKm, request.departureTime(), request.returnTime()
        ));
        
        return toCommuteResponse(commute);
    }
    
    /**
     * Get driver's commute information
     */
    public CommuteResponse getCommute(UUID driverId) {
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException(
                "No commute found for this driver. Please set up your commute first."
            ));
        
        return toCommuteResponse(commute);
    }
    
    /**
     * Activate commute and create route
     * This is called when driver goes online
     */
    @Transactional
    public ActivateCommuteResponse activateCommute(
            UUID driverId,
            CommuteDirection direction) {
        
        Log.info(String.format(
            "Activating commute for driver %s, direction: %s",
            driverId, direction
        ));
        
        // 1. Get driver's commute
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException(
                "No commute found. Please set up your commute first."
            ));
        
        if (!commute.getIsActive()) {
            throw new IllegalStateException("Commute is not active");
        }
        
        // 2. Deactivate any existing routes for this driver
        List<Route> existingRoutes = routeRepository.findByDriverId(driverId);
        existingRoutes.forEach(route -> {
            route.setIsPublished(false);
            route.setIsActive(false);
        });
        
        // 3. Create route based on direction
        Route route = createRouteFromCommute(commute, direction);
        
        // 4. Save route
        routeRepository.persist(route);
        
        // 5. Update driver status to ONLINE
        User driver = commute.getDriver();
        driver.setDriverStatus("ONLINE");
        userRepository.persist(driver);
        
        Log.info(String.format(
            "Route created: %s (%.2f km)",
            route.getName(), route.getDistanceKm()
        ));
        
        return new ActivateCommuteResponse(
            route.getId().toString(),
            route.getName(),
            "ACTIVE",
            direction.name(),
            "Route activated successfully"
        );
    }
    
    /**
     * Auto-detect commute direction based on current time
     */
    public CommuteDirection detectDirection(DriverCommute commute) {
        LocalTime now = LocalTime.now();
        return commute.isToWorkTime(now) ? CommuteDirection.TO_WORK : CommuteDirection.TO_HOME;
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    /**
     * Create a route from commute information
     */
    private Route createRouteFromCommute(
            DriverCommute commute,
            CommuteDirection direction) {
        
        Point start, end;
        String startName, endName;
        
        if (direction == CommuteDirection.TO_WORK) {
            start = commute.getHomeLocation();
            end = commute.getWorkLocation();
            startName = "Home";
            endName = "Work";
        } else {
            start = commute.getWorkLocation();
            end = commute.getHomeLocation();
            startName = "Work";
            endName = "Home";
        }
        
        // Create simple 2-point route (Home → Work or Work → Home)
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(start.getX(), start.getY()),
            new Coordinate(end.getX(), end.getY())
        };
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        // Create route with descriptive name
        String routeName = String.format("%s → %s (%s)",
            startName,
            endName,
            direction == CommuteDirection.TO_WORK ? "Morning" : "Evening"
        );
        
        Route route = new Route(routeName, geometry, commute.getDriver().getId());
        route.setDescription(String.format(
            "Daily commute from %s to %s",
            direction == CommuteDirection.TO_WORK ? commute.getHomeAddress() : commute.getWorkAddress(),
            direction == CommuteDirection.TO_WORK ? commute.getWorkAddress() : commute.getHomeAddress()
        ));
        route.setDistanceKm(commute.getCommuteDistanceKm());
        route.setIsActive(true);
        route.setIsPublished(true);
        route.setMaxDeviationMeters(1000); // 1km deviation allowed
        
        // Add virtual stops at start and end
        addVirtualStops(route, start, end, startName, endName);
        
        return route;
    }
    
    /**
     * Add start and end virtual stops to route
     */
    private void addVirtualStops(Route route, Point start, Point end,
                                 String startName, String endName) {
        VirtualStop startStop = new VirtualStop(startName, start, route, 0);
        startStop.setTimeOffsetMinutes(0);
        route.addVirtualStop(startStop);
        
        VirtualStop endStop = new VirtualStop(endName, end, route, 1);
        // Estimate time: distance_km / 30 km/h average speed
        int estimatedMinutes = (int) (route.getDistanceKm() * 2);
        endStop.setTimeOffsetMinutes(estimatedMinutes);
        route.addVirtualStop(endStop);
    }
    
    /**
     * Create PostGIS point
     */
    private Point createPoint(double longitude, double latitude) {
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
    
    /**
     * Calculate straight-line distance between two points (in km)
     */
    private double calculateDistance(Point p1, Point p2) {
        // Using simple Euclidean distance * 111 (rough km per degree)
        // In production, use ST_Distance with geography type
        double distance = p1.distance(p2) * 111.0;
        return Math.round(distance * 100.0) / 100.0; // Round to 2 decimals
    }
    
    /**
     * Convert entity to DTO
     */
    private CommuteResponse toCommuteResponse(DriverCommute commute) {
        return new CommuteResponse(
            commute.getDriver().getId().toString(),
            commute.getHomeAddress(),
            commute.getHomeLocation().getY(), // latitude
            commute.getHomeLocation().getX(), // longitude
            commute.getWorkAddress(),
            commute.getWorkLocation().getY(),
            commute.getWorkLocation().getX(),
            commute.getDepartureTime().toString(),
            commute.getReturnTime().toString(),
            commute.getCapacity(),
            commute.getCommuteDistanceKm(),
            commute.getIsActive()
        );
    }
}