-- V6__Add_Corridor_Matching.sql
-- Corridor-based matching for carpooling (replaces radius-based taxi matching)

-- ==================== CORRIDOR MATCHING FUNCTION ====================

-- Match riders to drivers whose route PASSES BY the pickup AND dropoff locations
-- This is the KEY difference between carpooling and taxi services
CREATE OR REPLACE FUNCTION match_commuter_routes(
    p_pickup_lat DOUBLE PRECISION,
    p_pickup_lng DOUBLE PRECISION,
    p_dropoff_lat DOUBLE PRECISION,
    p_dropoff_lng DOUBLE PRECISION,
    p_tolerance_meters DOUBLE PRECISION DEFAULT 500
)
RETURNS TABLE (
    variation_id UUID,
    driver_id UUID,
    driver_name VARCHAR,
    route_name VARCHAR,
    match_score DOUBLE PRECISION,
    pickup_distance_m DOUBLE PRECISION,
    dropoff_distance_m DOUBLE PRECISION,
    direction VARCHAR,
    estimated_duration_min INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        rv.id as variation_id,
        dc.driver_id,
        u.full_name as driver_name,
        rv.name as route_name,
        -- Score: Lower is better (sum of distances to the line)
        (
            ST_Distance(
                rv.geometry::geography, 
                ST_SetSRID(ST_MakePoint(p_pickup_lng, p_pickup_lat), 4326)::geography
            ) +
            ST_Distance(
                rv.geometry::geography, 
                ST_SetSRID(ST_MakePoint(p_dropoff_lng, p_dropoff_lat), 4326)::geography
            )
        ) as match_score,
        -- Individual distances for UI display
        ST_Distance(
            rv.geometry::geography,
            ST_SetSRID(ST_MakePoint(p_pickup_lng, p_pickup_lat), 4326)::geography
        ) as pickup_distance_m,
        ST_Distance(
            rv.geometry::geography,
            ST_SetSRID(ST_MakePoint(p_dropoff_lng, p_dropoff_lat), 4326)::geography
        ) as dropoff_distance_m,
        rv.direction::VARCHAR as direction,
        rv.duration_minutes as estimated_duration_min
    FROM route_variations rv
    JOIN driver_commutes dc ON rv.commute_id = dc.id
    JOIN users u ON dc.driver_id = u.id
    WHERE 
        rv.is_active = true 
        AND rv.is_preferred = true -- Only match against driver's preferred route
        AND dc.is_active = true
        AND u.driver_status = 'ONLINE' -- Driver must be online
        AND
        -- CRITICAL: Pickup is within tolerance of the route LINE (not just endpoints)
        ST_DWithin(
            rv.geometry::geography,
            ST_SetSRID(ST_MakePoint(p_pickup_lng, p_pickup_lat), 4326)::geography,
            p_tolerance_meters
        )
        AND
        -- CRITICAL: Dropoff is within tolerance of the route LINE
        ST_DWithin(
            rv.geometry::geography,
            ST_SetSRID(ST_MakePoint(p_dropoff_lng, p_dropoff_lat), 4326)::geography,
            p_tolerance_meters
        )
        AND
        -- CRITICAL: Direction matters - Pickup must come BEFORE Dropoff on the route
        -- ST_LineLocatePoint returns fraction (0.0 to 1.0) along the line
        ST_LineLocatePoint(
            rv.geometry, 
            ST_SetSRID(ST_MakePoint(p_pickup_lng, p_pickup_lat), 4326)
        ) < ST_LineLocatePoint(
            rv.geometry, 
            ST_SetSRID(ST_MakePoint(p_dropoff_lng, p_dropoff_lat), 4326)
        )
    ORDER BY match_score ASC
    LIMIT 20;
END;
$$ LANGUAGE plpgsql;

-- ==================== HELPER FUNCTIONS ====================

-- Calculate the fraction (0.0 to 1.0) of where a point projects onto a route
-- Useful for determining pickup/dropoff sequence
CREATE OR REPLACE FUNCTION get_point_fraction_on_route(
    p_route_geometry GEOMETRY(LineString, 4326),
    p_point_lat DOUBLE PRECISION,
    p_point_lng DOUBLE PRECISION
)
RETURNS DOUBLE PRECISION AS $$
BEGIN
    RETURN ST_LineLocatePoint(
        p_route_geometry,
        ST_SetSRID(ST_MakePoint(p_point_lng, p_point_lat), 4326)
    );
END;
$$ LANGUAGE plpgsql;

-- Get the closest point on a route to a given location
CREATE OR REPLACE FUNCTION get_closest_point_on_route(
    p_route_geometry GEOMETRY(LineString, 4326),
    p_point_lat DOUBLE PRECISION,
    p_point_lng DOUBLE PRECISION
)
RETURNS TABLE (
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    distance_meters DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        ST_Y(closest_point) as latitude,
        ST_X(closest_point) as longitude,
        ST_Distance(
            closest_point::geography,
            ST_SetSRID(ST_MakePoint(p_point_lng, p_point_lat), 4326)::geography
        ) as distance_meters
    FROM (
        SELECT ST_ClosestPoint(
            p_route_geometry,
            ST_SetSRID(ST_MakePoint(p_point_lng, p_point_lat), 4326)
        ) as closest_point
    ) sub;
END;
$$ LANGUAGE plpgsql;

-- ==================== BOOKING SEQUENCING HELPER ====================

-- For a given route variation, get all active bookings in sequence order
-- This is used to build the "school bus manifest"
CREATE OR REPLACE FUNCTION get_route_manifest(
    p_variation_id UUID
)
RETURNS TABLE (
    booking_id UUID,
    stop_type VARCHAR,
    sequence_order DOUBLE PRECISION,
    passenger_name VARCHAR,
    passenger_count INTEGER,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    scheduled_time TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    WITH active_bookings AS (
        SELECT 
            b.id as booking_id,
            b.rider_id,
            b.passenger_count,
            b.scheduled_pickup_time,
            ST_Y(b.pickup_location) as pickup_lat,
            ST_X(b.pickup_location) as pickup_lng,
            ST_Y(b.dropoff_location) as dropoff_lat,
            ST_X(b.dropoff_location) as dropoff_lng,
            u.full_name as passenger_name
        FROM bookings b
        JOIN route_variations rv ON b.route_id IN (
            SELECT r.id FROM routes r 
            WHERE r.driver_id = (
                SELECT dc.driver_id FROM driver_commutes dc 
                WHERE dc.id = (
                    SELECT rv2.commute_id FROM route_variations rv2 WHERE rv2.id = p_variation_id
                )
            )
        )
        JOIN users u ON b.rider_id = u.id
        WHERE b.status IN ('CONFIRMED', 'IN_PROGRESS')
    ),
    pickups AS (
        SELECT 
            booking_id,
            'PICKUP'::VARCHAR as stop_type,
            ST_LineLocatePoint(
                rv.geometry,
                ST_SetSRID(ST_MakePoint(ab.pickup_lng, ab.pickup_lat), 4326)
            ) as sequence_order,
            ab.passenger_name,
            ab.passenger_count,
            ab.pickup_lat as latitude,
            ab.pickup_lng as longitude,
            ab.scheduled_time
        FROM active_bookings ab
        CROSS JOIN route_variations rv
        WHERE rv.id = p_variation_id
    ),
    dropoffs AS (
        SELECT 
            booking_id,
            'DROPOFF'::VARCHAR as stop_type,
            ST_LineLocatePoint(
                rv.geometry,
                ST_SetSRID(ST_MakePoint(ab.dropoff_lng, ab.dropoff_lat), 4326)
            ) as sequence_order,
            ab.passenger_name,
            ab.passenger_count,
            ab.dropoff_lat as latitude,
            ab.dropoff_lng as longitude,
            ab.scheduled_time
        FROM active_bookings ab
        CROSS JOIN route_variations rv
        WHERE rv.id = p_variation_id
    )
    SELECT * FROM pickups
    UNION ALL
    SELECT * FROM dropoffs
    ORDER BY sequence_order ASC;
END;
$$ LANGUAGE plpgsql;

-- ==================== INDEXES FOR PERFORMANCE ====================

-- Spatial index on route_variations geometry (if not exists)
CREATE INDEX IF NOT EXISTS idx_route_variations_geometry 
ON route_variations USING GIST (geometry);

-- Index on active/preferred routes
CREATE INDEX IF NOT EXISTS idx_route_variations_active_preferred 
ON route_variations (is_active, is_preferred) 
WHERE is_active = true AND is_preferred = true;

-- Index on driver commutes active status
CREATE INDEX IF NOT EXISTS idx_driver_commutes_active 
ON driver_commutes (is_active) 
WHERE is_active = true;

-- Composite index for booking status queries
CREATE INDEX IF NOT EXISTS idx_bookings_status_route 
ON bookings (status, route_id) 
WHERE status IN ('CONFIRMED', 'IN_PROGRESS');

-- ==================== COMMENTS ====================

COMMENT ON FUNCTION match_commuter_routes IS 
'Matches riders to drivers whose route corridor passes near both pickup and dropoff. 
This is corridor-based matching (carpooling) NOT radius-based matching (taxi).';

COMMENT ON FUNCTION get_route_manifest IS 
'Returns ordered list of all pickup/dropoff stops for a route variation. 
Used to generate driver manifest (school bus style routing).';

COMMENT ON FUNCTION get_point_fraction_on_route IS 
'Returns 0.0-1.0 indicating where a point projects onto a route line. 
Used for determining stop sequence order.';