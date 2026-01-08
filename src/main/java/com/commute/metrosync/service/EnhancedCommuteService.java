package com.commute.metrosync.service;

import com.commute.metrosync.dto.CommuteDTOs.*;
import com.commute.metrosync.entity.*;
import com.commute.metrosync.repository.*;
import com.commute.metrosync.service.DirectionDetectorService.DetectionResult;
import com.commute.metrosync.service.GoogleDirectionsService.RouteResponse;
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
import java.util.stream.Collectors;

/**
 * ENHANCED Commute Service with:
 * ✅ Google Directions API integration (real routes)
 * ✅ Multiple route variations support
 * ✅ Dynamic capacity updates
 * ✅ Smart direction auto-detection
 */
@ApplicationScoped
public class EnhancedCommuteService {
    
    @Inject
    CommuteRepository commuteRepository;
    
    @Inject
    RouteVariationRepository variationRepository;
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    RouteRepository routeRepository;
    
    @Inject
    GoogleDirectionsService directionsService;
    
    @Inject
    DirectionDetectorService directionDetector;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    // ==================== SAVE COMMUTE (Enhanced with Google Directions) ====================
    
    /**
     * Save commute and generate route variations using Google Directions API
     */
    @Transactional
    public CommuteResponse saveCommute(SaveCommuteRequest request) {
        Log.info("Saving enhanced commute for driver: " + request.driverId());
        
        // 1. Validate driver
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
        
        // 4. Find or create commute
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
        
        // 5. Update fields if existing
        if (commute.getId() != null) {
            commute.setHomeAddress(request.homeAddress());
            commute.setHomeLocation(homeLocation);
            commute.setWorkAddress(request.workAddress());
            commute.setWorkLocation(workLocation);
            commute.setDepartureTime(departureTime);
            commute.setReturnTime(returnTime);
            commute.setCapacity(request.capacity());
        }
        
        // 6. Calculate straight-line distance (fallback)
        double distanceKm = calculateDistance(homeLocation, workLocation);
        commute.setCommuteDistanceKm(distanceKm);
        
        // 7. Save commute first
        commuteRepository.persist(commute);
        commuteRepository.flush();
        
        // 8. Generate route variations using Google Directions API
        generateRouteVariations(commute);
        
        Log.info("Commute saved with route variations");
        
        return toCommuteResponse(commute);
    }
    
    /**
     * Generate route variations using Google Directions API
     */
    private void generateRouteVariations(DriverCommute commute) {
        Log.info("Generating route variations using Google Directions API");
        
        // Generate TO_WORK routes
        generateVariationsForDirection(
            commute,
            CommuteDirection.TO_WORK,
            commute.getHomeLocation(),
            commute.getWorkLocation()
        );
        
        // Generate TO_HOME routes
        generateVariationsForDirection(
            commute,
            CommuteDirection.TO_HOME,
            commute.getWorkLocation(),
            commute.getHomeLocation()
        );
    }
    
    /**
     * Generate route variations for a specific direction
     */
    private void generateVariationsForDirection(
            DriverCommute commute,
            CommuteDirection direction,
            Point origin,
            Point destination) {
        
        try {
            // Get route alternatives from Google
            List<RouteResponse> alternatives = directionsService.getRouteAlternatives(
                origin.getY(),  // latitude
                origin.getX(),  // longitude
                destination.getY(),
                destination.getX()
            );
            
            Log.info(String.format("Found %d route alternatives for %s", 
                alternatives.size(), direction));
            
            // Save each alternative as a RouteVariation
            for (int i = 0; i < alternatives.size(); i++) {
                RouteResponse response = alternatives.get(i);
                
                String name = (i == 0) ? "Recommended Route" : "Alternative Route " + i;
                
                RouteVariation variation = new RouteVariation(
                    commute,
                    direction,
                    name,
                    response.geometry()
                );
                
                variation.setDescription(response.summary());
                variation.setDistanceKm(response.distanceKm());
                variation.setDurationMinutes(response.durationMinutes());
                variation.setEncodedPolyline(response.encodedPolyline());
                variation.setRouteSummary(response.summary());
                variation.setIsPreferred(i == 0);  // First route is preferred
                
                variationRepository.persist(variation);
                
                Log.info(String.format("Saved route variation: %s (%.2f km, %d min)",
                    name, response.distanceKm(), response.durationMinutes()));
            }
            
        } catch (Exception e) {
            Log.error("Failed to generate route variations", e);
            // Create fallback straight-line route
            createFallbackVariation(commute, direction, origin, destination);
        }
    }
    
