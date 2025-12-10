package com.commute.metrosync.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import com.commute.metrosync.entity.Route;
import com.commute.metrosync.entity.VirtualStop;
import com.commute.metrosync.repository.RouteRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Seeds the database with sample routes for Abuja on startup.
 * Only runs in DEV mode and if database is empty.
 */
@ApplicationScoped
public class DatabaseSeeder {
    
    private static final Logger LOG = Logger.getLogger(DatabaseSeeder.class.getName());
    
    @Inject
    RouteRepository routeRepository;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    /**
     * Seeds database on application startup
     */
    @Transactional
    public void seedDatabase(@Observes StartupEvent event) {
        // Only seed if running in dev mode
        String profile = System.getProperty("quarkus.profile", "dev");
        if (!"dev".equals(profile)) {
            return;
        }
        
        LOG.info("🌱 Checking if database needs seeding...");
        
        // Check if routes already exist
        if (!routeRepository.findByDriverId(UUID.randomUUID()).isEmpty()) {
            LOG.info("✅ Database already contains routes. Skipping seed.");
            return;
        }
        
        LOG.info("🌱 Seeding database with sample Abuja routes...");
        
        try {
            seedCentralToMaitama();
            seedGwarinpaToWuse();
            seedKubowaToJabi();
            
            LOG.info("✅ Database seeding completed successfully!");
        } catch (Exception e) {
            LOG.severe("❌ Failed to seed database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Route 1: Central Business District to Maitama
     */
    private void seedCentralToMaitama() {
        UUID driverId = UUID.randomUUID();
        
        // Create route geometry (polyline)
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(7.4905, 9.0574),  // Central Area
            new Coordinate(7.4920, 9.0600),  // Area 3
            new Coordinate(7.4935, 9.0625),  // Wuse
            new Coordinate(7.4950, 9.0765)   // Maitama
        };
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        Route route = new Route(
            "Central Area → Maitama",
            geometry,
            driverId
        );
        route.setDescription("Morning commute through central Abuja");
        route.setDistanceKm(BigDecimal.valueOf(5.2).doubleValue());
        route.setIsPublished(true);
        route.setMaxDeviationMeters(500);
        
        // Save route
        routeRepository.save(route);
        
        // Add virtual stops
        addVirtualStop(route, "Central Business District", 7.4905, 9.0574, 0, 0);
        addVirtualStop(route, "Area 3 Junction", 7.4920, 9.0600, 1, 5);
        addVirtualStop(route, "Wuse Market", 7.4935, 9.0625, 2, 10);
        addVirtualStop(route, "Maitama District", 7.4950, 9.0765, 3, 15);
        
        LOG.info("✅ Created route: Central Area → Maitama");
    }
    
    /**
     * Route 2: Gwarinpa to Wuse
     */
    private void seedGwarinpaToWuse() {
        UUID driverId = UUID.randomUUID();
        
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(7.4124, 9.1108),  // Gwarinpa
            new Coordinate(7.4300, 9.0950),  // Dutse
            new Coordinate(7.4500, 9.0800),  // Berger
            new Coordinate(7.4935, 9.0625)   // Wuse
        };
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        Route route = new Route(
            "Gwarinpa → Wuse",
            geometry,
            driverId
        );
        route.setDescription("Popular route from Gwarinpa estate to Wuse business district");
        route.setDistanceKm(BigDecimal.valueOf(8.5).doubleValue());
        route.setIsPublished(true);
        
        routeRepository.save(route);
        
        addVirtualStop(route, "Gwarinpa Estate", 7.4124, 9.1108, 0, 0);
        addVirtualStop(route, "Dutse Junction", 7.4300, 9.0950, 1, 8);
        addVirtualStop(route, "Berger Roundabout", 7.4500, 9.0800, 2, 15);
        addVirtualStop(route, "Wuse Zone 5", 7.4935, 9.0625, 3, 20);
        
        LOG.info("✅ Created route: Gwarinpa → Wuse");
    }
    
    /**
     * Route 3: Kubwa to Jabi
     */
    private void seedKubowaToJabi() {
        UUID driverId = UUID.randomUUID();
        
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(7.3386, 9.0965),  // Kubwa
            new Coordinate(7.3700, 9.0850),  // Gwarinpa Junction
            new Coordinate(7.4200, 9.0750),  // Wuye
            new Coordinate(7.4600, 9.0700)   // Jabi
        };
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        Route route = new Route(
            "Kubwa → Jabi",
            geometry,
            driverId
        );
        route.setDescription("Major route connecting Kubwa residential area to Jabi");
        route.setDistanceKm(BigDecimal.valueOf(12.3).doubleValue());
        route.setIsPublished(true);
        
        routeRepository.save(route);
        
        addVirtualStop(route, "Kubwa Market", 7.3386, 9.0965, 0, 0);
        addVirtualStop(route, "Gwarinpa Junction", 7.3700, 9.0850, 1, 10);
        addVirtualStop(route, "Wuye District", 7.4200, 9.0750, 2, 18);
        addVirtualStop(route, "Jabi Lake Mall", 7.4600, 9.0700, 3, 25);
        
        LOG.info("✅ Created route: Kubwa → Jabi");
    }
    
    /**
     * Helper method to create virtual stops
     */
    private void addVirtualStop(Route route, String name, 
                                double longitude, double latitude,
                                int sequence, int timeOffset) {
        Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        location.setSRID(4326);
        
        VirtualStop stop = new VirtualStop(name, location, route, sequence);
        stop.setTimeOffsetMinutes(timeOffset);
        stop.setDescription("Stop along " + route.getName());
        
        route.addVirtualStop(stop);
    }
}