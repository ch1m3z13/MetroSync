-- ==================== DATABASE INDEXES FOR PERFORMANCE ====================
-- Performance optimization indexes for MetroSync carpooling platform
-- Run this migration after V7__Align_With_Spec.sql

-- ==================== USERS TABLE ====================

-- Email lookup (login) - CRITICAL
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Phone number lookup
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone_number);

-- Active users filter
CREATE INDEX IF NOT EXISTS idx_users_active ON users(is_active) WHERE is_active = true;

-- Driver status (for online driver queries)
CREATE INDEX IF NOT EXISTS idx_users_driver_status ON users(driver_status) WHERE driver_status = 'ONLINE';

-- Geospatial index on current location (for nearby driver search)
CREATE INDEX IF NOT EXISTS idx_users_current_location_gist ON users USING GIST(current_location);


-- ==================== ROUTES TABLE ====================

-- Driver's routes lookup - CRITICAL
CREATE INDEX IF NOT EXISTS idx_routes_driver_id ON routes(driver_id);

-- Active routes filter
CREATE INDEX IF NOT EXISTS idx_routes_is_active ON routes(is_active) WHERE is_active = true;

-- Published routes (passenger search)
CREATE INDEX IF NOT EXISTS idx_routes_is_published ON routes(is_published) WHERE is_published = true;

-- Compound index for active published routes
CREATE INDEX IF NOT EXISTS idx_routes_active_published ON routes(is_active, is_published) 
    WHERE is_active = true AND is_published = true;

-- Geospatial index on route geometry (for proximity searches) - CRITICAL
CREATE INDEX IF NOT EXISTS idx_routes_geometry_gist ON routes USING GIST(geometry);

-- Combined status check (common query pattern)
CREATE INDEX IF NOT EXISTS idx_routes_status_driver ON routes(is_active, is_published, driver_id);


-- ==================== ROUTE_VARIATIONS TABLE ====================

-- Commute variations lookup
CREATE INDEX IF NOT EXISTS idx_route_variations_commute ON route_variations(commute_id);

-- Direction filter
CREATE INDEX IF NOT EXISTS idx_route_variations_direction ON route_variations(direction);

-- Active variations
CREATE INDEX IF NOT EXISTS idx_route_variations_active ON route_variations(is_active) WHERE is_active = true;

-- Preferred route lookup
CREATE INDEX IF NOT EXISTS idx_route_variations_preferred ON route_variations(commute_id, direction, is_preferred) 
    WHERE is_preferred = true;

-- Geospatial index on variation geometry
CREATE INDEX IF NOT EXISTS idx_route_variations_geometry_gist ON route_variations USING GIST(geometry);


-- ==================== DRIVER_COMMUTES TABLE ====================

-- Driver commute lookup (one-to-one) - CRITICAL
CREATE UNIQUE INDEX IF NOT EXISTS idx_driver_commutes_driver_id ON driver_commutes(driver_id);

-- Active commutes
CREATE INDEX IF NOT EXISTS idx_driver_commutes_active ON driver_commutes(is_active) WHERE is_active = true;

-- Geospatial indexes
CREATE INDEX IF NOT EXISTS idx_driver_commutes_home_gist ON driver_commutes USING GIST(home_location);
CREATE INDEX IF NOT EXISTS idx_driver_commutes_work_gist ON driver_commutes USING GIST(work_location);


-- ==================== BOOKINGS TABLE ====================

-- Passenger's bookings - CRITICAL
CREATE INDEX IF NOT EXISTS idx_bookings_rider_id ON bookings(rider_id);

-- Route's bookings - CRITICAL (driver queries)
CREATE INDEX IF NOT EXISTS idx_bookings_route_id ON bookings(route_id);

-- Status filter
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);

-- Scheduled time (for upcoming bookings)
CREATE INDEX IF NOT EXISTS idx_bookings_scheduled_time ON bookings(scheduled_pickup_time);

-- Compound: Passenger + Status (optimized passenger queries) - VERY IMPORTANT
CREATE INDEX IF NOT EXISTS idx_bookings_rider_status ON bookings(rider_id, status);

-- Compound: Route + Status (optimized driver queries) - VERY IMPORTANT
CREATE INDEX IF NOT EXISTS idx_bookings_route_status ON bookings(route_id, status);

-- Active bookings for capacity checks
CREATE INDEX IF NOT EXISTS idx_bookings_route_active ON bookings(route_id, status) 
    WHERE status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS');

-- Completed bookings for analytics
CREATE INDEX IF NOT EXISTS idx_bookings_completed ON bookings(completed_at) 
    WHERE status = 'COMPLETED';

-- Geospatial indexes for pickup/dropoff
CREATE INDEX IF NOT EXISTS idx_bookings_pickup_gist ON bookings USING GIST(pickup_location);
CREATE INDEX IF NOT EXISTS idx_bookings_dropoff_gist ON bookings USING GIST(dropoff_location);

-- Reference number lookup (unique identifier)
CREATE UNIQUE INDEX IF NOT EXISTS idx_bookings_reference ON bookings(reference_number);


-- ==================== VIRTUAL_STOPS TABLE ====================

-- Route's stops lookup
CREATE INDEX IF NOT EXISTS idx_virtual_stops_route ON virtual_stops(route_id);

