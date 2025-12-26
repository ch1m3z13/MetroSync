package com.commute.metrosync.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import com.commute.metrosync.entity.Route;
import com.commute.metrosync.repository.RouteRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Business logic for Route operations.
 * Handles geospatial matching and validation.
 */
@ApplicationScoped
public class RouteService {
    
    private static final Logger LOG = Logger.getLogger(RouteService.class.getName());
    private static final double DEFAULT_SEARCH_RADIUS_METERS = 500.0;
    
    @Inject
    RouteRepository routeRepository;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    /**
     * Find drivers passing near a user's location.
     * Core matching logic for the commuter network.
     * 
     * @param latitude User latitude
     * @param longitude User longitude
     * @param radiusMeters Optional search radius (default 500m)
     * @return List of nearby routes
     */
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
    
    /**
     * Find drivers heading towards a destination.
     * Useful for directional matching.
     */
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
            45.0, // 45-degree tolerance
            radiusMeters
        );
    }
    
    /**
     * Create a new route for a driver.
     * Validates geometry and calculates distance.
     */
    @Transactional
    public Route createRoute(CreateRouteRequest request) {
        // Validate coordinates
        if (request.coordinates().size() < 2) {
            throw new IllegalArgumentException("Route must have at least 2 coordinates");
        }
        
        // Build LineString from coordinates
        Coordinate[] coords = request.coordinates().stream()
            .map(c -> new Coordinate(c.longitude(), c.latitude()))
            .toArray(Coordinate[]::new);
        
        Route route = new Route(
            request.name(),
            geometryFactory.createLineString(coords),
            request.driverId()
        );
        
        route.setDescription(request.description());
        
        // Calculate distance (could be done in DB trigger)
        route.setDistanceKm(calculateDistance(route));
        
        routeRepository.persist(route);
        return route;
    }
    
    /**
     * Validate if a pickup point is acceptable for a route.
     */
    public boolean isValidPickupPoint(UUID routeId, double latitude, double longitude) {
        Point pickupPoint = createPoint(longitude, latitude);
        return routeRepository.isPointNearRoute(
            routeId, 
            pickupPoint, 
            DEFAULT_SEARCH_RADIUS_METERS
        );
    }
    
    private Point createPoint(double longitude, double latitude) {
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326); // WGS84
        return point;
    }
    
    private Double calculateDistance(Route route) {
        // Simplified - in production, use PostGIS ST_Length(geography)
        return route.getGeometry().getLength() * 111.0; // Rough km conversion
    }
    
    // DTOs
    public record CreateRouteRequest(
        String name,
        String description,
        List<CoordinatePair> coordinates,
        UUID driverId
    ) {}
    
    public record CoordinatePair(
        double latitude,
        double longitude
    ) {}
}
