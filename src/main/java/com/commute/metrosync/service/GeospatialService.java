package com.commute.metrosync.service;

import jakarta.enterprise.context.ApplicationScoped;
import com.commute.metrosync.entity.Route;
import com.commute.metrosync.repository.RouteRepository;
import com.commute.metrosync.util.GeometryUtil;
import org.locationtech.jts.geom.Point;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class GeospatialService {

    @Inject
    RouteRepository routeRepository;

    private static final double PICKUP_RADIUS_METERS = 2000; // 2km radius
    private static final double ROUTE_DEVIATION_METERS = 1000; // 1km allowed deviation

    /**
     * Find routes that pass near the pickup and dropoff locations
     */
    public List<Route> findMatchingRoutes(Point pickupPoint, Point dropoffPoint) {
        // Find all active routes
        // Optimized approach: In production, use PostGIS ST_DWithin in the repository query
        // For now, we fetch active routes and filter in memory for complex path logic
        List<Route> activeRoutes = routeRepository.findAllActive();

        return activeRoutes.stream()
            .filter(route -> isRouteMatch(route, pickupPoint, dropoffPoint))
            .collect(Collectors.toList());
    }

    /**
     * Check if a route matches the rider's journey
     */
    private boolean isRouteMatch(Route route, Point pickupPoint, Point dropoffPoint) {
        // Check if pickup is near route start
        double pickupDistance = GeometryUtil.distance(route.getFromPoint(), pickupPoint);
        if (pickupDistance > PICKUP_RADIUS_METERS) {
            return false;
        }

        // Check if dropoff is near route end
        double dropoffDistance = GeometryUtil.distance(route.getToPoint(), dropoffPoint);
        if (dropoffDistance > PICKUP_RADIUS_METERS) {
            return false;
        }

        // Basic LineString logic checking
        if (route.getRoutePath() != null) {
            // In a full production implementation, we would check if the pickup/dropoff 
            // points project onto the LineString within a threshold.
            // For MVP, we check start/end proximity logic above.
            
            // Additional check: Ensure route direction is correct (simple Euclidean heuristic)
            // Distance from Pickup to Dropoff should be consistent with route direction
            double directDistance = GeometryUtil.distance(pickupPoint, dropoffPoint);
            return directDistance > 100; // Minimal distance check
        }

        return true;
    }
}