-- V11__Add_Notifications.sql
-- Add notifications table for in-app alerts
-- CommuteNG Specification Requirement

-- ==================== NOTIFICATIONS TABLE ====================

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- Notification content
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,  -- BOOKING_UPDATE, PAYMENT_SUCCESS, DRIVER_ARRIVED, etc.
    
    -- Notification data (JSON for flexibility)
    data JSONB,
    
    -- Read status
    is_read BOOLEAN NOT NULL DEFAULT false,
    read_at TIMESTAMP,
    
    -- Priority level
    priority VARCHAR(20) DEFAULT 'NORMAL',  -- LOW, NORMAL, HIGH, URGENT
    
    -- Deep linking
    action_url VARCHAR(500),  -- URL to open when notification is tapped
    
    -- Related entities
    related_booking_id UUID REFERENCES bookings(id) ON DELETE CASCADE,
    related_route_id UUID REFERENCES routes(id) ON DELETE CASCADE,
    
    -- Standard audit fields
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_notification_type CHECK (type IN (
        'BOOKING_CREATED',
        'BOOKING_CONFIRMED',
        'BOOKING_CANCELLED',
        'DRIVER_ARRIVED',
        'TRIP_STARTED',
        'TRIP_COMPLETED',
        'PAYMENT_SUCCESS',
        'PAYMENT_FAILED',
        'WALLET_CREDITED',
        'WALLET_DEBITED',
        'VERIFICATION_APPROVED',
        'VERIFICATION_REJECTED',
        'ROUTE_ACTIVATED',
        'ROUTE_DEACTIVATED',
        'GENERAL'
    )),
    CONSTRAINT chk_notification_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
);

-- ==================== INDEXES ====================

-- User's notifications lookup - CRITICAL
CREATE INDEX idx_notifications_user ON notifications(user_id);

-- Unread notifications (for notification badge count)
CREATE INDEX idx_notifications_unread ON notifications(user_id, is_read) 
    WHERE is_read = false;

-- Recent notifications (for notification center)
CREATE INDEX idx_notifications_recent ON notifications(user_id, created_at DESC);

-- Type filter
CREATE INDEX idx_notifications_type ON notifications(type);

-- Priority filter
CREATE INDEX idx_notifications_priority ON notifications(priority);

-- Related booking lookup
CREATE INDEX idx_notifications_booking ON notifications(related_booking_id)
    WHERE related_booking_id IS NOT NULL;

-- Related route lookup
CREATE INDEX idx_notifications_route ON notifications(related_route_id)
    WHERE related_route_id IS NOT NULL;

-- ==================== TRIGGERS ====================

-- Update timestamp trigger
CREATE TRIGGER notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Auto-set read_at when is_read is changed to true
CREATE OR REPLACE FUNCTION set_notification_read_time()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_read = true AND OLD.is_read = false THEN
        NEW.read_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER notification_set_read_time
    BEFORE UPDATE ON notifications
    FOR EACH ROW
    WHEN (NEW.is_read = true AND OLD.is_read = false)
    EXECUTE FUNCTION set_notification_read_time();

-- ==================== HELPER FUNCTIONS ====================

/**
 * Create a notification for a user
 */
CREATE OR REPLACE FUNCTION create_notification(
    p_user_id UUID,
    p_title VARCHAR,
    p_message TEXT,
    p_type VARCHAR,
    p_data JSONB DEFAULT NULL,
    p_priority VARCHAR DEFAULT 'NORMAL',
    p_action_url VARCHAR DEFAULT NULL,
    p_booking_id UUID DEFAULT NULL,
    p_route_id UUID DEFAULT NULL
)
RETURNS UUID AS $$
DECLARE
    v_notification_id UUID;
BEGIN
    INSERT INTO notifications (
        user_id, title, message, type, data, priority,
        action_url, related_booking_id, related_route_id
    ) VALUES (
        p_user_id, p_title, p_message, p_type, p_data, p_priority,
        p_action_url, p_booking_id, p_route_id
    )
    RETURNING id INTO v_notification_id;
    
    RETURN v_notification_id;
END;
$$ LANGUAGE plpgsql;

/**
 * Mark all notifications as read for a user
 */
