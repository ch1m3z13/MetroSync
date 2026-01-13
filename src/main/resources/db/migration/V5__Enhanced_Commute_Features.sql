-- V5__Enhanced_Commute_Features.sql
-- Add route variations table for multiple route options
-- Place in src/main/resources/db/migration/

-- ==================== Route Variations Table ====================
CREATE TABLE route_variations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    commute_id UUID NOT NULL REFERENCES driver_commutes(id) ON DELETE CASCADE,
    direction VARCHAR(20) NOT NULL,  -- TO_WORK or TO_HOME
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    
    -- Actual road geometry from Google Directions API
    geometry geometry(LineString, 4326) NOT NULL,
    
    -- Google's encoded polyline (for efficient storage/transmission)
    encoded_polyline TEXT,
    
    -- Actual road distance and duration
    distance_km DOUBLE PRECISION,
    duration_minutes INTEGER,
    
    -- Preferences
    is_preferred BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    
    -- Route summary from Google (e.g., "Via I-95 N")
    route_summary VARCHAR(200),
    
    -- Standard audit fields
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_direction CHECK (direction IN ('TO_WORK', 'TO_HOME'))
);

-- ==================== Indexes ====================

-- Index on commute for fast lookups
CREATE INDEX idx_variation_commute ON route_variations(commute_id);

-- Index on direction for filtering
CREATE INDEX idx_variation_direction ON route_variations(direction);

-- Index on preferred routes
CREATE INDEX idx_variation_preferred ON route_variations(is_preferred) 
    WHERE is_preferred = true;

-- Composite index for common query (commute + direction + preferred)
CREATE INDEX idx_variation_lookup ON route_variations(commute_id, direction, is_preferred);

-- Spatial index on geometry
CREATE INDEX idx_variation_geometry ON route_variations USING GIST(geometry);

-- ==================== Triggers ====================

-- Update timestamp trigger
CREATE TRIGGER route_variations_updated_at
    BEFORE UPDATE ON route_variations
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- ==================== Constraints ====================

-- Ensure only one preferred route per direction per commute
CREATE UNIQUE INDEX idx_one_preferred_per_direction 
ON route_variations(commute_id, direction) 
WHERE is_preferred = true;

-- ==================== Helper Functions ====================

/**
 * Get all route variations for a commute
 */
