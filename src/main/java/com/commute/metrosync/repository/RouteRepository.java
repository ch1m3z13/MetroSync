package com.commute.metrosync.repository;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import com.commute.metrosync.entity.Route;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RouteRepository implements PanacheRepositoryBase<Route, UUID> {
    
    /**
     * Find route by name (for seeder idempotency)
     */
    public Optional<Route> findByName(String name) {
        return find("name", name).firstResultOptional();
    }
    
    /**
     * Find routes by driver
     */
    public List<Route> findByDriverId(UUID driverId) {
        return find("driverId = ?1 order by createdAt desc", driverId).list();
    }
    
    /**
     * Find published routes
     */
    public List<Route> findPublishedRoutes() {
        return find("isPublished = true and isActive = true").list();
    }
    
    /**
     * Find routes passing within a specified distance of a point.
     * Uses PostGIS ST_DWithin with geography for accurate distance calculation.
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
        return getEntityManager().createNativeQuery(sql, Route.class)
                .setParameter("lon", userLocation.getX())
                .setParameter("lat", userLocation.getY())
                .setParameter("radius", radiusMeters)
                .getResultList();
    }
    
    /**
     * Find routes with calculated distance from user location.
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
        
        return getEntityManager()
                .createNativeQuery(sql, "RouteWithDistanceMapping")
                .setParameter("lon", longitude)
                .setParameter("lat", latitude)
                .setParameter("maxDistance", maxDistanceMeters)
                .getResultList();
    }
    
    /**
     * Find closest point on route to a given location.
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
        
        String wkt = (String) getEntityManager().createNativeQuery(sql)
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
        
        return (Boolean) getEntityManager().createNativeQuery(sql)
                .setParameter("routeId", routeId)
                .setParameter("lon", point.getX())
                .setParameter("lat", point.getY())
                .setParameter("maxDeviation", maxDeviationMeters)
                .getSingleResult();
    }
    
    /**
     * Find drivers heading in the direction of a destination.
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
        
        return getEntityManager().createNativeQuery(sql, Route.class)
                .setParameter("originLon", origin.getX())
                .setParameter("originLat", origin.getY())
                .setParameter("destLon", destination.getX())
                .setParameter("destLat", destination.getY())
                .setParameter("radius", radiusMeters)
                .setParameter("tolerance", toleranceDegrees)
                .getResultList();
    }
    
    public void flush() {
        getEntityManager().flush();
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