CREATE OR REPLACE FUNCTION mark_all_notifications_read(p_user_id UUID)
RETURNS INTEGER AS $$
DECLARE
    v_updated_count INTEGER;
BEGIN
    UPDATE notifications
    SET is_read = true,
        read_at = CURRENT_TIMESTAMP
    WHERE user_id = p_user_id
    AND is_read = false;
    
    GET DIAGNOSTICS v_updated_count = ROW_COUNT;
    
    RETURN v_updated_count;
END;
$$ LANGUAGE plpgsql;

/**
 * Get unread notification count for a user
 */
CREATE OR REPLACE FUNCTION get_unread_notification_count(p_user_id UUID)
RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM notifications
    WHERE user_id = p_user_id
    AND is_read = false;
    
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

/**
 * Delete old read notifications (cleanup function)
 * Keeps unread notifications indefinitely
 * Deletes read notifications older than 30 days
 */
CREATE OR REPLACE FUNCTION cleanup_old_notifications()
RETURNS INTEGER AS $$
DECLARE
    v_deleted_count INTEGER;
BEGIN
    DELETE FROM notifications
    WHERE is_read = true
    AND read_at < CURRENT_TIMESTAMP - INTERVAL '30 days';
    
    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
    
    RETURN v_deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ==================== VIEWS ====================

/**
 * Recent unread notifications view
 */
CREATE VIEW user_unread_notifications AS
SELECT 
    n.id,
    n.user_id,
    n.title,
    n.message,
    n.type,
    n.data,
    n.priority,
    n.action_url,
    n.related_booking_id,
    n.related_route_id,
    n.created_at,
    -- Additional context
    CASE 
        WHEN n.related_booking_id IS NOT NULL THEN 
            (SELECT status FROM bookings WHERE id = n.related_booking_id)
        ELSE NULL
    END as booking_status,
    CASE 
        WHEN n.related_route_id IS NOT NULL THEN 
            (SELECT name FROM routes WHERE id = n.related_route_id)
        ELSE NULL
    END as route_name
FROM notifications n
WHERE n.is_read = false
ORDER BY 
    CASE n.priority
        WHEN 'URGENT' THEN 1
        WHEN 'HIGH' THEN 2
        WHEN 'NORMAL' THEN 3
        WHEN 'LOW' THEN 4
    END,
    n.created_at DESC;

/**
 * Notification statistics by type
 */
CREATE VIEW notification_statistics AS
SELECT 
    type,
    COUNT(*) as total_count,
    COUNT(*) FILTER (WHERE is_read = false) as unread_count,
    COUNT(*) FILTER (WHERE is_read = true) as read_count,
    COUNT(*) FILTER (WHERE priority = 'URGENT') as urgent_count,
    COUNT(*) FILTER (WHERE priority = 'HIGH') as high_count,
    AVG(EXTRACT(EPOCH FROM (read_at - created_at))) / 60 as avg_read_time_minutes
FROM notifications
GROUP BY type
ORDER BY total_count DESC;

-- ==================== AUTOMATIC NOTIFICATION TRIGGERS ====================

/**
 * Auto-create notification when booking is confirmed
 */
CREATE OR REPLACE FUNCTION notify_booking_confirmed()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'CONFIRMED' AND OLD.status = 'PENDING' THEN
        -- Notify rider
        PERFORM create_notification(
            NEW.rider_id,
            'Booking Confirmed',
            'Your ride has been confirmed by the driver',
            'BOOKING_CONFIRMED',
            jsonb_build_object(
                'booking_id', NEW.id,
                'route_id', NEW.route_id,
                'scheduled_time', NEW.scheduled_pickup_time
            ),
            'HIGH',
            '/bookings/' || NEW.id,
            NEW.id,
            NEW.route_id
        );
        
        -- Notify driver
        PERFORM create_notification(
            (SELECT driver_id FROM routes WHERE id = NEW.route_id),
            'New Booking',
            'You have a new confirmed booking',
            'BOOKING_CONFIRMED',
            jsonb_build_object(
                'booking_id', NEW.id,
                'passenger_count', NEW.passenger_count
            ),
            'NORMAL',
            '/bookings/' || NEW.id,
            NEW.id,
            NEW.route_id
        );
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER booking_confirmed_notification
    AFTER UPDATE ON bookings
    FOR EACH ROW
    WHEN (NEW.status = 'CONFIRMED' AND OLD.status = 'PENDING')
    EXECUTE FUNCTION notify_booking_confirmed();