    /**
     * Create simple fallback route if Google Directions fails
     */
    private void createFallbackVariation(
            DriverCommute commute,
            CommuteDirection direction,
            Point origin,
            Point destination) {
        
        Coordinate[] coords = new Coordinate[]{
            new Coordinate(origin.getX(), origin.getY()),
            new Coordinate(destination.getX(), destination.getY())
        };
        
        LineString geometry = geometryFactory.createLineString(coords);
        geometry.setSRID(4326);
        
        RouteVariation variation = new RouteVariation(
            commute,
            direction,
            "Direct Route",
            geometry
        );
        
        double distance = calculateDistance(origin, destination);
        variation.setDistanceKm(distance);
        variation.setDurationMinutes((int) (distance * 2));
        variation.setIsPreferred(true);
        
        variationRepository.persist(variation);
    }
    
    // ==================== UPDATE CAPACITY (Dynamic) ====================
    
    /**
     * Update driver's current capacity
     * Allows drivers to adjust capacity on-the-fly
     */
    @Transactional
    public CapacityUpdateResponse updateCapacity(UUID driverId, int newCapacity) {
        Log.info(String.format("Updating capacity for driver %s to %d", driverId, newCapacity));
        
        // Validate capacity
        if (newCapacity < 1 || newCapacity > 20) {
            throw new IllegalArgumentException("Capacity must be between 1 and 20");
        }
        
        // Get commute
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException("No commute found for this driver"));
        
        int oldCapacity = commute.getCapacity();
        commute.setCapacity(newCapacity);
        commuteRepository.persist(commute);
        
        Log.info(String.format("Capacity updated: %d → %d", oldCapacity, newCapacity));
        
