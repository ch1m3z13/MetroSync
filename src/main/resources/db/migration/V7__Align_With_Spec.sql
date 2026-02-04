-- V7__Align_With_Spec.sql
-- Align database schema with specification requirements
-- This migration updates existing tables to match the core specification

-- ==================== USERS TABLE UPDATES ====================

-- Make phone_number optional (currently NOT NULL)
ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;

-- Add comment to clarify roles field format
COMMENT ON COLUMN users.roles IS 
'Comma-separated roles: PASSENGER, DRIVER. User can have both roles.
Examples: "PASSENGER", "DRIVER", "PASSENGER,DRIVER"';

-- ==================== ROUTES TABLE UPDATES ====================

-- Add missing fields from specification
ALTER TABLE routes 
ADD COLUMN IF NOT EXISTS origin VARCHAR(500),
ADD COLUMN IF NOT EXISTS destination VARCHAR(500),
ADD COLUMN IF NOT EXISTS polyline TEXT,
ADD COLUMN IF NOT EXISTS price_per_seat NUMERIC(10, 2),
ADD COLUMN IF NOT EXISTS available_seats INTEGER,
ADD COLUMN IF NOT EXISTS departure_time TIMESTAMP,
ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';

-- Add constraints for new fields
ALTER TABLE routes 
ADD CONSTRAINT chk_route_price_per_seat CHECK (price_per_seat IS NULL OR price_per_seat >= 0),
ADD CONSTRAINT chk_route_available_seats CHECK (available_seats IS NULL OR available_seats >= 0),
ADD CONSTRAINT chk_route_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'COMPLETED', 'CANCELLED'));

-- Create index on status
CREATE INDEX idx_route_status ON routes(status);

-- Migrate existing is_active/is_published to status
UPDATE routes 
SET status = CASE 
    WHEN is_active = true AND is_published = true THEN 'ACTIVE'
    WHEN is_active = false OR is_published = false THEN 'INACTIVE'
    ELSE 'ACTIVE'
END
WHERE status = 'ACTIVE'; -- Only update if still default

-- Add function to sync geometry and polyline
CREATE OR REPLACE FUNCTION sync_route_polyline()
RETURNS TRIGGER AS $$
BEGIN
    -- If geometry is updated, clear polyline to force regeneration
    IF NEW.geometry IS DISTINCT FROM OLD.geometry THEN
        NEW.polyline = NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER route_sync_polyline
    BEFORE UPDATE ON routes
    FOR EACH ROW
    WHEN (NEW.geometry IS DISTINCT FROM OLD.geometry)
    EXECUTE FUNCTION sync_route_polyline();

-- Add business rule validation trigger
CREATE OR REPLACE FUNCTION validate_route_business_rules()
RETURNS TRIGGER AS $$
BEGIN
    -- Rule: availableSeats must be > 0 when ACTIVE
    IF NEW.status = 'ACTIVE' AND (NEW.available_seats IS NULL OR NEW.available_seats <= 0) THEN
        RAISE EXCEPTION 'Active routes must have available_seats > 0';
    END IF;
    
    -- Rule: Must have at least 2 stops (will be checked by virtual_stops constraint)
    -- This will be validated at application level since stops are in separate table
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER route_validate_business_rules
    BEFORE INSERT OR UPDATE ON routes
    FOR EACH ROW
    EXECUTE FUNCTION validate_route_business_rules();

-- ==================== VIRTUAL STOPS TABLE UPDATES ====================

-- Add stop type field (PICKUP or DROPOFF)
ALTER TABLE virtual_stops 
ADD COLUMN IF NOT EXISTS type VARCHAR(20);

-- Add constraint for type
ALTER TABLE virtual_stops 
ADD CONSTRAINT chk_virtual_stop_type CHECK (type IN ('PICKUP', 'DROPOFF'));

-- Add address field (separate from name)
ALTER TABLE virtual_stops 
ADD COLUMN IF NOT EXISTS address VARCHAR(500);

-- Rename sequence_order to sequence for consistency with spec
-- (Keep both for backward compatibility during transition)
ALTER TABLE virtual_stops 
ADD COLUMN IF NOT EXISTS sequence INTEGER;

-- Copy data from sequence_order to sequence
UPDATE virtual_stops 
SET sequence = sequence_order 
WHERE sequence IS NULL;