/**
 * Auto-create notification when booking is cancelled
 */
CREATE OR REPLACE FUNCTION notify_booking_cancelled()
RETURNS TRIGGER AS $$
DECLARE
    v_driver_id UUID;
BEGIN
    IF NEW.status = 'CANCELLED' AND OLD.status IN ('PENDING', 'CONFIRMED') THEN
        SELECT driver_id INTO v_driver_id FROM routes WHERE id = NEW.route_id;
        
        -- Notify rider
        PERFORM create_notification(
            NEW.rider_id,
            'Booking Cancelled',
            'Your booking has been cancelled',
            'BOOKING_CANCELLED',
            jsonb_build_object(
                'booking_id', NEW.id,
                'cancellation_reason', NEW.cancellation_reason
            ),
            'HIGH',
            '/bookings/' || NEW.id,
            NEW.id,
            NEW.route_id
        );
        
        -- Notify driver
        PERFORM create_notification(
            v_driver_id,
            'Booking Cancelled',
            'A booking has been cancelled',
            'BOOKING_CANCELLED',
            jsonb_build_object(
                'booking_id', NEW.id,
                'cancelled_by', NEW.cancelled_by
            ),
            'NORMAL',
            '/bookings/' || NEW.id,
            NEW.id,
            NEW.route_id
        );
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER booking_cancelled_notification
    AFTER UPDATE ON bookings
    FOR EACH ROW
    WHEN (NEW.status = 'CANCELLED' AND OLD.status IN ('PENDING', 'CONFIRMED'))
    EXECUTE FUNCTION notify_booking_cancelled();

/**
 * Auto-create notification when trip is completed
 */
CREATE OR REPLACE FUNCTION notify_trip_completed()
RETURNS TRIGGER AS $$
DECLARE
    v_driver_id UUID;
BEGIN
    IF NEW.status = 'COMPLETED' AND OLD.status = 'IN_PROGRESS' THEN
        SELECT driver_id INTO v_driver_id FROM routes WHERE id = NEW.route_id;
        
        -- Notify rider
        PERFORM create_notification(
            NEW.rider_id,
            'Trip Completed',
            'Your trip has been completed. Please rate your experience.',
            'TRIP_COMPLETED',
            jsonb_build_object(
                'booking_id', NEW.id,
                'fare_amount', NEW.fare_amount
            ),
            'NORMAL',
            '/bookings/' || NEW.id || '/rate',
            NEW.id,
            NEW.route_id
        );
        
        -- Notify driver
        PERFORM create_notification(
            v_driver_id,
            'Trip Completed',
            'Trip completed successfully',
            'TRIP_COMPLETED',
            jsonb_build_object(
                'booking_id', NEW.id,
                'earnings', NEW.fare_amount
            ),
            'NORMAL',
            '/bookings/' || NEW.id,
            NEW.id,
            NEW.route_id
        );
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trip_completed_notification
    AFTER UPDATE ON bookings
    FOR EACH ROW
    WHEN (NEW.status = 'COMPLETED' AND OLD.status = 'IN_PROGRESS')
    EXECUTE FUNCTION notify_trip_completed();

-- ==================== COMMENTS ====================

COMMENT ON TABLE notifications IS 
'Stores in-app notifications for users. 
Automatically created by triggers for key events (booking confirmed, payment success, etc.).
Read notifications older than 30 days are automatically deleted.';

COMMENT ON COLUMN notifications.type IS 
'Notification type for categorization and filtering. 
Determines the icon and behavior in the mobile app.';

COMMENT ON COLUMN notifications.data IS 
'JSON data for additional context. 
Can store booking IDs, payment amounts, driver info, etc.';

COMMENT ON COLUMN notifications.action_url IS 
'Deep link URL to open when notification is tapped. 
Example: /bookings/123, /wallet, /profile/verification';

COMMENT ON FUNCTION cleanup_old_notifications IS 
'Maintenance function to delete old read notifications.
Should be called daily by scheduled job.
Keeps unread notifications indefinitely.';