package com.commute.metrosync.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import com.commute.metrosync.entity.Route;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Route entity with PostGIS geospatial queries.
 * Uses Jakarta Persistence EntityManager for native spatial queries.
 */
@ApplicationScoped
public class RouteRepository {
    
    @Inject
    EntityManager em;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    /**
     * Find routes passing within a specified distance of a point.
     * Uses PostGIS ST_DWithin with geography for accurate distance calculation.
     * 
     * @param userLocation User's current location (Point)
     * @param radiusMeters Search radius in meters
     * @return List of routes within range
     */
    public List<Route> findRoutesWithinDistance(Point userLocation, double radiusMeters) {
        String sql = """
            SELECT * FROM routes r
            WHERE r.is_active = true
            AND r.is_published = true
            AND ST_DWithin(
                r.geometry::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                :radius
            )
            ORDER BY ST_Distance(
                r.geometry::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
            )
            LIMIT 20
        """;
        return em.createNativeQuery(sql, Route.class)
                .setParameter("lon", userLocation.getX())
                .setParameter("lat", userLocation.getY())
                .setParameter("radius", radiusMeters)
                .getResultList();
    }
    
    /**
     * Find routes with calculated distance from user location.
     * Uses native SQL for optimal PostGIS performance.
     * 
     * @param longitude User longitude
     * @param latitude User latitude
     * @param maxDistanceMeters Maximum distance in meters
     * @return List of routes with distance
     */
    public List<RouteWithDistance> findRoutesWithDistanceNative(
            double longitude, 
            double latitude, 
            double maxDistanceMeters) {
        
        String sql = """
            SELECT 
                r.id,
                r.name,
                r.distance_km,
                r.driver_id,
                ST_Distance(
                    r.geometry::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                ) as distance_meters
            FROM routes r
            WHERE r.is_active = true 
            AND r.is_published = true
            AND ST_DWithin(
                r.geometry::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                :maxDistance
            )
            ORDER BY distance_meters ASC
            LIMIT 20
            """;
        
        return em.createNativeQuery(sql, "RouteWithDistanceMapping")
                .setParameter("lon", longitude)
                .setParameter("lat", latitude)
                .setParameter("maxDistance", maxDistanceMeters)
                .getResultList();
    }
    
    /**
     * Find closest point on route to a given location.
     * Uses ST_ClosestPoint to find optimal pickup/dropoff point.
     * 
     * @param routeId Route UUID
     * @param userLocation User's location
     * @return Closest point on route geometry
     */
    public Point findClosestPointOnRoute(UUID routeId, Point userLocation) {
        String sql = """
            SELECT ST_AsText(
                ST_ClosestPoint(
                    r.geometry,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
                )
            )
            FROM routes r
            WHERE r.id = :routeId
            """;
        
        String wkt = (String) em.createNativeQuery(sql)
                .setParameter("routeId", routeId)
                .setParameter("lon", userLocation.getX())
                .setParameter("lat", userLocation.getY())
                .getSingleResult();
        
        // Parse WKT and return Point
        // In production, use JTS WKTReader
        return userLocation; // Simplified for example
    }
    
    /**
     * Check if a point is within deviation tolerance of route.
     * 
     * @param routeId Route UUID
     * @param point Point to check
     * @param maxDeviationMeters Maximum allowed deviation
     * @return true if point is within tolerance
     */
    public boolean isPointNearRoute(UUID routeId, Point point, double maxDeviationMeters) {
        String sql = """
            SELECT EXISTS(
                SELECT 1 FROM routes r
                WHERE r.id = :routeId
                AND ST_DWithin(
                    r.geometry::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                    :maxDeviation
                )
            )
            """;
        
        return (Boolean) em.createNativeQuery(sql)
                .setParameter("routeId", routeId)
                .setParameter("lon", point.getX())
                .setParameter("lat", point.getY())
                .setParameter("maxDeviation", maxDeviationMeters)
                .getSingleResult();
    }
    
    /**
     * Find drivers heading in the direction of a destination.
     * Uses azimuth calculation for directional matching.
     * 
     * @param origin Starting point
     * @param destination End point
     * @param toleranceDegrees Azimuth tolerance (e.g., 45 degrees)
     * @param radiusMeters Search radius from origin
     * @return Routes heading in similar direction
     */
    public List<Route> findRoutesHeadingTowards(
            Point origin, 
            Point destination,
            double toleranceDegrees,
            double radiusMeters) {
        
        String sql = """
            WITH user_azimuth AS (
                SELECT degrees(
                    ST_Azimuth(
                        ST_SetSRID(ST_MakePoint(:originLon, :originLat), 4326),
                        ST_SetSRID(ST_MakePoint(:destLon, :destLat), 4326)
                    )
                ) as target_bearing
            )
            SELECT DISTINCT r.*
            FROM routes r, user_azimuth ua
            WHERE r.is_active = true
            AND r.is_published = true
            AND ST_DWithin(
                r.geometry::geography,
                ST_SetSRID(ST_MakePoint(:originLon, :originLat), 4326)::geography,
                :radius
            )
            AND ABS(
                degrees(ST_Azimuth(
                    ST_StartPoint(r.geometry),
                    ST_EndPoint(r.geometry)
                )) - ua.target_bearing
            ) < :tolerance
            """;
        
        return em.createNativeQuery(sql, Route.class)
                .setParameter("originLon", origin.getX())
                .setParameter("originLat", origin.getY())
                .setParameter("destLon", destination.getX())
                .setParameter("destLat", destination.getY())
                .setParameter("radius", radiusMeters)
                .setParameter("tolerance", toleranceDegrees)
                .getResultList();
    }
    
    // Standard CRUD operations
    public Optional<Route> findById(UUID id) {
        return Optional.ofNullable(em.find(Route.class, id));
    }
    
    public Route save(Route route) {
        if (route.getId() == null) {
            em.persist(route);
            return route;
        } else {
            return em.merge(route);
        }
    }
    
    public void delete(UUID id) {
        findById(id).ifPresent(em::remove);
    }
    
    public List<Route> findByDriverId(UUID driverId) {
        return em.createQuery(
                "SELECT r FROM Route r WHERE r.driverId = :driverId ORDER BY r.createdAt DESC", 
                Route.class)
                .setParameter("driverId", driverId)
                .getResultList();
    }
    
    /**
     * DTO for routes with calculated distance
     */
    public record RouteWithDistance(
        UUID id,
        String name,
        Double distanceKm,
        UUID driverId,
        Double distanceMeters
    ) {}
}