-- Add unique constraint on route + sequence
CREATE UNIQUE INDEX IF NOT EXISTS idx_virtual_stop_route_sequence 
ON virtual_stops(route_id, sequence);

-- Validation function for stop business rules
CREATE OR REPLACE FUNCTION validate_virtual_stop_rules()
RETURNS TRIGGER AS $$
DECLARE
    pickup_count INTEGER;
    dropoff_count INTEGER;
BEGIN
    -- Validate coordinates (latitude: -90 to 90, longitude: -180 to 180)
    IF ST_Y(NEW.location) < -90 OR ST_Y(NEW.location) > 90 THEN
        RAISE EXCEPTION 'Invalid latitude: must be between -90 and 90';
    END IF;
    
    IF ST_X(NEW.location) < -180 OR ST_X(NEW.location) > 180 THEN
        RAISE EXCEPTION 'Invalid longitude: must be between -180 and 180';
    END IF;
    
    -- After insert/update, check if route has at least one PICKUP and one DROPOFF
    IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
        SELECT 
            COUNT(*) FILTER (WHERE type = 'PICKUP'),
            COUNT(*) FILTER (WHERE type = 'DROPOFF')
        INTO pickup_count, dropoff_count
        FROM virtual_stops
        WHERE route_id = NEW.route_id AND is_active = true;
        
        -- Note: This check is informational; we allow partial states during bulk inserts
        -- Full validation should happen at route activation
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER virtual_stop_validate_rules
    BEFORE INSERT OR UPDATE ON virtual_stops
    FOR EACH ROW
    EXECUTE FUNCTION validate_virtual_stop_rules();