-- Sequence order (for ordering stops)
CREATE INDEX IF NOT EXISTS idx_virtual_stops_sequence ON virtual_stops(route_id, sequence_order);

-- Active stops
CREATE INDEX IF NOT EXISTS idx_virtual_stops_active ON virtual_stops(is_active) WHERE is_active = true;

-- Geospatial index for proximity searches
CREATE INDEX IF NOT EXISTS idx_virtual_stops_location_gist ON virtual_stops USING GIST(location);


-- ==================== LOCATION_TRACKING TABLE (if implemented) ====================

-- Route replay queries
CREATE INDEX IF NOT EXISTS idx_location_tracking_route ON location_tracking(route_id);

-- Add the column first to the 'location_tracking' table
ALTER TABLE location_tracking ADD COLUMN tracked_at TIMESTAMP;

-- (Optional) Backfill data from the existing timestamp column so the index isn't empty
UPDATE location_tracking SET tracked_at = timestamp WHERE tracked_at IS NULL;

-- Then create the index
CREATE INDEX idx_location_tracking_tracked_at ON location_tracking (tracked_at);

-- Time-based queries and cleanup
CREATE INDEX IF NOT EXISTS idx_location_tracking_timestamp ON location_tracking(tracked_at);

-- Compound for route playback
CREATE INDEX IF NOT EXISTS idx_location_tracking_route_time ON location_tracking(route_id, tracked_at DESC);


-- ==================== VEHICLES TABLE ====================

-- Owner's vehicles
CREATE INDEX IF NOT EXISTS idx_vehicles_owner_id ON vehicles(owner_id);

-- Active vehicles
CREATE INDEX IF NOT EXISTS idx_vehicles_active ON vehicles(is_active) WHERE is_active = true;

-- License plate lookup (unique)
CREATE UNIQUE INDEX IF NOT EXISTS idx_vehicles_license_plate ON vehicles(license_plate);


-- ==================== PERFORMANCE NOTES ====================

/*
 * INDEX USAGE GUIDELINES:
 * 
 * 1. B-tree Indexes (DEFAULT):
 *    - Equality checks: WHERE column = value
 *    - Range queries: WHERE column > value
 *    - Sorting: ORDER BY column
 *    - Pattern matching: WHERE column LIKE 'prefix%'
 * 
 * 2. GIST Indexes (for PostGIS):
 *    - Proximity searches: ST_DWithin(geom, point, distance)
 *    - Contains checks: ST_Contains(geom, point)
 *    - Intersects: ST_Intersects(geom1, geom2)
 * 
 * 3. Partial Indexes (WHERE clause):
 *    - Smaller index size (faster)
 *    - Only index rows you actually query
 *    - Example: WHERE is_active = true
 * 
 * 4. Compound Indexes:
 *    - Must match query WHERE clause order
 *    - Example: Index on (rider_id, status) helps:
 *      - WHERE rider_id = ? AND status = ?  ✅
 *      - WHERE rider_id = ?                  ✅
 *      - WHERE status = ?                    ❌ (won't use index)
 * 
 * MONITORING:
 * 
 * Check index usage:
 *   SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read
 *   FROM pg_stat_user_indexes
 *   ORDER BY idx_scan ASC;
 * 
 * Find missing indexes:
 *   SELECT 
 *     schemaname, tablename, 
 *     seq_scan, seq_tup_read,
 *     idx_scan, idx_tup_fetch
 *   FROM pg_stat_user_tables
 *   WHERE seq_scan > 1000  -- Many sequential scans
 *   AND idx_scan < 100     -- Few index scans
 *   ORDER BY seq_tup_read DESC;
 * 
 * MAINTENANCE:
 * 
 * Rebuild fragmented indexes:
 *   REINDEX INDEX CONCURRENTLY idx_name;
 * 
 * Update statistics (after bulk inserts):
 *   ANALYZE bookings;
 *   ANALYZE routes;
 */

-- ==================== QUERY OPTIMIZATION EXAMPLES ====================

/*
 * OPTIMIZED QUERY PATTERNS:
 * 
 * 1. Find passenger's active bookings:
 *    SELECT * FROM bookings 
 *    WHERE rider_id = ? 
 *    AND status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS')
 *    ORDER BY scheduled_pickup_time ASC;
 *    
 *    Uses: idx_bookings_rider_status
 * 
 * 2. Find driver's route bookings:
 *    SELECT * FROM bookings 
 *    WHERE route_id = ?
 *    AND status = 'CONFIRMED'
 *    ORDER BY scheduled_pickup_time ASC;
 *    
 *    Uses: idx_bookings_route_status
 * 
 * 3. Find nearby drivers:
 *    SELECT * FROM routes r
 *    WHERE is_active = true
 *    AND is_published = true
 *    AND ST_DWithin(
 *        geometry::geography,
 *        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
 *        500  -- meters
 *    );
 *    
 *    Uses: idx_routes_geometry_gist + idx_routes_active_published
 * 
 * 4. Check booking capacity:
 *    SELECT COUNT(*) FROM bookings
 *    WHERE route_id = ?
 *    AND status IN ('CONFIRMED', 'IN_PROGRESS')
 *    AND scheduled_pickup_time::date = ?::date;
 *    
 *    Uses: idx_bookings_route_active
 */