        return new CapacityUpdateResponse(
            driverId.toString(),
            newCapacity,
            oldCapacity,
            "Capacity updated successfully"
        );
    }
    
    // ==================== ACTIVATE COMMUTE (Smart Direction Detection) ====================
    
    /**
     * Activate commute with smart direction auto-detection
     */
    @Transactional
    public ActivateCommuteResponse activateCommuteAuto(UUID driverId) {
        Log.info("Activating commute with auto-detection for driver: " + driverId);
        
        // Get driver and commute
        User driver = userRepository.findByIdOptional(driverId)
            .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException("No commute found"));
        
        // Smart direction detection
        DetectionResult detection = directionDetector.detectDirection(commute, driver);
        
        Log.info(String.format("Detected direction: %s (confidence: %.2f) - %s",
            detection.direction(), detection.confidence(), detection.reason()));
        
        // Activate with detected direction
        return activateCommute(driverId, detection.direction(), detection);
    }
    
    /**
     * Activate commute with manual direction
     */
    @Transactional
    public ActivateCommuteResponse activateCommute(
            UUID driverId,
            CommuteDirection direction) {
        
        return activateCommute(driverId, direction, null);
    }
    
    /**
     * Activate commute (internal method)
     */
    private ActivateCommuteResponse activateCommute(
            UUID driverId,
            CommuteDirection direction,
            DetectionResult detection) {
        
        Log.info(String.format("Activating commute for driver %s, direction: %s",
            driverId, direction));
        
        // Get commute
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException("No commute found"));
        
        // Get preferred route variation for this direction
        RouteVariation preferredVariation = variationRepository
            .findPreferredRoute(commute.getId(), direction)
            .orElseGet(() -> {
                // Fallback: get any route for this direction
                List<RouteVariation> variations = variationRepository
                    .findByCommuteIdAndDirection(commute.getId(), direction);
                
                if (variations.isEmpty()) {
                    throw new IllegalStateException(
                        "No route variations found. Please save commute first.");
                }
                
                return variations.get(0);
            });
        
        // Deactivate existing routes
        List<Route> existingRoutes = routeRepository.findByDriverId(driverId);
        existingRoutes.forEach(route -> {
            route.setIsPublished(false);
            route.setIsActive(false);
        });
        
        // Create active route from variation
        Route activeRoute = createRouteFromVariation(preferredVariation, driverId);
        routeRepository.persist(activeRoute);
        
        // Update driver status
        User driver = commute.getDriver();
        driver.setDriverStatus("ONLINE");
        userRepository.persist(driver);
        
        String message = detection != null
            ? String.format("Route activated (auto-detected with %.0f%% confidence)",
                detection.confidence() * 100)
            : "Route activated successfully";
        
        return new ActivateCommuteResponse(
            activeRoute.getId().toString(),
            activeRoute.getName(),
            "ACTIVE",
            direction.name(),
            message
        );
    }
    
    /**
     * Create Route entity from RouteVariation
     */
    private Route createRouteFromVariation(RouteVariation variation, UUID driverId) {
        String routeName = String.format("%s (%s)",
            variation.getName(),
            variation.getDirection() == CommuteDirection.TO_WORK ? "Morning" : "Evening"
        );
        
        Route route = new Route(routeName, variation.getGeometry(), driverId);
        route.setDescription(variation.getDescription());
        route.setDistanceKm(variation.getDistanceKm());
        route.setIsActive(true);
        route.setIsPublished(true);
        route.setMaxDeviationMeters(1000);
        
        // Add virtual stops at start and end
        addVirtualStopsFromVariation(route, variation);
        
        return route;
    }
    
    /**
     * Add virtual stops to route
     */
    private void addVirtualStopsFromVariation(Route route, RouteVariation variation) {
        Coordinate[] coords = variation.getGeometry().getCoordinates();
        
        if (coords.length < 2) return;
        
        // Start stop
        Point startPoint = geometryFactory.createPoint(coords[0]);
        startPoint.setSRID(4326);
        
        String startName = variation.getDirection() == CommuteDirection.TO_WORK
            ? "Home" : "Work";
        
        VirtualStop startStop = new VirtualStop(startName, startPoint, route, 0);
        startStop.setTimeOffsetMinutes(0);
        route.addVirtualStop(startStop);
        
        // End stop
        Point endPoint = geometryFactory.createPoint(coords[coords.length - 1]);
        endPoint.setSRID(4326);
        
        String endName = variation.getDirection() == CommuteDirection.TO_WORK
            ? "Work" : "Home";
        
        VirtualStop endStop = new VirtualStop(endName, endPoint, route, 1);
        endStop.setTimeOffsetMinutes(variation.getDurationMinutes());
        route.addVirtualStop(endStop);
    }
    
    // ==================== GET ROUTE VARIATIONS ====================
    
    /**
     * Get all route variations for a driver
     */
    public List<RouteVariationDTO> getRouteVariations(UUID driverId) {
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException("No commute found"));
        
        List<RouteVariation> variations = variationRepository.findByCommuteId(commute.getId());
        
        return variations.stream()
            .map(this::toVariationDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Select a route variation as preferred
     */
    @Transactional
    public void selectPreferredRoute(UUID driverId, UUID variationId) {
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException("No commute found"));
        
        RouteVariation variation = variationRepository.findByIdOptional(variationId)
            .orElseThrow(() -> new IllegalArgumentException("Route variation not found"));
        
        if (!variation.getCommute().getId().equals(commute.getId())) {
            throw new IllegalArgumentException("Route variation does not belong to this driver");
        }
        
        variationRepository.setPreferred(
            variationId,
            commute.getId(),
            variation.getDirection()
        );
        
        Log.info(String.format("Set preferred route: %s for %s",
            variation.getName(), variation.getDirection()));
    }
    
    // ==================== HELPER METHODS ====================
    
    private Point createPoint(double longitude, double latitude) {
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
    
    private double calculateDistance(Point p1, Point p2) {
        double distance = p1.distance(p2) * 111.0;
        return Math.round(distance * 100.0) / 100.0;
    }
    
    private CommuteResponse toCommuteResponse(DriverCommute commute) {
        return new CommuteResponse(
            commute.getDriver().getId().toString(),
            commute.getHomeAddress(),
            commute.getHomeLocation().getY(),
            commute.getHomeLocation().getX(),
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
    
    private RouteVariationDTO toVariationDTO(RouteVariation variation) {
        return new RouteVariationDTO(
            variation.getId().toString(),
            variation.getName(),
            variation.getDescription(),
            variation.getDirection().name(),
            variation.getDistanceKm(),
            variation.getDurationMinutes(),
            variation.getRouteSummary(),
            variation.getIsPreferred(),
            variation.getEncodedPolyline()
        );
    }
    
    // ==================== DTOs ====================
    
    public record CapacityUpdateResponse(
        String driverId,
        int newCapacity,
        int oldCapacity,
        String message
    ) {}
    
    public record RouteVariationDTO(
        String id,
        String name,
        String description,
        String direction,
        Double distanceKm,
        Integer durationMinutes,
        String routeSummary,
        Boolean isPreferred,
        String encodedPolyline
    ) {}
}