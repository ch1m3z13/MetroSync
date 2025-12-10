-- V1__Initial_Schema.sql
-- Initial database schema with PostGIS support
-- Place in src/main/resources/db/migration/

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- Verify PostGIS installation
SELECT PostGIS_version();

-- ==================== Routes Table ====================
CREATE TABLE routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    geometry geometry(LineString, 4326) NOT NULL,
    distance_km NUMERIC(10, 2),
    driver_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_published BOOLEAN NOT NULL DEFAULT false,
    max_deviation_meters INTEGER DEFAULT 500,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Spatial index on geometry (critical for performance)
CREATE INDEX idx_route_geometry ON routes USING GIST(geometry);

-- Standard indexes
CREATE INDEX idx_route_driver ON routes(driver_id);
CREATE INDEX idx_route_active ON routes(is_active) WHERE is_active = true;
CREATE INDEX idx_route_published ON routes(is_published) WHERE is_published = true;

-- Composite index for common queries
CREATE INDEX idx_route_active_published ON routes(is_active, is_published) 
    WHERE is_active = true AND is_published = true;

-- ==================== Virtual Stops Table ====================
CREATE TABLE virtual_stops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    location geometry(Point, 4326) NOT NULL,
    route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    sequence_order INTEGER NOT NULL,
    time_offset_minutes INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ensure sequence_order is unique per route
    CONSTRAINT uq_virtual_stop_sequence UNIQUE (route_id, sequence_order)
);

-- Spatial index on location
CREATE INDEX idx_virtual_stop_location ON virtual_stops USING GIST(location);

-- Foreign key indexes
CREATE INDEX idx_virtual_stop_route ON virtual_stops(route_id);
CREATE INDEX idx_virtual_stop_sequence ON virtual_stops(route_id, sequence_order);

-- ==================== Triggers ====================

-- Update timestamp trigger function
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply timestamp triggers
CREATE TRIGGER routes_updated_at
    BEFORE UPDATE ON routes
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER virtual_stops_updated_at
    BEFORE UPDATE ON virtual_stops
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Auto-calculate route distance on insert/update
CREATE OR REPLACE FUNCTION calculate_route_distance()
RETURNS TRIGGER AS $$
BEGIN
    -- Calculate distance in kilometers using geography type
    NEW.distance_km = ROUND(
        (ST_Length(NEW.geometry::geography) / 1000)::numeric, 
        2
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER routes_calculate_distance
    BEFORE INSERT OR UPDATE ON routes
    FOR EACH ROW
    WHEN (NEW.geometry IS NOT NULL)
    EXECUTE FUNCTION calculate_route_distance();

-- ==================== Helper Functions ====================

-- Function to find routes within distance of a point
CREATE OR REPLACE FUNCTION find_routes_near_point(
    p_longitude DOUBLE PRECISION,
    p_latitude DOUBLE PRECISION,
    p_radius_meters DOUBLE PRECISION DEFAULT 500
)
RETURNS TABLE (
    route_id UUID,
    route_name VARCHAR,
    distance_meters DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        r.id,
        r.name,
        ST_Distance(
            r.geometry::geography,
            ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326)::geography
        ) as distance
    FROM routes r
    WHERE r.is_active = true
    AND r.is_published = true
    AND ST_DWithin(
        r.geometry::geography,
        ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326)::geography,
        p_radius_meters
    )
    ORDER BY distance ASC;
END;
$$ LANGUAGE plpgsql;

-- Function to get closest point on route
CREATE OR REPLACE FUNCTION get_closest_point_on_route(
    p_route_id UUID,
    p_longitude DOUBLE PRECISION,
    p_latitude DOUBLE PRECISION
)
RETURNS geometry AS $$
DECLARE
    closest_point geometry;
BEGIN
    SELECT ST_ClosestPoint(
        r.geometry,
        ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326)
    ) INTO closest_point
    FROM routes r
    WHERE r.id = p_route_id;
    
    RETURN closest_point;
END;
$$ LANGUAGE plpgsql;

-- ==================== Sample Data for Testing ====================

-- Example: Route from Central Area to Maitama (Abuja)
INSERT INTO routes (name, description, geometry, driver_id, is_published) VALUES (
    'Central Area to Maitama',
    'Daily commute route through central Abuja',
    ST_GeomFromText(
        'LINESTRING(7.4905 9.0574, 7.4920 9.0600, 7.4935 9.0625, 7.4950 9.0765)', 
        4326
    ),
    gen_random_uuid(),
    true
);

-- Example: Virtual stops along the route
WITH route AS (
    SELECT id FROM routes WHERE name = 'Central Area to Maitama' LIMIT 1
)
INSERT INTO virtual_stops (name, location, route_id, sequence_order, time_offset_minutes)
SELECT 
    'Central Business District',
    ST_GeomFromText('POINT(7.4905 9.0574)', 4326),
    route.id,
    0,
    0
FROM route
UNION ALL
SELECT 
    'Area 3 Junction',
    ST_GeomFromText('POINT(7.4920 9.0600)', 4326),
    route.id,
    1,
    5
FROM route
UNION ALL
SELECT 
    'Wuse Market',
    ST_GeomFromText('POINT(7.4935 9.0625)', 4326),
    route.id,
    2,
    10
FROM route
UNION ALL
SELECT 
    'Maitama District',
    ST_GeomFromText('POINT(7.4950 9.0765)', 4326),
    route.id,
    3,
    15
FROM route;

-- ==================== Performance Views ====================

-- Materialized view for frequently accessed route summaries
CREATE MATERIALIZED VIEW route_summaries AS
SELECT 
    r.id,
    r.name,
    r.distance_km,
    r.driver_id,
    COUNT(vs.id) as stop_count,
    ST_AsGeoJSON(r.geometry)::json as geometry_geojson,
    ST_AsText(ST_StartPoint(r.geometry)) as start_point_wkt,
    ST_AsText(ST_EndPoint(r.geometry)) as end_point_wkt
FROM routes r
LEFT JOIN virtual_stops vs ON vs.route_id = r.id AND vs.is_active = true
WHERE r.is_active = true
GROUP BY r.id, r.name, r.distance_km, r.driver_id, r.geometry;

CREATE INDEX idx_route_summaries_id ON route_summaries(id);