CREATE OR REPLACE FUNCTION get_route_variations(p_commute_id UUID)
RETURNS TABLE (
    variation_id UUID,
    direction VARCHAR,
    name VARCHAR,
    distance_km DOUBLE PRECISION,
    duration_minutes INTEGER,
    is_preferred BOOLEAN,
    route_summary VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        rv.id as variation_id,
        rv.direction,
        rv.name,
        rv.distance_km,
        rv.duration_minutes,
        rv.is_preferred,
        rv.route_summary
    FROM route_variations rv
    WHERE rv.commute_id = p_commute_id
    AND rv.is_active = true
    ORDER BY rv.direction, rv.is_preferred DESC, rv.duration_minutes ASC;
END;
$$ LANGUAGE plpgsql;

/**
 * Get preferred route for a direction
 */
CREATE OR REPLACE FUNCTION get_preferred_route(
    p_commute_id UUID,
    p_direction VARCHAR
)
RETURNS TABLE (
    variation_id UUID,
    name VARCHAR,
    geometry geometry,
    distance_km DOUBLE PRECISION,
    duration_minutes INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        rv.id as variation_id,
        rv.name,
        rv.geometry,
        rv.distance_km,
        rv.duration_minutes
    FROM route_variations rv
    WHERE rv.commute_id = p_commute_id
    AND rv.direction = p_direction
    AND rv.is_preferred = true
    AND rv.is_active = true
    LIMIT 1;
END;
$$ LANGUAGE plpgsql;

/**
 * Set a route as preferred (automatically unsets others)
 */
CREATE OR REPLACE FUNCTION set_preferred_route(
    p_variation_id UUID
)
RETURNS VOID AS $$
DECLARE
    v_commute_id UUID;
    v_direction VARCHAR;
BEGIN
    -- Get commute_id and direction for this variation
    SELECT commute_id, direction INTO v_commute_id, v_direction
    FROM route_variations
    WHERE id = p_variation_id;
    
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Route variation not found';
    END IF;
    
    -- Unset all preferred routes for this commute + direction
    UPDATE route_variations
    SET is_preferred = false
    WHERE commute_id = v_commute_id
    AND direction = v_direction;
    
    -- Set this one as preferred
    UPDATE route_variations
    SET is_preferred = true
    WHERE id = p_variation_id;
END;
$$ LANGUAGE plpgsql;

-- ==================== Statistics View ====================

/**
 * View for route variation statistics
 */
CREATE VIEW route_variation_stats AS
SELECT 
    dc.driver_id,
    u.full_name as driver_name,
    COUNT(*) FILTER (WHERE rv.direction = 'TO_WORK') as to_work_routes,
    COUNT(*) FILTER (WHERE rv.direction = 'TO_HOME') as to_home_routes,
    AVG(rv.distance_km) as avg_distance_km,
    AVG(rv.duration_minutes) as avg_duration_minutes,
    COUNT(*) FILTER (WHERE rv.is_preferred = true) as preferred_count
FROM driver_commutes dc
JOIN users u ON dc.driver_id = u.id
LEFT JOIN route_variations rv ON rv.commute_id = dc.id AND rv.is_active = true
GROUP BY dc.driver_id, u.full_name;

-- ==================== Sample Data (Optional) ====================

-- Generate sample route variations for existing demo drivers
DO $$
DECLARE
    commute_record RECORD;
BEGIN
    -- Loop through existing commutes
    FOR commute_record IN 
        SELECT id, home_location, work_location, driver_id 
        FROM driver_commutes 
        WHERE is_active = true
        LIMIT 2  -- Just demo drivers
    LOOP
        -- Create TO_WORK variations
        -- Variation 1: Fast route (straight line for demo)
        INSERT INTO route_variations (
            commute_id, direction, name, description,
            geometry, distance_km, duration_minutes, is_preferred
        ) VALUES (
            commute_record.id,
            'TO_WORK',
            'Recommended Route',
            'Fastest route based on current traffic',
            ST_MakeLine(commute_record.home_location, commute_record.work_location),
            ST_Distance(
                commute_record.home_location::geography,
                commute_record.work_location::geography
            ) / 1000.0,
            ROUND((ST_Distance(
                commute_record.home_location::geography,
                commute_record.work_location::geography
            ) / 1000.0) * 2),  -- Assume 30 km/h average
            true  -- This is preferred
        );
        
        -- Variation 2: Alternative route (slightly longer)
        INSERT INTO route_variations (
            commute_id, direction, name, description,
            geometry, distance_km, duration_minutes, is_preferred
        ) VALUES (
            commute_record.id,
            'TO_WORK',
            'Alternative Route',
            'Scenic route avoiding highways',
            ST_MakeLine(commute_record.home_location, commute_record.work_location),
            ST_Distance(
                commute_record.home_location::geography,
                commute_record.work_location::geography
            ) / 1000.0 * 1.15,  -- 15% longer
            ROUND((ST_Distance(
                commute_record.home_location::geography,
                commute_record.work_location::geography
            ) / 1000.0) * 2.3),
            false
        );
        
        -- Create TO_HOME variations
        INSERT INTO route_variations (
            commute_id, direction, name, description,
            geometry, distance_km, duration_minutes, is_preferred
        ) VALUES (
            commute_record.id,
            'TO_HOME',
            'Recommended Route',
            'Fastest route for evening commute',
            ST_MakeLine(commute_record.work_location, commute_record.home_location),
            ST_Distance(
                commute_record.home_location::geography,
                commute_record.work_location::geography
            ) / 1000.0,
            ROUND((ST_Distance(
                commute_record.home_location::geography,
                commute_record.work_location::geography
            ) / 1000.0) * 2),
            true
        );
        
        INSERT INTO route_variations (
            commute_id, direction, name, description,
            geometry, distance_km, duration_minutes, is_preferred
        ) VALUES (
            commute_record.id,
            'TO_HOME',
            'Traffic-Free Route',
            'Avoids main roads during rush hour',
            ST_MakeLine(commute_record.work_location, commute_record.home_location),
            ST_Distance(
                commute_record.home_location::geography,
                commute_record.work_location::geography
            ) / 1000.0 * 1.2,
            ROUND((ST_Distance(
                commute_record.home_location::geography,
                commute_record.work_location::geography
            ) / 1000.0) * 2.4),
            false
        );
        
    END LOOP;
END $$;

-- ==================== Performance Monitoring ====================

-- Index for monitoring route variation usage
CREATE INDEX idx_variation_created_at ON route_variations(created_at);

-- View for most popular routes
CREATE VIEW popular_route_variations AS
SELECT 
    rv.name,
    rv.direction,
    rv.route_summary,
    COUNT(DISTINCT rv.commute_id) as driver_count,
    AVG(rv.distance_km) as avg_distance,
    AVG(rv.duration_minutes) as avg_duration
FROM route_variations rv
WHERE rv.is_active = true
GROUP BY rv.name, rv.direction, rv.route_summary
ORDER BY driver_count DESC;

-- ==================== Validation ====================

-- Ensure route variation belongs to an active commute
ALTER TABLE route_variations 
ADD CONSTRAINT fk_active_commute 
FOREIGN KEY (commute_id) 
REFERENCES driver_commutes(id);

-- Add comment for documentation
COMMENT ON TABLE route_variations IS 
'Stores multiple route alternatives for driver commutes. 
Generated using Google Directions API with real road geometry.
Each commute can have multiple variations per direction (TO_WORK, TO_HOME).';

COMMENT ON COLUMN route_variations.encoded_polyline IS 
'Google Maps encoded polyline format for efficient storage and transmission to frontend.';

COMMENT ON COLUMN route_variations.is_preferred IS 
'Only one route per direction can be marked as preferred. 
This is the route that will be activated by default.';