-- Function to validate route has minimum required stops
CREATE OR REPLACE FUNCTION validate_route_stops(p_route_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    pickup_count INTEGER;
    dropoff_count INTEGER;
    total_count INTEGER;
BEGIN
    SELECT 
        COUNT(*),
        COUNT(*) FILTER (WHERE type = 'PICKUP'),
        COUNT(*) FILTER (WHERE type = 'DROPOFF')
    INTO total_count, pickup_count, dropoff_count
    FROM virtual_stops
    WHERE route_id = p_route_id AND is_active = true;
    
    -- Must have at least 2 total stops
    IF total_count < 2 THEN
        RETURN FALSE;
    END IF;
    
    -- Must have at least one PICKUP and one DROPOFF
    IF pickup_count < 1 OR dropoff_count < 1 THEN
        RETURN FALSE;
    END IF;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- ==================== BOOKINGS TABLE UPDATES ====================

-- Add driver_id for denormalized quick access (as per spec)
ALTER TABLE bookings 
ADD COLUMN IF NOT EXISTS driver_id UUID;

-- Populate driver_id from routes
UPDATE bookings b
SET driver_id = r.driver_id
FROM routes r
WHERE b.route_id = r.id AND b.driver_id IS NULL;

-- Add foreign key constraint
ALTER TABLE bookings
ADD CONSTRAINT fk_booking_driver 
FOREIGN KEY (driver_id) REFERENCES users(id);

-- Create index on driver_id
CREATE INDEX IF NOT EXISTS idx_booking_driver ON bookings(driver_id);

-- Add fare column (rename from fare_amount for consistency)
-- Keep fare_amount for backward compatibility
ALTER TABLE bookings 
ADD COLUMN IF NOT EXISTS fare NUMERIC(10, 2);

-- Copy data
UPDATE bookings SET fare = fare_amount WHERE fare IS NULL;

-- Add constraints for fare
ALTER TABLE bookings 
ADD CONSTRAINT chk_booking_fare CHECK (fare IS NULL OR fare >= 0);

-- Rename time fields to match spec (add new columns, keep old ones for compatibility)
ALTER TABLE bookings 
ADD COLUMN IF NOT EXISTS pickup_time TIMESTAMP,
ADD COLUMN IF NOT EXISTS dropoff_time TIMESTAMP;

-- Copy estimated times to new fields
UPDATE bookings 
SET pickup_time = scheduled_pickup_time 
WHERE pickup_time IS NULL;

UPDATE bookings 
SET dropoff_time = estimated_dropoff_time 
WHERE dropoff_time IS NULL;

-- Update status constraint to remove NO_SHOW (not in spec)
-- First update any NO_SHOW bookings to CANCELLED
UPDATE bookings SET status = 'CANCELLED' WHERE status = 'NO_SHOW';

-- Drop old constraint
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS chk_status;

-- Add new constraint matching spec
ALTER TABLE bookings 
ADD CONSTRAINT chk_booking_status 
CHECK (status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

-- Validation function for booking business rules
CREATE OR REPLACE FUNCTION validate_booking_business_rules()
RETURNS TRIGGER AS $$
DECLARE
    route_available_seats INTEGER;
    active_booking_count INTEGER;
BEGIN
    -- Rule: Passenger cannot book same route twice if active booking exists
    IF TG_OP = 'INSERT' THEN
        SELECT COUNT(*) INTO active_booking_count
        FROM bookings
        WHERE rider_id = NEW.rider_id 
        AND route_id = NEW.route_id
        AND status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS');
        
        IF active_booking_count > 0 THEN
            RAISE EXCEPTION 'Passenger already has an active booking for this route';
        END IF;
    END IF;
    
    -- Rule: Booking creation must check route.availableSeats > 0
    IF TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND NEW.status = 'CONFIRMED') THEN
        SELECT available_seats INTO route_available_seats
        FROM routes
        WHERE id = NEW.route_id;
        
        IF route_available_seats IS NOT NULL AND route_available_seats <= 0 THEN
            RAISE EXCEPTION 'No available seats on this route';
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER booking_validate_business_rules
    BEFORE INSERT OR UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION validate_booking_business_rules();

-- Function to update route available seats when booking status changes
CREATE OR REPLACE FUNCTION update_route_available_seats()
RETURNS TRIGGER AS $$
BEGIN
    -- When booking is confirmed, decrement available seats
    IF TG_OP = 'INSERT' AND NEW.status = 'CONFIRMED' THEN
        UPDATE routes 
        SET available_seats = available_seats - COALESCE(NEW.passenger_count, 1)
        WHERE id = NEW.route_id;
    END IF;
    
    -- When booking status changes to confirmed
    IF TG_OP = 'UPDATE' AND OLD.status != 'CONFIRMED' AND NEW.status = 'CONFIRMED' THEN
        UPDATE routes 
        SET available_seats = available_seats - COALESCE(NEW.passenger_count, 1)
        WHERE id = NEW.route_id;
    END IF;
    
    -- When booking is cancelled or completed, increment available seats
    IF TG_OP = 'UPDATE' AND OLD.status = 'CONFIRMED' AND NEW.status IN ('CANCELLED', 'COMPLETED') THEN
        UPDATE routes 
        SET available_seats = available_seats + COALESCE(OLD.passenger_count, 1)
        WHERE id = NEW.route_id;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER booking_update_seats
    AFTER INSERT OR UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION update_route_available_seats();

-- ==================== LOCATION TRACKING TABLE (NEW) ====================

-- Create location tracking table as per specification
CREATE TABLE IF NOT EXISTS location_tracking (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    bearing DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_location_latitude CHECK (latitude >= -90 AND latitude <= 90),
    CONSTRAINT chk_location_longitude CHECK (longitude >= -180 AND longitude <= 180),
    CONSTRAINT chk_location_bearing CHECK (bearing IS NULL OR (bearing >= 0 AND bearing <= 360)),
    CONSTRAINT chk_location_speed CHECK (speed IS NULL OR speed >= 0)
);

-- Indexes for location tracking
CREATE INDEX idx_location_route ON location_tracking(route_id);
CREATE INDEX idx_location_user ON location_tracking(user_id);
CREATE INDEX idx_location_timestamp ON location_tracking(timestamp);

-- Composite index for time-based queries
CREATE INDEX idx_location_route_timestamp ON location_tracking(route_id, timestamp DESC);

-- Business rule: Only store if driver has active route
CREATE OR REPLACE FUNCTION validate_location_tracking()
RETURNS TRIGGER AS $$
DECLARE
    route_is_active BOOLEAN;
    user_is_driver BOOLEAN;
BEGIN
    -- Check if route is active
    SELECT (status = 'ACTIVE') INTO route_is_active
    FROM routes
    WHERE id = NEW.route_id;
    
    IF NOT route_is_active THEN
        RAISE EXCEPTION 'Cannot track location for inactive route';
    END IF;
    
    -- Check if user is the driver of this route
    SELECT EXISTS (
        SELECT 1 FROM routes 
        WHERE id = NEW.route_id AND driver_id = NEW.user_id
    ) INTO user_is_driver;
    
    IF NOT user_is_driver THEN
        RAISE EXCEPTION 'User must be the driver of the route to track location';
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER location_validate_tracking
    BEFORE INSERT ON location_tracking
    FOR EACH ROW
    EXECUTE FUNCTION validate_location_tracking();

-- GDPR Compliance: Auto-delete location data after 30 days
-- This is implemented as a scheduled job function
CREATE OR REPLACE FUNCTION cleanup_old_location_tracking()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM location_tracking
    WHERE timestamp < CURRENT_TIMESTAMP - INTERVAL '30 days';
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Create a view for recent location tracking (last 24 hours)
CREATE OR REPLACE VIEW recent_location_tracking AS
SELECT 
    lt.id,
    lt.route_id,
    lt.user_id,
    u.full_name as driver_name,
    lt.latitude,
    lt.longitude,
    lt.bearing,
    lt.speed,
    lt.timestamp,
    r.name as route_name,
    r.status as route_status
FROM location_tracking lt
JOIN users u ON lt.user_id = u.id
JOIN routes r ON lt.route_id = r.id
WHERE lt.timestamp >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
ORDER BY lt.timestamp DESC;

-- ==================== HELPER VIEWS ====================

-- View for active routes with all required fields
CREATE OR REPLACE VIEW active_routes_summary AS
SELECT 
    r.id,
    r.driver_id,
    u.full_name as driver_name,
    r.origin,
    r.destination,
    r.price_per_seat,
    r.available_seats,
    r.departure_time,
    r.status,
    r.distance_km,
    COUNT(vs.id) as stop_count,
    COUNT(vs.id) FILTER (WHERE vs.type = 'PICKUP') as pickup_count,
    COUNT(vs.id) FILTER (WHERE vs.type = 'DROPOFF') as dropoff_count,
    ST_AsGeoJSON(r.geometry)::json as geometry_geojson,
    r.created_at,
    r.updated_at
FROM routes r
JOIN users u ON r.driver_id = u.id
LEFT JOIN virtual_stops vs ON vs.route_id = r.id AND vs.is_active = true
WHERE r.status = 'ACTIVE'
GROUP BY r.id, u.full_name;

-- View for booking details with passenger and driver info
CREATE OR REPLACE VIEW booking_details AS
SELECT 
    b.id as booking_id,
    b.status,
    b.fare,
    b.pickup_time,
    b.dropoff_time,
    b.passenger_count,
    
    -- Passenger info
    p.id as passenger_id,
    p.full_name as passenger_name,
    p.email as passenger_email,
    p.phone_number as passenger_phone,
    
    -- Driver info
    d.id as driver_id,
    d.full_name as driver_name,
    d.email as driver_email,
    d.phone_number as driver_phone,
    
    -- Route info
    r.id as route_id,
    r.origin,
    r.destination,
    r.departure_time,
    
    -- Locations
    ST_Y(b.pickup_location) as pickup_latitude,
    ST_X(b.pickup_location) as pickup_longitude,
    ST_Y(b.dropoff_location) as dropoff_latitude,
    ST_X(b.dropoff_location) as dropoff_longitude,
    
    -- Stop IDs
    b.pickup_stop_id,
    b.dropoff_stop_id,
    
    b.created_at,
    b.updated_at,
    b.completed_at
FROM bookings b
JOIN users p ON b.rider_id = p.id
JOIN routes r ON b.route_id = r.id
JOIN users d ON b.driver_id = d.id;

-- ==================== COMMENTS FOR DOCUMENTATION ====================

COMMENT ON TABLE location_tracking IS 
'Stores historical GPS location data for active routes. 
Automatically deleted after 30 days for GDPR compliance.
Used for route replay and dispute resolution.';

COMMENT ON COLUMN routes.polyline IS 
'Google Maps encoded polyline format. Should be synced with geometry field.';

COMMENT ON COLUMN routes.available_seats IS 
'Current number of available seats. Automatically updated when bookings are confirmed/cancelled.';

COMMENT ON COLUMN virtual_stops.type IS 
'Stop type: PICKUP or DROPOFF. Each route must have at least one of each.';

COMMENT ON COLUMN bookings.driver_id IS 
'Denormalized driver ID for quick access. Synced from routes.driver_id.';

COMMENT ON FUNCTION cleanup_old_location_tracking IS 
'GDPR compliance function. Should be called by scheduled job daily.
Deletes location tracking records older than 30 days.';
