-- V3__Add_Driver_Stats.sql
-- Add driver status and dashboard statistics view

-- 1. Add driver_status to users table
ALTER TABLE users 
ADD COLUMN driver_status VARCHAR(20) DEFAULT 'OFFLINE';

-- Create index for fast status lookups
CREATE INDEX idx_user_driver_status ON users(driver_status);

-- 2. Create the Dashboard Stats View
-- This complex view aggregates data from bookings and routes to provide
-- a single snapshot of driver performance.
CREATE OR REPLACE VIEW driver_dashboard_stats AS
SELECT 
    u.id as driver_id,
    u.rating,
    u.total_ratings,
    u.driver_status as status,
    
    -- Active Route (if any)
    (SELECT r.id FROM routes r WHERE r.driver_id = u.id AND r.is_active = true LIMIT 1) as current_route_id,
    
    -- Active Passengers (Currently in a ride or confirmed waiting)
    (SELECT COUNT(*) FROM bookings b 
     JOIN routes r ON b.route_id = r.id 
     WHERE r.driver_id = u.id 
     AND b.status IN ('CONFIRMED', 'IN_PROGRESS')) as active_passengers,

    -- Trip Counts
    (SELECT COUNT(*) FROM bookings b JOIN routes r ON b.route_id = r.id WHERE r.driver_id = u.id AND b.status = 'COMPLETED') as completed_trips,
    (SELECT COUNT(*) FROM bookings b JOIN routes r ON b.route_id = r.id WHERE r.driver_id = u.id AND b.status = 'COMPLETED' AND b.completed_at >= CURRENT_DATE) as completed_trips_today,
    (SELECT COUNT(*) FROM bookings b JOIN routes r ON b.route_id = r.id WHERE r.driver_id = u.id AND b.status = 'COMPLETED' AND b.completed_at >= date_trunc('week', CURRENT_DATE)) as completed_trips_this_week,
    (SELECT COUNT(*) FROM bookings b JOIN routes r ON b.route_id = r.id WHERE r.driver_id = u.id AND b.status = 'COMPLETED' AND b.completed_at >= date_trunc('month', CURRENT_DATE)) as completed_trips_this_month,

    -- Earnings (Sum of fare_amount)
    COALESCE((SELECT SUM(b.fare_amount) FROM bookings b JOIN routes r ON b.route_id = r.id WHERE r.driver_id = u.id AND b.status = 'COMPLETED'), 0) as total_earnings,
    COALESCE((SELECT SUM(b.fare_amount) FROM bookings b JOIN routes r ON b.route_id = r.id WHERE r.driver_id = u.id AND b.status = 'COMPLETED' AND b.completed_at >= CURRENT_DATE), 0) as earnings_today,
    COALESCE((SELECT SUM(b.fare_amount) FROM bookings b JOIN routes r ON b.route_id = r.id WHERE r.driver_id = u.id AND b.status = 'COMPLETED' AND b.completed_at >= date_trunc('week', CURRENT_DATE)), 0) as earnings_this_week,
    COALESCE((SELECT SUM(b.fare_amount) FROM bookings b JOIN routes r ON b.route_id = r.id WHERE r.driver_id = u.id AND b.status = 'COMPLETED' AND b.completed_at >= date_trunc('month', CURRENT_DATE)), 0) as earnings_this_month

FROM users u
WHERE u.roles LIKE '%DRIVER%';