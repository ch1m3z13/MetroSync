-- V6__Landmark_Locations.sql
-- Curated landmark locations for Abuja (junctions, estates, landmarks)
-- Reduces reliance on external geocoding APIs during driver signup

-- ==================== Landmark Locations Table ====================
CREATE TABLE landmark_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL, -- 'JUNCTION', 'ESTATE', 'LANDMARK', 'DISTRICT', 'MALL', 'HOSPITAL'
    description VARCHAR(500),
    location geometry(Point, 4326) NOT NULL,
    district VARCHAR(100), -- 'Wuse', 'Maitama', 'Garki', 'Gwarinpa', 'Kubwa', etc.
    is_active BOOLEAN NOT NULL DEFAULT true,
    search_terms TEXT[], -- For fuzzy search: ['wuse market', 'wuse zone 5', 'wuse']
    popularity_score INTEGER DEFAULT 0, -- Track usage frequency
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_category CHECK (category IN (
        'JUNCTION', 'ESTATE', 'LANDMARK', 'DISTRICT', 
        'MALL', 'HOSPITAL', 'GOVERNMENT', 'TRANSPORT', 'OTHER'
    ))
);

-- ==================== Indexes ====================

-- Spatial index for proximity queries
CREATE INDEX idx_landmark_location ON landmark_locations USING GIST(location);

-- Category filtering
CREATE INDEX idx_landmark_category ON landmark_locations(category) WHERE is_active = true;

-- District filtering
CREATE INDEX idx_landmark_district ON landmark_locations(district) WHERE is_active = true;

-- Full-text search on search_terms
CREATE INDEX idx_landmark_search ON landmark_locations USING GIN(search_terms);

-- Full-text search on name
CREATE INDEX idx_landmark_name_search ON landmark_locations 
    USING GIN(to_tsvector('english', name));

-- Popularity sorting
CREATE INDEX idx_landmark_popularity ON landmark_locations(popularity_score DESC) 
    WHERE is_active = true;

-- Composite index for common queries
CREATE INDEX idx_landmark_active_category ON landmark_locations(category, district) 
    WHERE is_active = true;

-- ==================== Triggers ====================

-- Update timestamp trigger
CREATE TRIGGER landmark_locations_updated_at
    BEFORE UPDATE ON landmark_locations
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Auto-populate search_terms from name if not provided
CREATE OR REPLACE FUNCTION generate_search_terms()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.search_terms IS NULL OR array_length(NEW.search_terms, 1) IS NULL THEN
        -- Generate basic search terms from name (lowercase, no punctuation)
        NEW.search_terms = ARRAY[
            lower(NEW.name),
            lower(regexp_replace(NEW.name, '[^a-zA-Z0-9\s]', '', 'g'))
        ];
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER landmark_generate_search_terms
    BEFORE INSERT OR UPDATE ON landmark_locations
    FOR EACH ROW
    EXECUTE FUNCTION generate_search_terms();

-- ==================== Helper Functions ====================

/**
 * Find landmarks near a point
 */
CREATE OR REPLACE FUNCTION find_landmarks_near(
    p_longitude DOUBLE PRECISION,
    p_latitude DOUBLE PRECISION,
    p_radius_meters DOUBLE PRECISION DEFAULT 1000,
    p_category VARCHAR DEFAULT NULL,
    p_limit INTEGER DEFAULT 10
)
RETURNS TABLE (
    landmark_id UUID,
    landmark_name VARCHAR,
    category VARCHAR,
    district VARCHAR,
    distance_meters DOUBLE PRECISION,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        l.id,
        l.name,
        l.category,
        l.district,
        ST_Distance(
            l.location::geography,
            ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326)::geography
        ) as distance,
        ST_Y(l.location) as lat,
        ST_X(l.location) as lon
    FROM landmark_locations l
    WHERE l.is_active = true
    AND (p_category IS NULL OR l.category = p_category)
    AND ST_DWithin(
        l.location::geography,
        ST_SetSRID(ST_MakePoint(p_longitude, p_latitude), 4326)::geography,
        p_radius_meters
    )
    ORDER BY distance ASC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql;

/**
 * Search landmarks by name or search terms
 */
