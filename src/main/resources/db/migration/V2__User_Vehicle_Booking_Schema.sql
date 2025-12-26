-- V2__User_Vehicle_Booking_Schema.sql
-- User, Vehicle, and Booking tables
-- Place in src/main/resources/db/migration/

-- ==================== Users Table ====================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    roles VARCHAR(100) NOT NULL DEFAULT 'RIDER',
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    current_location geometry(Point, 4326),
    profile_image_url VARCHAR(500),
    rating NUMERIC(3, 2) DEFAULT 0.00,
    total_ratings INTEGER DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_verified BOOLEAN NOT NULL DEFAULT false,
    verification_token VARCHAR(100),
    last_login TIMESTAMP,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_user_phone ON users(phone_number);
CREATE INDEX idx_user_active ON users(is_active) WHERE is_active = true;
CREATE INDEX idx_user_current_location ON users USING GIST(current_location);
CREATE INDEX idx_user_roles ON users USING GIN(string_to_array(roles, ','));

-- Trigger
CREATE TRIGGER users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- ==================== Vehicles Table ====================
CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    make VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    year INTEGER NOT NULL,
    color VARCHAR(30) NOT NULL,
    license_plate VARCHAR(20) NOT NULL UNIQUE,
    capacity INTEGER NOT NULL,
    vehicle_image_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_verified BOOLEAN NOT NULL DEFAULT false,
    vehicle_type VARCHAR(20) DEFAULT 'SEDAN',
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_vehicle_capacity CHECK (capacity > 0 AND capacity <= 50),
    CONSTRAINT chk_vehicle_year CHECK (year >= 1900 AND year <= 2100)
);

-- Indexes
CREATE INDEX idx_vehicle_owner ON vehicles(owner_id);
CREATE INDEX idx_vehicle_plate ON vehicles(license_plate);
CREATE INDEX idx_vehicle_active ON vehicles(is_active) WHERE is_active = true;

-- Trigger
CREATE TRIGGER vehicles_updated_at
    BEFORE UPDATE ON vehicles
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- ==================== Bookings Table ====================
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rider_id UUID NOT NULL REFERENCES users(id),
    route_id UUID NOT NULL REFERENCES routes(id),
    pickup_location geometry(Point, 4326) NOT NULL,
    dropoff_location geometry(Point, 4326) NOT NULL,
    pickup_stop_id UUID REFERENCES virtual_stops(id),
    dropoff_stop_id UUID REFERENCES virtual_stops(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_pickup_time TIMESTAMP NOT NULL,
    estimated_dropoff_time TIMESTAMP,
    actual_pickup_time TIMESTAMP,
    actual_dropoff_time TIMESTAMP,
    passenger_count INTEGER NOT NULL DEFAULT 1,
    fare_amount NUMERIC(10, 2) NOT NULL,
    distance_km NUMERIC(10, 2),
    special_instructions VARCHAR(500),
    rider_rating INTEGER,
    driver_rating INTEGER,
    rider_feedback VARCHAR(1000),
    driver_feedback VARCHAR(1000),
    cancellation_reason VARCHAR(500),
    cancelled_by UUID,
    cancelled_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    completed_at TIMESTAMP,
    reference_number VARCHAR(255) NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_passenger_count CHECK (passenger_count > 0 AND passenger_count <= 10),
    CONSTRAINT chk_fare_amount CHECK (fare_amount >= 0),
    CONSTRAINT chk_rider_rating CHECK (rider_rating IS NULL OR (rider_rating >= 1 AND rider_rating <= 5)),
    CONSTRAINT chk_driver_rating CHECK (driver_rating IS NULL OR (driver_rating >= 1 AND driver_rating <= 5)),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'))
);

-- Indexes
CREATE INDEX idx_booking_rider ON bookings(rider_id);
CREATE INDEX idx_booking_route ON bookings(route_id);
CREATE INDEX idx_booking_status ON bookings(status);
CREATE INDEX idx_booking_scheduled_time ON bookings(scheduled_pickup_time);
CREATE INDEX idx_booking_pickup_location ON bookings USING GIST(pickup_location);
CREATE INDEX idx_booking_dropoff_location ON bookings USING GIST(dropoff_location);

-- Composite indexes for common queries
CREATE INDEX idx_booking_rider_status ON bookings(rider_id, status);
CREATE INDEX idx_booking_route_status ON bookings(route_id, status);
CREATE INDEX idx_booking_route_scheduled ON bookings(route_id, scheduled_pickup_time) 
    WHERE status IN ('CONFIRMED', 'IN_PROGRESS');

-- Trigger
CREATE TRIGGER bookings_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- ==================== Helper Functions ====================

