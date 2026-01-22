package com.commute.metrosync.service;

import com.commute.metrosync.dto.CommuteDTOs.*;
import com.commute.metrosync.entity.*;
import com.commute.metrosync.repository.*;
import com.commute.metrosync.service.DirectionDetectorService.DetectionResult;
import com.commute.metrosync.service.MapboxDirectionsService.RouteAlternative;
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
 * PRODUCTION-READY Commute Service with REAL Mapbox Integration
 * * Changes from Previous Version:
 * ✅ Uses MapboxDirectionsService for REAL road routes
 * ✅ Generates actual route variations (not straight lines)
 * ✅ Provides encoded polylines for map display
 * ✅ Handles API failures gracefully
 * ✅ FIXED: Handles duplicate key constraints by deactivating old routes
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
    MapboxDirectionsService mapboxDirections;  // ✅ NEW: Real routing service
    
    @Inject
    DirectionDetectorService directionDetector;
    
    private final GeometryFactory geometryFactory = new GeometryFactory();
    
    // ==================== SAVE COMMUTE (NOW WITH REAL ROUTING!) ====================
    
    /**
     * Save commute and generate REAL route variations using Mapbox Directions API
     * * Flow:
     * 1. Save commute information
     * 2. Deactivate OLD routes to prevent DB conflicts
     * 3. Call Mapbox Directions API for TO_WORK routes
     * 4. Call Mapbox Directions API for TO_HOME routes
     * 5. Save all variations to database
     */
    @Transactional
    public CommuteResponse saveCommute(SaveCommuteRequest request) {
        Log.info("Saving enhanced commute with REAL route generation for driver: " + request.driverId());
        
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
        
        // 6. Calculate straight-line distance (for reference)
        double distanceKm = calculateDistance(homeLocation, workLocation);
        commute.setCommuteDistanceKm(distanceKm);
        
        // 7. Save commute first
        commute.setIsActive(true);
        commuteRepository.persist(commute);
        commuteRepository.flush(); // Ensure ID is generated
        
        // 8. ✅ CRITICAL FIX: Deactivate OLD routes before generating new ones
        // This prevents "duplicate key value violates unique constraint" error
        deactivateOldRoutes(commute.getId());
        
        // 9. ✅ GENERATE REAL ROUTE VARIATIONS using Mapbox
        generateRealRouteVariations(commute);
        
        Log.info("Commute saved with REAL route variations from Mapbox");
        
        return toCommuteResponse(commute);
    }

    /**
     * Sets is_active = false for all existing routes of this commute.
     * This ensures the unique index (commute_id, direction) WHERE is_preferred=true
     * does not conflict with the new routes we are about to insert.
     */
    private void deactivateOldRoutes(UUID commuteId) {
        // We set isPreferred = false explicitly to free up the unique index
        // and isActive = false to mark them as historical
        variationRepository.update("isActive = false, isPreferred = false WHERE commute.id = ?1", commuteId);
        
        // Force flush to ensure DB sees the update before we try inserting new active ones
        variationRepository.flush(); 
    }
    
    /**
     * ✅ NEW: Generate REAL route variations using Mapbox Directions API
     */
    private void generateRealRouteVariations(DriverCommute commute) {
        Log.info("Generating REAL route variations using Mapbox Directions API");
        
        try {
            // Generate TO_WORK routes (Home → Work)
            generateVariationsForDirection(
                commute,
                CommuteDirection.TO_WORK,
                commute.getHomeLocation().getY(),  // latitude
                commute.getHomeLocation().getX(),  // longitude
                commute.getWorkLocation().getY(),
                commute.getWorkLocation().getX()
            );
            
            // Generate TO_HOME routes (Work → Home)
            generateVariationsForDirection(
                commute,
                CommuteDirection.TO_HOME,
                commute.getWorkLocation().getY(),
                commute.getWorkLocation().getX(),
                commute.getHomeLocation().getY(),
                commute.getHomeLocation().getX()
            );
            
            Log.info("Successfully generated route variations with Mapbox");
            
        } catch (Exception e) {
            Log.error("Failed to generate route variations, using fallback", e);
            
            // Fallback: Generate simple straight-line routes
            generateSimpleRouteVariations(commute);
        }
    }
    
    /**
     * Generate route variations for a specific direction using Mapbox
     */
    private void generateVariationsForDirection(
            DriverCommute commute,
            CommuteDirection direction,
            double originLat,
            double originLon,
            double destLat,
            double destLon) {
        
        try {
            Log.info(String.format(
                "Fetching %s routes from (%.6f, %.6f) to (%.6f, %.6f)",
                direction, originLat, originLon, destLat, destLon
            ));
            
            // ✅ Call Mapbox Directions API
            List<RouteAlternative> alternatives = mapboxDirections.getRouteAlternatives(
                originLat, originLon, destLat, destLon
            );
            
            if (alternatives.isEmpty()) {
                Log.warn("Mapbox returned no routes, using fallback");
                generateFallbackVariation(commute, direction, originLat, originLon, destLat, destLon);
                return;
            }
            
            // Save each alternative to database
            for (RouteAlternative alternative : alternatives) {
                saveRouteVariation(commute, direction, alternative);
            }
            
            Log.info(String.format("Saved %d route variations for %s", alternatives.size(), direction));
            
        } catch (Exception e) {
            Log.error("Failed to fetch Mapbox routes for " + direction, e);
            generateFallbackVariation(commute, direction, originLat, originLon, destLat, destLon);
        }
    }
    
    /**
     * Save a route alternative to database
     */
    private void saveRouteVariation(
            DriverCommute commute,
            CommuteDirection direction,
            RouteAlternative alternative) {
        
        RouteVariation variation = new RouteVariation(
            commute,
            direction,
            alternative.name(),
            alternative.geometry()
        );
        
        variation.setDescription(alternative.description());
        variation.setDistanceKm(alternative.distanceKm());
        variation.setDurationMinutes(alternative.durationMinutes());
        variation.setEncodedPolyline(alternative.encodedPolyline());
        variation.setRouteSummary(alternative.routeSummary());
        variation.setIsPreferred(alternative.isPreferred());
        variation.setIsActive(true);
        
        variationRepository.persist(variation);
        
        Log.info(String.format(
            "Saved route: %s (%.2f km, %d min, preferred: %s)",
            alternative.name(),
            alternative.distanceKm(),
            alternative.durationMinutes(),
            alternative.isPreferred()
        ));
    }
    
    /**
     * Generate fallback straight-line route when Mapbox fails
     */
    private void generateFallbackVariation(
            DriverCommute commute,
            CommuteDirection direction,
            double originLat,
            double originLon,
            double destLat,
            double destLon) {
        
        try {
            Coordinate[] coords = new Coordinate[]{
                new Coordinate(originLon, originLat),
                new Coordinate(destLon, destLat)
            };
            
            LineString geometry = geometryFactory.createLineString(coords);
            geometry.setSRID(4326);
            
            Point origin = createPoint(originLon, originLat);
            Point dest = createPoint(destLon, destLat);
            double distance = calculateDistance(origin, dest);
            int estimatedDuration = (int) (distance * 2); // Assume 30 km/h
            
            RouteVariation variation = new RouteVariation(
                commute,
                direction,
                "Direct Route (Fallback)",
                geometry
            );
            
            variation.setDescription("Estimated route - actual path may vary");
            variation.setDistanceKm(distance);
            variation.setDurationMinutes(estimatedDuration);
            variation.setEncodedPolyline(""); // No polyline for straight line
            variation.setRouteSummary("Direct connection");
            variation.setIsPreferred(true);
            variation.setIsActive(true);
            
            variationRepository.persist(variation);
            
            Log.info("Saved fallback straight-line route");
            
        } catch (Exception e) {
            Log.error("Failed to create fallback route", e);
        }
    }
    
    /**
     * Legacy fallback method (kept for compatibility)
     */
    private void generateSimpleRouteVariations(DriverCommute commute) {
        Log.info("Generating simple route variations (straight-line fallback)");
        
        generateFallbackVariation(
            commute,
            CommuteDirection.TO_WORK,
            commute.getHomeLocation().getY(),
            commute.getHomeLocation().getX(),
            commute.getWorkLocation().getY(),
            commute.getWorkLocation().getX()
        );
        
        generateFallbackVariation(
            commute,
            CommuteDirection.TO_HOME,
            commute.getWorkLocation().getY(),
            commute.getWorkLocation().getX(),
            commute.getHomeLocation().getY(),
            commute.getHomeLocation().getX()
        );
    }
    
    // ==================== UPDATE CAPACITY ====================
    
    @Transactional
    public CapacityUpdateResponse updateCapacity(UUID driverId, int newCapacity) {
        Log.info(String.format("Updating capacity for driver %s to %d", driverId, newCapacity));
        
        if (newCapacity < 1 || newCapacity > 20) {
            throw new IllegalArgumentException("Capacity must be between 1 and 20");
        }
        
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
    
    // ==================== ACTIVATE COMMUTE ====================
    
    @Transactional
    public ActivateCommuteResponse activateCommuteAuto(UUID driverId) {
        Log.info("Activating commute with auto-detection for driver: " + driverId);
        
        User driver = userRepository.findByIdOptional(driverId)
            .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException("No commute found"));
        
        DetectionResult detection = directionDetector.detectDirection(commute, driver);
        
        Log.info(String.format("Detected direction: %s (confidence: %.2f) - %s",
            detection.direction(), detection.confidence(), detection.reason()));
        
        return activateCommute(driverId, detection.direction(), detection);
    }
    
    @Transactional
    public ActivateCommuteResponse activateCommute(
            UUID driverId,
            CommuteDirection direction) {
        
        return activateCommute(driverId, direction, null);
    }
    
    private ActivateCommuteResponse activateCommute(
            UUID driverId,
            CommuteDirection direction,
            DetectionResult detection) {
        
        Log.info(String.format("Activating commute for driver %s, direction: %s",
            driverId, direction));
        
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException("No commute found"));
        
        RouteVariation preferredVariation = variationRepository
            .findPreferredRoute(commute.getId(), direction)
            .orElseGet(() -> {
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
        
        addVirtualStopsFromVariation(route, variation);
        
        return route;
    }
    
    private void addVirtualStopsFromVariation(Route route, RouteVariation variation) {
        Coordinate[] coords = variation.getGeometry().getCoordinates();
        
        if (coords.length < 2) return;
        
        Point startPoint = geometryFactory.createPoint(coords[0]);
        startPoint.setSRID(4326);
        
        String startName = variation.getDirection() == CommuteDirection.TO_WORK
            ? "Home" : "Work";
        
        VirtualStop startStop = new VirtualStop(startName, startPoint, route, 0);
        startStop.setTimeOffsetMinutes(0);
        route.addVirtualStop(startStop);
        
        Point endPoint = geometryFactory.createPoint(coords[coords.length - 1]);
        endPoint.setSRID(4326);
        
        String endName = variation.getDirection() == CommuteDirection.TO_WORK
            ? "Work" : "Home";
        
        VirtualStop endStop = new VirtualStop(endName, endPoint, route, 1);
        endStop.setTimeOffsetMinutes(variation.getDurationMinutes());
        route.addVirtualStop(endStop);
    }
    
    // ==================== GET ROUTE VARIATIONS ====================
    
    public List<RouteVariationDTO> getRouteVariations(UUID driverId) {
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException("No commute found"));
        
        List<RouteVariation> variations = variationRepository.findByCommuteId(commute.getId());
        
        return variations.stream()
            .map(this::toVariationDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public void selectPreferredRoute(UUID driverId, UUID variationId) {
        DriverCommute commute = commuteRepository.findByDriverId(driverId)
            .orElseThrow(() -> new IllegalArgumentException("No commute found"));
        
        RouteVariation variation = variationRepository.findByIdOptional(variationId)
            .orElseThrow(() -> new IllegalArgumentException("Route variation not found"));
        
        if (!variation.getCommute().getId().equals(commute.getId())) {
            throw new IllegalArgumentException("Route variation does not belong to this driver");
        }
        
        // Deactivate preferred status for other routes in same direction
        // This is handled by a custom repository method or manual update
        variationRepository.update("isPreferred = false WHERE commute.id = ?1 AND direction = ?2", 
            commute.getId(), variation.getDirection());
        variationRepository.flush(); // Ensure update is seen
        
        // Set new preferred
        variation.setIsPreferred(true);
        variationRepository.persist(variation);
        
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
}