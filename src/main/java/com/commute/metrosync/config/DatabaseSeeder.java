package com.commute.metrosync.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import com.commute.metrosync.entity.*;
import com.commute.metrosync.repository.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Seeds the database with sample routes, users, and bookings for Abuja on startup.
 * Only runs in DEV mode and if database is empty.
 */
@ApplicationScoped
public class DatabaseSeeder {
    
    private static final Logger LOG = Logger.getLogger(DatabaseSeeder.class.getName());
    
    @Inject
    RouteRepository routeRepository;
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    BookingRepository bookingRepository;
    
    @Inject
    VehicleRepository vehicleRepository;
    
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
        
        // Check if data already exists
        if (userRepository.existsByUsername("demo_rider")) {
            LOG.info("✅ Database already seeded. Skipping.");
            return;
        }
        
        LOG.info("🌱 Seeding database with sample data...");
        
        try {
            // Seed users first
            List<User> users = seedUsers();
            User rider = users.get(0);
            User driver1 = users.get(1);
            User driver2 = users.get(2);
            
            // Seed vehicles for drivers
            seedVehicles(driver1, driver2);
            
            // Seed routes
            Route route1 = seedCentralToMaitama(driver1.getId());
            Route route2 = seedGwarinpaToWuse(driver2.getId());
            Route route3 = seedKubowaToJabi(driver1.getId());
            
            // Seed sample bookings
            seedBookings(rider, route1, route2);
            
            LOG.info("✅ Database seeding completed successfully!");
            LOG.info("📝 Test Credentials:");
            LOG.info("   Rider - username: demo_rider, password: password123");
            LOG.info("   Driver - username: demo_driver1, password: password123");
            LOG.info("   Driver - username: demo_driver2, password: password123");
        } catch (Exception e) {
            LOG.severe("❌ Failed to seed database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Seed sample users
     */
    private List<User> seedUsers() {
        User rider = new User(
            "demo_rider",
            "password123",
            "Amina Bello",
            "amina@example.com",
            "+2348012345678"
        );
        rider.setIsVerified(true);
        rider.setIsActive(true);
        userRepository.save(rider);
        LOG.info("✅ Created rider: " + rider.getFullName());
        
        User driver1 = new User(
            "demo_driver1",
            "password123",
            "Emeka Okafor",
            "emeka@example.com",
            "+2348087654321"
        );
        driver1.addRole(UserRole.DRIVER);
        driver1.setIsVerified(true);
        driver1.setIsActive(true);
        userRepository.save(driver1);
        LOG.info("✅ Created driver: " + driver1.getFullName());
        
        User driver2 = new User(
            "demo_driver2",
            "password123",
            "Fatima Ibrahim",
            "fatima@example.com",
            "+2348098765432"
        );
        driver2.addRole(UserRole.DRIVER);
        driver2.setIsVerified(true);
        driver2.setIsActive(true);
        userRepository.save(driver2);
        LOG.info("✅ Created driver: " + driver2.getFullName());
        
        return List.of(rider, driver1, driver2);
    }
    
    /**
     * Seed sample vehicles
     */
    private void seedVehicles(User driver1, User driver2) {
        Vehicle vehicle1 = new Vehicle(
            driver1,
            "Toyota",
            "Camry",
            2020,
            "Black",
            "ABC-123-DE",
            4
        );
        vehicle1.setVehicleType(VehicleType.SEDAN);
        vehicle1.setIsVerified(true);
        vehicleRepository.save(vehicle1);
        LOG.info("✅ Created vehicle: " + vehicle1.getDisplayName());
        
        Vehicle vehicle2 = new Vehicle(
            driver2,
            "Toyota",
            "Hiace",
            2019,
            "White",
            "XYZ-789-FG",
            14
        );
        vehicle2.setVehicleType(VehicleType.MINIBUS);
        vehicle2.setIsVerified(true);
        vehicleRepository.save(vehicle2);
        LOG.info("✅ Created vehicle: " + vehicle2.getDisplayName());
    }
    
    /**
     * Seed sample bookings
     */
    private void seedBookings(User rider, Route route1, Route route2) {
        // Booking 1: Confirmed for tomorrow morning
        Point pickup1 = createPoint(7.4920, 9.0600);
        Point dropoff1 = createPoint(7.4950, 9.0765);
        
        Booking booking1 = new Booking(
            rider,
            route1,
            pickup1,
            dropoff1,
            LocalDateTime.now().plusDays(1).withHour(8).withMinute(0),
            BigDecimal.valueOf(350.00)
        );
        booking1.setStatus(BookingStatus.CONFIRMED);
        booking1.setPassengerCount(1);
        booking1.setDistanceKm(BigDecimal.valueOf(3.2));
        booking1.setConfirmedAt(LocalDateTime.now());
        bookingRepository.save(booking1);
        LOG.info("✅ Created confirmed booking for tomorrow at 8:00 AM");
        
        // Booking 2: Pending for tomorrow afternoon
        Point pickup2 = createPoint(7.4300, 9.0950);
        Point dropoff2 = createPoint(7.4935, 9.0625);
        
        Booking booking2 = new Booking(
            rider,
            route2,
            pickup2,
            dropoff2,
            LocalDateTime.now().plusDays(1).withHour(17).withMinute(30),
            BigDecimal.valueOf(450.00)
        );
        booking2.setStatus(BookingStatus.PENDING);
        booking2.setPassengerCount(2);
        booking2.setDistanceKm(BigDecimal.valueOf(5.8));
        bookingRepository.save(booking2);
        LOG.info("✅ Created pending booking for tomorrow at 5:30 PM");
        
        // Booking 3: Completed from yesterday
        Point pickup3 = createPoint(7.4905, 9.0574);
        Point dropoff3 = createPoint(7.4935, 9.0625);
        
        Booking booking3 = new Booking(
            rider,
            route1,
            pickup3,
            dropoff3,
            LocalDateTime.now().minusDays(1).withHour(9).withMinute(0),
            BigDecimal.valueOf(300.00)
        );
        booking3.setStatus(BookingStatus.COMPLETED);
        booking3.setPassengerCount(1);
        booking3.setDistanceKm(BigDecimal.valueOf(2.5));
        booking3.setActualPickupTime(LocalDateTime.now().minusDays(1).withHour(9).withMinute(5));
        booking3.setActualDropoffTime(LocalDateTime.now().minusDays(1).withHour(9).withMinute(20));
        booking3.setCompletedAt(LocalDateTime.now().minusDays(1).withHour(9).withMinute(20));
        booking3.setRiderRating(5);
        booking3.setDriverRating(5);
        bookingRepository.save(booking3);
        LOG.info("✅ Created completed booking from yesterday");
    }
    
    /**
     * Route 1: Central Business District to Maitama
     */
    private Route seedCentralToMaitama(UUID driverId) {
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
        route = routeRepository.save(route);
        
        // Add virtual stops
        addVirtualStop(route, "Central Business District", 7.4905, 9.0574, 0, 0);
        addVirtualStop(route, "Area 3 Junction", 7.4920, 9.0600, 1, 5);
        addVirtualStop(route, "Wuse Market", 7.4935, 9.0625, 2, 10);
        addVirtualStop(route, "Maitama District", 7.4950, 9.0765, 3, 15);
        
        LOG.info("✅ Created route: Central Area → Maitama");
        return route;
    }
    
    /**
     * Route 2: Gwarinpa to Wuse
     */
    private Route seedGwarinpaToWuse(UUID driverId) {
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
        
        route = routeRepository.save(route);
        
        addVirtualStop(route, "Gwarinpa Estate", 7.4124, 9.1108, 0, 0);
        addVirtualStop(route, "Dutse Junction", 7.4300, 9.0950, 1, 8);
        addVirtualStop(route, "Berger Roundabout", 7.4500, 9.0800, 2, 15);
        addVirtualStop(route, "Wuse Zone 5", 7.4935, 9.0625, 3, 20);
        
        LOG.info("✅ Created route: Gwarinpa → Wuse");
        return route;
    }
    
    /**
     * Route 3: Kubwa to Jabi
     */
    private Route seedKubowaToJabi(UUID driverId) {
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
        
        route = routeRepository.save(route);
        
        addVirtualStop(route, "Kubwa Market", 7.3386, 9.0965, 0, 0);
        addVirtualStop(route, "Gwarinpa Junction", 7.3700, 9.0850, 1, 10);
        addVirtualStop(route, "Wuye District", 7.4200, 9.0750, 2, 18);
        addVirtualStop(route, "Jabi Lake Mall", 7.4600, 9.0700, 3, 25);
        
        LOG.info("✅ Created route: Kubwa → Jabi");
        return route;
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
    
    /**
     * Helper method to create a Point
     */
    private Point createPoint(double longitude, double latitude) {
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
}