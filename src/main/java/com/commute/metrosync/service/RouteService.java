package com.commute.metrosync.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import com.commute.metrosync.entity.Route;
import com.commute.metrosync.entity.User;
import com.commute.metrosync.entity.DriverCommute;
import com.commute.metrosync.repository.RouteRepository;
import com.commute.metrosync.repository.UserRepository;
import com.commute.metrosync.repository.BookingRepository;
import com.commute.metrosync.repository.CommuteRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.LineString;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class RouteService {
    
    private static final Logger LOG = Logger.getLogger(RouteService.class.getName());
    private static final double DEFAULT_SEARCH_RADIUS_METERS = 500.0;
    
    @Inject
    RouteRepository routeRepository;
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    BookingRepository bookingRepository;
    
    @Inject
    CommuteRepository commuteRepository;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    // ==================== SEARCH OPERATIONS ====================
    
    public List<Route> findNearbyDrivers(
            double latitude, 
            double longitude,
            Double radiusMeters) {
        
        Point userLocation = createPoint(longitude, latitude);
        double searchRadius = radiusMeters != null ? radiusMeters : DEFAULT_SEARCH_RADIUS_METERS;
        
        LOG.info(String.format(
            "Searching for drivers within %.0fm of (%.6f, %.6f)",
            searchRadius, latitude, longitude
        ));
        
        List<Route> routes = routeRepository.findRoutesWithinDistance(
            userLocation, 
            searchRadius
        );
        
        LOG.info(String.format("Found %d matching routes", routes.size()));
        return routes;
    }
    
    public List<Route> findDriversHeadingTo(
            double originLat,
            double originLon,
            double destLat,
            double destLon,
            double radiusMeters) {
        
        Point origin = createPoint(originLon, originLat);
        Point destination = createPoint(destLon, destLat);
        
        return routeRepository.findRoutesHeadingTowards(
            origin,
            destination,
            45.0,
            radiusMeters
        );
    }
    
    public boolean isValidPickupPoint(UUID routeId, double latitude, double longitude) {
        Point pickupPoint = createPoint(longitude, latitude);
        return routeRepository.isPointNearRoute(
            routeId, 
            pickupPoint, 
            DEFAULT_SEARCH_RADIUS_METERS
        );
    }
    
    // ==================== ROUTE CRUD OPERATIONS ====================
    
    /**
     * Create a new route with commute data - accepts name, description, coordinates, 
     * driver ID, and commute information
     */
    @Transactional
    public Route createRoute(
            String name,
            String description,
            List<CoordinateDTO> coordinates,
            UUID driverId,
            String homeAddress,
            String workAddress,
            LocalTime departureTime,
            LocalTime returnTime,
            Integer capacity) {
        
        // Validate driver exists
        User driver = userRepository.findByIdOptional(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        
        if (!driver.isDriver()) {
            throw new IllegalArgumentException("User is not registered as a driver");
        }
        
        // Validate coordinates
        if (coordinates.size() < 2) {
            throw new IllegalArgumentException("Route must have at least 2 coordinates");
        }
        
        // Build LineString from coordinates
        Coordinate[] coords = coordinates.stream()
            .map(c -> new Coordinate(c.longitude(), c.latitude()))
            .toArray(Coordinate[]::new);
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        Route route = new Route(name, geometry, driverId);
        route.setDescription(description);
        route.setDistanceKm(calculateDistance(route));
        route.setIsPublished(false);
        
        routeRepository.persist(route);
        
        LOG.info(String.format("Created route: %s for driver: %s", 
            route.getId(), driverId));
        
        // ==================== CREATE DRIVER COMMUTE ====================
        
        // Extract home and work locations from route coordinates
        CoordinateDTO firstCoord = coordinates.get(0);
        CoordinateDTO lastCoord = coordinates.get(coordinates.size() - 1);
        
        Point homeLocation = createPoint(firstCoord.longitude(), firstCoord.latitude());
        Point workLocation = createPoint(lastCoord.longitude(), lastCoord.latitude());
        
        // Check if DriverCommute already exists for this driver
        DriverCommute existingCommute = commuteRepository.findByDriverId(driverId).orElse(null);
        
        if (existingCommute == null) {
            // Create new DriverCommute
            DriverCommute commute = new DriverCommute(
                driver,
                homeAddress,
                homeLocation,
                workAddress,
                workLocation,
                departureTime,
                returnTime,
                capacity
            );
            
            // Calculate commute distance from route
            commute.setCommuteDistanceKm(route.getDistanceKm());
            commute.setIsActive(true);
            
            commuteRepository.persist(commute);
            
            LOG.info(String.format("Created DriverCommute for driver: %s", driverId));
        } else {
            // Update existing DriverCommute with latest information
            existingCommute.setHomeAddress(homeAddress);
            existingCommute.setHomeLocation(homeLocation);
            existingCommute.setWorkAddress(workAddress);
            existingCommute.setWorkLocation(workLocation);
            existingCommute.setDepartureTime(departureTime);
            existingCommute.setReturnTime(returnTime);
            existingCommute.setCapacity(capacity);
            existingCommute.setCommuteDistanceKm(route.getDistanceKm());
            existingCommute.setIsActive(true);
            
            commuteRepository.persist(existingCommute);
            
            LOG.info(String.format("Updated existing DriverCommute for driver: %s", driverId));
        }
        
        // Flush to ensure DriverCommute is persisted before returning
        commuteRepository.flush();
        
        return route;
    }
    
    /**
     * Overloaded createRoute for backwards compatibility - without commute data
     * This version won't create a DriverCommute record
     */
    @Transactional
    public Route createRoute(
            String name,
            String description,
            List<CoordinateDTO> coordinates,
            UUID driverId) {
        
        // Validate driver exists
        User driver = userRepository.findByIdOptional(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        
        if (!driver.isDriver()) {
            throw new IllegalArgumentException("User is not registered as a driver");
        }
        
        // Validate coordinates
        if (coordinates.size() < 2) {
            throw new IllegalArgumentException("Route must have at least 2 coordinates");
        }
        
        // Build LineString from coordinates
        Coordinate[] coords = coordinates.stream()
            .map(c -> new Coordinate(c.longitude(), c.latitude()))
            .toArray(Coordinate[]::new);
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        Route route = new Route(name, geometry, driverId);
        route.setDescription(description);
        route.setDistanceKm(calculateDistance(route));
        route.setIsPublished(false);
        
        routeRepository.persist(route);
        
        LOG.info(String.format("Created route: %s for driver: %s (without commute data)", 
            route.getId(), driverId));
        
        return route;
    }
    
    /**
     * Update an existing route
     */
    @Transactional
    public Route updateRoute(
            UUID routeId,
            UUID driverId,
            String name,
            String description,
            List<CoordinateDTO> coordinates) {
        
        Route route = routeRepository.findByIdOptional(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));
        
        if (!route.getDriverId().equals(driverId)) {
            throw new IllegalArgumentException("You can only update your own routes");
        }
        
        Long activeBookings = bookingRepository.count(
            "route.id = ?1 and status in ('PENDING', 'CONFIRMED', 'IN_PROGRESS')", 
            routeId
        );
        
        if (activeBookings > 0) {
            throw new IllegalStateException(
                "Cannot update route with active bookings"
            );
        }
        
        if (name != null) {
            route.setName(name);
        }
        
        if (description != null) {
            route.setDescription(description);
        }
        
        if (coordinates != null && !coordinates.isEmpty()) {
            if (coordinates.size() < 2) {
                throw new IllegalArgumentException("Route must have at least 2 coordinates");
            }
            
            Coordinate[] coords = coordinates.stream()
                .map(c -> new Coordinate(c.longitude(), c.latitude()))
                .toArray(Coordinate[]::new);
            
            LineString geometry = geometryFactory.createLineString(coords);
            geometry.setSRID(4326);
            
            route.setGeometry(geometry);
            route.setDistanceKm(calculateDistance(route));
        }
        
        routeRepository.persist(route);
        
        LOG.info(String.format("Updated route: %s", routeId));
        return route;
    }
    
    @Transactional
    public void deleteRoute(UUID routeId) {
        Route route = routeRepository.findByIdOptional(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));
        
        Long activeBookings = bookingRepository.count(
            "route.id = ?1 and status in ('PENDING', 'CONFIRMED', 'IN_PROGRESS')", 
            routeId
        );
        
        if (activeBookings > 0) {
            throw new IllegalStateException(
                "Cannot delete route with active bookings"
            );
        }
        
        route.setIsActive(false);
        route.setIsPublished(false);
        
        routeRepository.persist(route);
        
        LOG.info(String.format("Deleted route: %s", routeId));
    }
    
    @Transactional
    public void activateRoute(UUID routeId, UUID driverId) {
        Route route = routeRepository.findByIdOptional(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));
        
        if (!route.getDriverId().equals(driverId)) {
            throw new IllegalArgumentException("You can only activate your own routes");
        }
        
        if (!route.getIsActive()) {
            throw new IllegalStateException("Cannot activate an inactive route");
        }
        
        // Deactivate all other routes for this driver
        List<Route> driverRoutes = routeRepository.findByDriverId(driverId);
        for (Route r : driverRoutes) {
            if (!r.getId().equals(routeId) && r.getIsPublished()) {
                r.setIsPublished(false);
            }
        }
        
        route.setIsPublished(true);
        routeRepository.persist(route);
        
        LOG.info(String.format("Activated route: %s for driver: %s", routeId, driverId));
    }
    
    @Transactional
    public void deactivateRoute(UUID routeId, UUID driverId) {
        Route route = routeRepository.findByIdOptional(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));
        
        if (!route.getDriverId().equals(driverId)) {
            throw new IllegalArgumentException("You can only deactivate your own routes");
        }
        
        Long inProgressBookings = bookingRepository.count(
            "route.id = ?1 and status = 'IN_PROGRESS'", 
            routeId
        );
        
        if (inProgressBookings > 0) {
            throw new IllegalStateException(
                "Cannot deactivate route with rides in progress"
            );
        }
        
        route.setIsPublished(false);
        routeRepository.persist(route);
        
        LOG.info(String.format("Deactivated route: %s for driver: %s", routeId, driverId));
    }
    
    // ==================== HELPER METHODS ====================
    
    private Point createPoint(double longitude, double latitude) {
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
    
    private Double calculateDistance(Route route) {
        return route.getGeometry().getLength() * 111.0;
    }
    
    // ==================== SIMPLE DTO ====================
    
    /**
     * Simple coordinate DTO used across all layers
     */
    public record CoordinateDTO(double latitude, double longitude) {}
}