-- Function to get user statistics
CREATE OR REPLACE FUNCTION get_user_statistics(p_user_id UUID)
RETURNS TABLE (
    total_bookings BIGINT,
    completed_bookings BIGINT,
    cancelled_bookings BIGINT,
    average_rating NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COUNT(*) as total_bookings,
        COUNT(*) FILTER (WHERE status = 'COMPLETED') as completed_bookings,
        COUNT(*) FILTER (WHERE status = 'CANCELLED') as cancelled_bookings,
        ROUND(AVG(rating), 2) as average_rating
    FROM (
        SELECT status, rider_rating as rating
        FROM bookings
        WHERE rider_id = p_user_id
        UNION ALL
        SELECT b.status, b.driver_rating as rating
        FROM bookings b
        JOIN routes r ON b.route_id = r.id
        WHERE r.driver_id = p_user_id
    ) user_bookings;
END;
$$ LANGUAGE plpgsql;

-- Function to calculate booking distance
CREATE OR REPLACE FUNCTION calculate_booking_distance()
RETURNS TRIGGER AS $$
BEGIN
    -- Calculate straight-line distance between pickup and dropoff
    NEW.distance_km = ROUND(
        (ST_Distance(
            NEW.pickup_location::geography,
            NEW.dropoff_location::geography
        ) / 1000)::numeric,
        2
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER bookings_calculate_distance
    BEFORE INSERT OR UPDATE ON bookings
    FOR EACH ROW
    WHEN (NEW.pickup_location IS NOT NULL AND NEW.dropoff_location IS NOT NULL)
    EXECUTE FUNCTION calculate_booking_distance();

-- ==================== Views ====================

-- Active bookings view (for dashboard)
CREATE VIEW active_bookings AS
SELECT 
    b.id as booking_id,
    b.status,
    b.scheduled_pickup_time,
    u.full_name as rider_name,
    u.phone_number as rider_phone,
    r.name as route_name,
    driver.full_name as driver_name,
    driver.phone_number as driver_phone,
    b.passenger_count,
    b.fare_amount,
    ST_AsGeoJSON(b.pickup_location)::json as pickup_location,
    ST_AsGeoJSON(b.dropoff_location)::json as dropoff_location
FROM bookings b
JOIN users u ON b.rider_id = u.id
JOIN routes r ON b.route_id = r.id
JOIN users driver ON r.driver_id = driver.id
WHERE b.status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS');

-- Driver earnings view
CREATE VIEW driver_earnings AS
SELECT 
    u.id as driver_id,
    u.full_name as driver_name,
    COUNT(b.id) as total_rides,
    COUNT(b.id) FILTER (WHERE b.status = 'COMPLETED') as completed_rides,
    SUM(b.fare_amount) FILTER (WHERE b.status = 'COMPLETED') as total_earnings,
    AVG(b.driver_rating) FILTER (WHERE b.driver_rating IS NOT NULL) as average_rating
FROM users u
JOIN routes r ON r.driver_id = u.id
LEFT JOIN bookings b ON b.route_id = r.id
WHERE u.roles LIKE '%DRIVER%'
GROUP BY u.id, u.full_name;

-- ==================== Sample Data ====================

-- Create sample users (riders and drivers)
INSERT INTO users (username, password_hash, full_name, email, phone_number, roles, is_verified) VALUES
    ('john_rider', '$2a$10$DOwASqrtzLz7OqU.FZbZmeKNXmNXWLjPB2LYCxjHMLO9rIvvhVVai', 'John Doe', 'john@example.com', '+2348012345678', 'RIDER', true),
    ('jane_driver', '$2a$10$DOwASqrtzLz7OqU.FZbZmeKNXmNXWLjPB2LYCxjHMLO9rIvvhVVai', 'Jane Smith', 'jane@example.com', '+2348087654321', 'RIDER,DRIVER', true),
    ('mike_driver', '$2a$10$DOwASqrtzLz7OqU.FZbZmeKNXmNXWLjPB2LYCxjHMLO9rIvvhVVai', 'Mike Johnson', 'mike@example.com', '+2348098765432', 'RIDER,DRIVER', true);

-- Note: Password is 'password123' hashed with bcrypt

-- Add sample vehicles
INSERT INTO vehicles (owner_id, make, model, year, color, license_plate, capacity, vehicle_type, is_verified)
SELECT 
    id,
    'Toyota',
    'Camry',
    2020,
    'Black',
    'ABC-123-DE',
    4,
    'SEDAN',
    true
FROM users WHERE username = 'jane_driver'
UNION ALL
SELECT 
    id,
    'Honda',
    'Pilot',
    2019,
    'Silver',
    'XYZ-789-FG',
    6,
    'SUV',
    true
FROM users WHERE username = 'mike_driver';

-- Update existing routes with driver IDs from sample users
UPDATE routes
SET driver_id = (SELECT id FROM users WHERE username = 'jane_driver' LIMIT 1)
WHERE name = 'Central Area → Maitama';

UPDATE routes
SET driver_id = (SELECT id FROM users WHERE username = 'mike_driver' LIMIT 1)
WHERE name = 'Gwarinpa → Wuse';

-- ==================== Indexes for Performance ====================

-- GIN index for full-text search on user names (optional)
CREATE INDEX idx_user_fullname_gin ON users USING GIN(to_tsvector('english', full_name));

-- Index for finding bookings in a date range
CREATE INDEX idx_booking_date_range ON bookings(scheduled_pickup_time)
WHERE status IN ('PENDING', 'CONFIRMED');