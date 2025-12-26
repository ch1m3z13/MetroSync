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
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Seeds the database with sample routes, users, and bookings for Abuja on startup.
 * Fully idempotent - can be run multiple times safely.
 * Only runs in DEV mode.
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
        
        LOG.info("🌱 Starting database seeding process...");
        
        try {
            // Seed users (idempotent)
            User rider = getOrCreateRider();
            User driver1 = getOrCreateDriver1();
            User driver2 = getOrCreateDriver2();
            
            // Seed vehicles (idempotent)
            Vehicle vehicle1 = getOrCreateVehicle1(driver1);
            Vehicle vehicle2 = getOrCreateVehicle2(driver2);
            
            // Seed routes (idempotent)
            Route route1 = getOrCreateCentralToMaitama(driver1.getId());
            Route route2 = getOrCreateGwarinpaToWuse(driver2.getId());
            Route route3 = getOrCreateKubowaToJabi(driver1.getId());
            
            // Seed sample bookings (idempotent)
            getOrCreateBookings(rider, route1, route2);
            
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
     * Get or create demo rider
     */
private User getOrCreateRider() {
    // 1. Check if username OR phone number already exists to prevent constraint violations
    Optional<User> existing = userRepository.find("username = ?1 or phoneNumber = ?2", 
                                                 "demo_rider", "+2348012345678").firstResultOptional();
    
    if (existing.isPresent()) {
        LOG.info("✓ Rider already exists (matching username or phone): " + existing.get().getFullName());
        return existing.get();
    }
    
    User rider = new User(
        "demo_rider",
        "password123",
        "Amina Bello",
        "amina@example.com",
        "+2348012345678"
    );
    rider.setIsVerified(true);
    rider.setIsActive(true);
    
    // 2. Use persist() instead of persistAndFlush()
    // Let the @Transactional boundary handle the flush.
    userRepository.persist(rider); 
    
    LOG.info("✅ Created rider: " + rider.getFullName());
    return rider;
}
    
    /**
     * Get or create demo driver 1
     */
private User getOrCreateDriver1() {
    Optional<User> existing = userRepository.find("username = ?1 or phoneNumber = ?2", 
                                                 "demo_driver1", "+23480876574321").firstResultOptional();
    if (existing.isPresent()) {
        LOG.info("✓ Driver 1 already exists: " + existing.get().getFullName());
        return existing.get();
    }
        
        User driver = new User(
            "demo_driver1",
            "password123",
            "Emeka Okafor",
            "emeka@example.com",
            "+23480876574321"
        );
        driver.addRole(UserRole.DRIVER);
        driver.setIsVerified(true);
        driver.setIsActive(true);
        userRepository.persistAndFlush(driver);
        LOG.info("✅ Created driver: " + driver.getFullName());
        return driver;
    }
    
    /**
     * Get or create demo driver 2
     */
private User getOrCreateDriver2() {
    // 1. Check if username OR phone number already exists to prevent unique constraint violations
    Optional<User> existing = userRepository.find("username = ?1 or phoneNumber = ?2", 
                                                 "demo_driver2", "+23480987654327").firstResultOptional();
    if (existing.isPresent()) {
        LOG.info("✓ Driver 2 already exists (matching username or phone): " + existing.get().getFullName());
        return existing.get();
    }
    
    User driver = new User(
        "demo_driver2",
        "password123",
        "Fatima Ibrahim",
        "fatima@example.com",
        "+23480987654327"
    );
    
    driver.addRole(UserRole.DRIVER);
    driver.setIsVerified(true);
    driver.setIsActive(true);
    
    // 2. Use persist() to save. 
    // This avoids forcing a write to the DB until the whole seeding transaction is ready.
    userRepository.persist(driver);
    
    LOG.info("✅ Created driver: " + driver.getFullName());
    return driver;
}
    
    /**
     * Get or create vehicle 1
     */
    private Vehicle getOrCreateVehicle1(User driver) {
        String licensePlate = "ABC-123-DE";
        Optional<Vehicle> existing = vehicleRepository.findByLicensePlate(licensePlate);
        if (existing.isPresent()) {
            LOG.info("✓ Vehicle 1 already exists: " + existing.get().getDisplayName());
            return existing.get();
        }
    
        Vehicle vehicle = new Vehicle(driver, "Toyota", "Camry", 2020, "Black", licensePlate, 4);
        vehicle.setVehicleType(VehicleType.SEDAN);
        vehicle.setIsVerified(true);
    
        vehicleRepository.persist(vehicle); // Removed AndFlush
        return vehicle;
    }
    
    /**
     * Get or create vehicle 2
     */
    private Vehicle getOrCreateVehicle2(User driver) {
        String licensePlate = "XYZ-789-FG";
        Optional<Vehicle> existing = vehicleRepository.findByLicensePlate(licensePlate);
        if (existing.isPresent()) {
            LOG.info("✓ Vehicle 2 already exists: " + existing.get().getDisplayName());
            return existing.get();
        }
        
        Vehicle vehicle = new Vehicle(
            driver,
            "Toyota",
            "Hiace",
            2019,
            "White",
            licensePlate,
            14
        );
        vehicle.setVehicleType(VehicleType.MINIBUS);
        vehicle.setIsVerified(true);
        vehicleRepository.persist(vehicle);
        LOG.info("✅ Created vehicle: " + vehicle.getDisplayName());
        return vehicle;
    }
    
    /**
     * Get or create sample bookings
     */
    private void getOrCreateBookings(User rider, Route route1, Route route2) {
        // Booking 1: Confirmed for tomorrow morning
        String bookingRef1 = "SEED-BOOKING-001";
        if (bookingRepository.findByReferenceNumber(bookingRef1).isEmpty()) {
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
            booking1.setReferenceNumber(bookingRef1);
            booking1.setStatus(BookingStatus.CONFIRMED);
            booking1.setPassengerCount(1);
            booking1.setDistanceKm(BigDecimal.valueOf(3.2));
            booking1.setConfirmedAt(LocalDateTime.now());
            bookingRepository.persistAndFlush(booking1);
            LOG.info("✅ Created confirmed booking for tomorrow at 8:00 AM");
        } else {
            LOG.info("✓ Booking 1 already exists");
        }
        
        // Booking 2: Pending for tomorrow afternoon
        String bookingRef2 = "SEED-BOOKING-002";
        if (bookingRepository.findByReferenceNumber(bookingRef2).isEmpty()) {
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
            booking2.setReferenceNumber(bookingRef2);
            booking2.setStatus(BookingStatus.PENDING);
            booking2.setPassengerCount(2);
            booking2.setDistanceKm(BigDecimal.valueOf(5.8));
            bookingRepository.persistAndFlush(booking2);
            LOG.info("✅ Created pending booking for tomorrow at 5:30 PM");
        } else {
            LOG.info("✓ Booking 2 already exists");
        }
        
        // Booking 3: Completed from yesterday
        String bookingRef3 = "SEED-BOOKING-003";
        if (bookingRepository.findByReferenceNumber(bookingRef3).isEmpty()) {
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
            booking3.setReferenceNumber(bookingRef3);
            booking3.setStatus(BookingStatus.COMPLETED);
            booking3.setPassengerCount(1);
            booking3.setDistanceKm(BigDecimal.valueOf(2.5));
            booking3.setActualPickupTime(LocalDateTime.now().minusDays(1).withHour(9).withMinute(5));
            booking3.setActualDropoffTime(LocalDateTime.now().minusDays(1).withHour(9).withMinute(20));
            booking3.setCompletedAt(LocalDateTime.now().minusDays(1).withHour(9).withMinute(20));
            booking3.setRiderRating(5);
            booking3.setDriverRating(5);
            bookingRepository.persistAndFlush(booking3);
            LOG.info("✅ Created completed booking from yesterday");
        } else {
            LOG.info("✓ Booking 3 already exists");
        }
    }
    
    /**
     * Route 1: Central Business District to Maitama
     */
    
    private Route getOrCreateCentralToMaitama(UUID driverId) {
        String routeName = "Central Area → Maitama";
        Optional<Route> existing = routeRepository.findByName(routeName);
        if (existing.isPresent()) {
            LOG.info("✓ Route already exists: " + routeName);
            return existing.get();
        }

        // Create route geometry (polyline)
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(7.4905, 9.0574),  // Central Area
            new Coordinate(7.4920, 9.0600),  // Area 3
            new Coordinate(7.4935, 9.0625),  // Wuse
            new Coordinate(7.4950, 9.0765)   // Maitama
        };
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);

        Route route = new Route(routeName, geometry, driverId);
        route.setDescription("Morning commute through central Abuja");
        route.setDistanceKm(5.2);
        route.setIsPublished(true);
        route.setMaxDeviationMeters(500);

        // Add virtual stops to the object FIRST
        addVirtualStop(route, "Central Business District", 7.4905, 9.0574, 0, 0);
        addVirtualStop(route, "Area 3 Junction", 7.4920, 9.0600, 1, 5);
        addVirtualStop(route, "Wuse Market", 7.4935, 9.0625, 2, 10);
        addVirtualStop(route, "Maitama District", 7.4950, 9.0765, 3, 15);

        // NOW persist the whole thing
        routeRepository.persistAndFlush(route);

        LOG.info("✅ Created route with stops: " + routeName);
        return route;
    }
    
    /**
     * Route 2: Gwarinpa to Wuse
     */
    private Route getOrCreateGwarinpaToWuse(UUID driverId) {
        String routeName = "Gwarinpa → Wuse";
        Optional<Route> existing = routeRepository.findByName(routeName);
        if (existing.isPresent()) {
            LOG.info("✓ Route already exists: " + routeName);
            return existing.get();
        }
        
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(7.4124, 9.1108),  // Gwarinpa
            new Coordinate(7.4300, 9.0950),  // Dutse
            new Coordinate(7.4500, 9.0800),  // Berger
            new Coordinate(7.4935, 9.0625)   // Wuse
        };
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        Route route = new Route(
            routeName,
            geometry,
            driverId
        );
        route.setDescription("Popular route from Gwarinpa estate to Wuse business district");
        route.setDistanceKm(BigDecimal.valueOf(8.5).doubleValue());
        route.setIsPublished(true);
        
        routeRepository.persistAndFlush(route);
        
        addVirtualStop(route, "Gwarinpa Estate", 7.4124, 9.1108, 0, 0);
        addVirtualStop(route, "Dutse Junction", 7.4300, 9.0950, 1, 8);
        addVirtualStop(route, "Berger Roundabout", 7.4500, 9.0800, 2, 15);
        addVirtualStop(route, "Wuse Zone 5", 7.4935, 9.0625, 3, 20);
        
        LOG.info("✅ Created route: " + routeName);
        return route;
    }
    
    /**
     * Route 3: Kubwa to Jabi
     */
    private Route getOrCreateKubowaToJabi(UUID driverId) {
        String routeName = "Kubwa → Jabi";
        Optional<Route> existing = routeRepository.findByName(routeName);
        if (existing.isPresent()) {
            LOG.info("✓ Route already exists: " + routeName);
            return existing.get();
        }
        
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(7.3386, 9.0965),  // Kubwa
            new Coordinate(7.3700, 9.0850),  // Gwarinpa Junction
            new Coordinate(7.4200, 9.0750),  // Wuye
            new Coordinate(7.4600, 9.0700)   // Jabi
        };
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        Route route = new Route(
            routeName,
            geometry,
            driverId
        );
        route.setDescription("Major route connecting Kubwa residential area to Jabi");
        route.setDistanceKm(BigDecimal.valueOf(12.3).doubleValue());
        route.setIsPublished(true);
        
        routeRepository.persistAndFlush(route);
        
        addVirtualStop(route, "Kubwa Market", 7.3386, 9.0965, 0, 0);
        addVirtualStop(route, "Gwarinpa Junction", 7.3700, 9.0850, 1, 10);
        addVirtualStop(route, "Wuye District", 7.4200, 9.0750, 2, 18);
        addVirtualStop(route, "Jabi Lake Mall", 7.4600, 9.0700, 3, 25);
        
        LOG.info("✅ Created route: " + routeName);
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