CREATE OR REPLACE FUNCTION search_landmarks(
    p_search_text VARCHAR,
    p_category VARCHAR DEFAULT NULL,
    p_limit INTEGER DEFAULT 20
)
RETURNS TABLE (
    landmark_id UUID,
    landmark_name VARCHAR,
    category VARCHAR,
    district VARCHAR,
    description VARCHAR,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    relevance_score REAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        l.id,
        l.name,
        l.category,
        l.district,
        l.description,
        ST_Y(l.location) as lat,
        ST_X(l.location) as lon,
        -- Calculate relevance score
        GREATEST(
            -- Exact name match
            CASE WHEN lower(l.name) = lower(p_search_text) THEN 1.0 ELSE 0.0 END,
            -- Name starts with search text
            CASE WHEN lower(l.name) LIKE lower(p_search_text) || '%' THEN 0.8 ELSE 0.0 END,
            -- Name contains search text
            CASE WHEN lower(l.name) LIKE '%' || lower(p_search_text) || '%' THEN 0.6 ELSE 0.0 END,
            -- Search terms match
            CASE WHEN EXISTS (
                SELECT 1 FROM unnest(l.search_terms) term 
                WHERE term LIKE '%' || lower(p_search_text) || '%'
            ) THEN 0.4 ELSE 0.0 END
        ) as score
    FROM landmark_locations l
    WHERE l.is_active = true
    AND (p_category IS NULL OR l.category = p_category)
    AND (
        lower(l.name) LIKE '%' || lower(p_search_text) || '%'
        OR EXISTS (
            SELECT 1 FROM unnest(l.search_terms) term 
            WHERE term LIKE '%' || lower(p_search_text) || '%'
        )
    )
    ORDER BY score DESC, l.popularity_score DESC, l.name ASC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql;

/**
 * Increment popularity score when landmark is used
 */
CREATE OR REPLACE FUNCTION increment_landmark_popularity(p_landmark_id UUID)
RETURNS VOID AS $$
BEGIN
    UPDATE landmark_locations
    SET popularity_score = popularity_score + 1
    WHERE id = p_landmark_id;
END;
$$ LANGUAGE plpgsql;

-- ==================== Views ====================

-- Popular landmarks view (for quick lookups)
CREATE VIEW popular_landmarks AS
SELECT 
    id,
    name,
    category,
    district,
    ST_Y(location) as latitude,
    ST_X(location) as longitude,
    popularity_score
FROM landmark_locations
WHERE is_active = true
ORDER BY popularity_score DESC, name ASC
LIMIT 50;

-- Landmarks by category
CREATE VIEW landmarks_by_category AS
SELECT 
    category,
    COUNT(*) as landmark_count,
    array_agg(name ORDER BY name) as landmark_names
FROM landmark_locations
WHERE is_active = true
GROUP BY category
ORDER BY landmark_count DESC;

-- ==================== Sample Starter Data ====================
-- Just a few examples to test the structure

INSERT INTO landmark_locations (name, category, location, district, description, search_terms) VALUES
-- Major Junctions
('Berger Roundabout', 'JUNCTION', ST_GeomFromText('POINT(7.4500 9.0800)', 4326), 'Wuse', 
 'Major junction connecting Wuse and Maitama', 
 ARRAY['berger', 'berger junction', 'berger roundabout']),

('Area 1 Roundabout', 'JUNCTION', ST_GeomFromText('POINT(7.4920 9.0600)', 4326), 'Area 1', 
 'Central Area junction near Federal Secretariat', 
 ARRAY['area 1', 'area one', 'area 1 junction']),

-- Estates
('Gwarinpa Estate', 'ESTATE', ST_GeomFromText('POINT(7.4124 9.1108)', 4326), 'Gwarinpa', 
 'Major residential estate in Abuja', 
 ARRAY['gwarinpa', 'gwarinpa estate']),

('Kubwa Estate', 'ESTATE', ST_GeomFromText('POINT(7.3386 9.0965)', 4326), 'Kubwa', 
 'Large residential area on the outskirts', 
 ARRAY['kubwa', 'kubwa estate']),

-- Landmarks
('Jabi Lake Mall', 'MALL', ST_GeomFromText('POINT(7.4600 9.0700)', 4326), 'Jabi', 
 'Popular shopping mall with cinema', 
 ARRAY['jabi lake', 'jabi mall', 'jabi lake mall']),

('National Mosque', 'LANDMARK', ST_GeomFromText('POINT(7.4905 9.0574)', 4326), 'Central Area', 
 'Nigerian National Mosque in Central Business District', 
 ARRAY['mosque', 'national mosque', 'central mosque']),

-- Government
('Federal Secretariat', 'GOVERNMENT', ST_GeomFromText('POINT(7.4935 9.0625)', 4326), 'Central Area', 
 'Federal Government administrative complex', 
 ARRAY['secretariat', 'federal secretariat', 'phase 1', 'phase 2']);

-- ==================== Comments ====================
COMMENT ON TABLE landmark_locations IS 'Curated list of landmarks in Abuja for driver signup';
COMMENT ON COLUMN landmark_locations.search_terms IS 'Array of lowercase search terms for fuzzy matching';
COMMENT ON COLUMN landmark_locations.popularity_score IS 'Incremented each time landmark is selected by a user';