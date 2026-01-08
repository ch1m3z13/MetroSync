-- V4__Driver_Commute_Schema.sql
-- Add driver commute table for storing home/work addresses
-- Place in src/main/resources/db/migration/

-- ==================== Driver Commutes Table ====================
CREATE TABLE driver_commutes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    
    -- Home location
    home_address VARCHAR(500) NOT NULL,
    home_location geometry(Point, 4326) NOT NULL,
    
    -- Work location
    work_address VARCHAR(500) NOT NULL,
    work_location geometry(Point, 4326) NOT NULL,
    
    -- Schedule
    departure_time TIME NOT NULL,  -- Time leaving home
    return_time TIME NOT NULL,     -- Time leaving work
    
    -- Capacity
    capacity INTEGER NOT NULL CHECK (capacity >= 1 AND capacity <= 20),
    
    -- Calculated distance
    commute_distance_km DOUBLE PRECISION,
    
    -- Status
    is_active BOOLEAN NOT NULL DEFAULT true,
    
    -- Standard audit fields
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== Indexes ====================

-- Unique index on driver (one commute per driver)
CREATE UNIQUE INDEX idx_commute_driver ON driver_commutes(driver_id);

-- Spatial indexes for geospatial queries
CREATE INDEX idx_commute_home_location ON driver_commutes USING GIST(home_location);
CREATE INDEX idx_commute_work_location ON driver_commutes USING GIST(work_location);

-- Index for finding active commutes
CREATE INDEX idx_commute_active ON driver_commutes(is_active) 
    WHERE is_active = true;

-- ==================== Triggers ====================

-- Update timestamp trigger
CREATE TRIGGER driver_commutes_updated_at
    BEFORE UPDATE ON driver_commutes
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Auto-calculate commute distance
CREATE OR REPLACE FUNCTION calculate_commute_distance()
RETURNS TRIGGER AS $$
BEGIN
    -- Calculate straight-line distance between home and work
    NEW.commute_distance_km = ROUND(
        (ST_Distance(
            NEW.home_location::geography,
            NEW.work_location::geography
        ) / 1000)::numeric,
        2
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER commute_calculate_distance
    BEFORE INSERT OR UPDATE ON driver_commutes
    FOR EACH ROW
    WHEN (NEW.home_location IS NOT NULL AND NEW.work_location IS NOT NULL)
    EXECUTE FUNCTION calculate_commute_distance();

-- ==================== Helper Functions ====================

/**
 * Find drivers commuting near a location
 * Useful for matching riders with nearby drivers
 */
CREATE OR REPLACE FUNCTION find_drivers_commuting_near(
    p_longitude DOUBLE PRECISION,
    p_latitude DOUBLE PRECISION,
    p_radius_meters DOUBLE PRECISION DEFAULT 1000,
    p_time_of_day TIME DEFAULT CURRENT_TIME
)
RETURNS TABLE (
    driver_id UUID,
    driver_name VARCHAR,
    home_address VARCHAR,
    work_address VARCHAR,
    distance_to_route_meters DOUBLE PRECISION,
    commute_direction VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    -- Find drivers whose home OR work is near the search point
    SELECT 
        dc.driver_id,
        u.full_name as driver_name,
        dc.home_address,
        dc.work_address,
        LEAST(
            ST_Distance(
                dc.home_location::geography,
                ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326)::geography
            ),
            ST_Distance(
                dc.work_location::geography,
                ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326)::geography
            )
        ) as distance,
        CASE 
            WHEN p_time_of_day < '12:00:00' THEN 'TO_WORK'
            ELSE 'TO_HOME'
        END as direction
    FROM driver_commutes dc
    JOIN users u ON dc.driver_id = u.id
    WHERE dc.is_active = true
    AND u.is_active = true
    AND u.driver_status = 'ONLINE'
    AND (
        -- Home is near search point
        ST_DWithin(
            dc.home_location::geography,
            ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326)::geography,
            p_radius_meters
        )
        OR
        -- Work is near search point
        ST_DWithin(
            dc.work_location::geography,
            ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326)::geography,
            p_radius_meters
        )
    )
    ORDER BY distance ASC;
END;
$$ LANGUAGE plpgsql;

-- ==================== Sample Data (Optional) ====================

-- Insert sample commutes for existing demo drivers
DO $$
DECLARE
    driver1_id UUID;
    driver2_id UUID;
BEGIN
    -- Get driver IDs
    SELECT id INTO driver1_id FROM users WHERE username = 'demo_driver1' LIMIT 1;
    SELECT id INTO driver2_id FROM users WHERE username = 'demo_driver2' LIMIT 1;
    
    -- Only insert if drivers exist
    IF driver1_id IS NOT NULL THEN
        INSERT INTO driver_commutes (
            driver_id,
            home_address,
            home_location,
            work_address,
            work_location,
            departure_time,
            return_time,
            capacity
        ) VALUES (
            driver1_id,
            'Gwarinpa Estate, Abuja',
            ST_GeomFromText('POINT(7.4124 9.1108)', 4326),
            'Central Business District, Abuja',
            ST_GeomFromText('POINT(7.4905 9.0574)', 4326),
            '07:30:00',
            '17:30:00',
            4
        );
    END IF;
    
    IF driver2_id IS NOT NULL THEN
        INSERT INTO driver_commutes (
            driver_id,
            home_address,
            home_location,
            work_address,
            work_location,
            departure_time,
            return_time,
            capacity
        ) VALUES (
            driver2_id,
            'Kubwa, Abuja',
            ST_GeomFromText('POINT(7.3386 9.0965)', 4326),
            'Wuse Zone 5, Abuja',
            ST_GeomFromText('POINT(7.4935 9.0625)', 4326),
            '08:00:00',
            '18:00:00',
            6
        );
    END IF;
END $$;

-- ==================== Statistics View ====================

-- View for commute analytics
CREATE VIEW commute_statistics AS
SELECT 
    COUNT(*) as total_commutes,
    COUNT(*) FILTER (WHERE is_active = true) as active_commutes,
    AVG(commute_distance_km) as avg_distance_km,
    MIN(commute_distance_km) as min_distance_km,
    MAX(commute_distance_km) as max_distance_km,
    AVG(capacity) as avg_capacity
FROM driver_commutes;

-- ==================== Validation Constraints ====================

-- Ensure departure and return times are logical
ALTER TABLE driver_commutes 
ADD CONSTRAINT chk_times_logical 
CHECK (return_time > departure_time);

-- Ensure home and work are not the same location
-- (Using a buffer of 100 meters to allow for address variations)
CREATE OR REPLACE FUNCTION validate_distinct_locations()
RETURNS TRIGGER AS $$
BEGIN
    IF ST_DWithin(
        NEW.home_location::geography,
        NEW.work_location::geography,
        100  -- 100 meters
    ) THEN
        RAISE EXCEPTION 'Home and work locations must be at least 100 meters apart';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER commute_validate_locations
    BEFORE INSERT OR UPDATE ON driver_commutes
    FOR EACH ROW
    EXECUTE FUNCTION validate_distinct_locations();