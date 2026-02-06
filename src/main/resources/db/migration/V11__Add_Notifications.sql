-- V11: Add Notifications System
-- Table: notifications

-- User Notifications (In-app alerts, Push notifications)
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,  -- CHANGED: BIGINT → UUID
    
    -- Notification Content
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    
    -- Notification Type
    type VARCHAR(50) NOT NULL,  -- BOOKING_CONFIRMED, BOOKING_CANCELLED, RIDE_STARTED, RIDE_COMPLETED, 
                                -- PAYMENT_RECEIVED, PAYMENT_FAILED, VERIFICATION_APPROVED, VERIFICATION_REJECTED,
                                -- WALLET_TOPUP, WALLET_WITHDRAWAL, DRIVER_ARRIVED, etc.
    
    -- Priority
    priority VARCHAR(20) DEFAULT 'NORMAL',  -- LOW, NORMAL, HIGH, URGENT
    
    -- Related Entities
    booking_id UUID,  -- CHANGED: BIGINT → UUID
    transaction_id BIGINT,
    route_id UUID,  -- CHANGED: BIGINT → UUID
    
    -- Delivery Status
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP,
    
    is_sent BOOLEAN DEFAULT FALSE,  -- For push notifications
    sent_at TIMESTAMP,
    
    -- Delivery Channels
    delivery_channels VARCHAR(100)[] DEFAULT ARRAY['IN_APP'],  -- IN_APP, PUSH, SMS, EMAIL
    
    -- Click Action (Deep linking)
    action_type VARCHAR(50),  -- VIEW_BOOKING, VIEW_TRANSACTION, VIEW_PROFILE, etc.
    action_data JSONB,  -- Additional data for the action (e.g., booking ID)
    
    -- Expiry
    expires_at TIMESTAMP,  -- Notifications can auto-expire
    
    -- Metadata
    metadata JSONB,  -- Flexible additional data
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_route FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE SET NULL
);

-- Indexes for notifications
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_booking_id ON notifications(booking_id);
CREATE INDEX idx_notifications_transaction_id ON notifications(transaction_id);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_notifications_priority ON notifications(priority);

-- Partial index for unread notifications (most common query)
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, created_at DESC) 
    WHERE is_read = FALSE;

-- Partial index for unsent push notifications
CREATE INDEX idx_notifications_unsent_push ON notifications(user_id, created_at) 
    WHERE is_sent = FALSE AND 'PUSH' = ANY(delivery_channels);

-- Composite index for user notification queries
CREATE INDEX idx_notifications_user_type_read ON notifications(user_id, type, is_read, created_at DESC);

-- GIN index for JSONB metadata queries
CREATE INDEX idx_notifications_metadata ON notifications USING GIN(metadata);
CREATE INDEX idx_notifications_action_data ON notifications USING GIN(action_data);

-- Function to mark notification as read
CREATE OR REPLACE FUNCTION mark_notification_read(p_notification_id BIGINT) RETURNS BOOLEAN AS $$
BEGIN
    UPDATE notifications
    SET is_read = TRUE,
        read_at = CURRENT_TIMESTAMP
    WHERE id = p_notification_id AND is_read = FALSE;
    
    RETURN FOUND;
END;
$$ LANGUAGE plpgsql;

-- Function to mark all user notifications as read
CREATE OR REPLACE FUNCTION mark_all_notifications_read(p_user_id UUID) RETURNS INTEGER AS $$  -- CHANGED: BIGINT → UUID
DECLARE
    v_count INTEGER;
BEGIN
    UPDATE notifications
    SET is_read = TRUE,
        read_at = CURRENT_TIMESTAMP
    WHERE user_id = p_user_id AND is_read = FALSE;
    
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- Function to delete expired notifications (to be called by cron job)
CREATE OR REPLACE FUNCTION delete_expired_notifications() RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    DELETE FROM notifications
    WHERE expires_at IS NOT NULL AND expires_at < CURRENT_TIMESTAMP;
    
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- Function to delete old read notifications (cleanup, keep last 90 days)
CREATE OR REPLACE FUNCTION cleanup_old_notifications(p_days INTEGER DEFAULT 90) RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    DELETE FROM notifications
    WHERE is_read = TRUE 
    AND created_at < CURRENT_TIMESTAMP - (p_days || ' days')::INTERVAL;
    
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- Add comments for documentation
COMMENT ON TABLE notifications IS 'User notifications for in-app alerts and push notifications';
COMMENT ON COLUMN notifications.type IS 'Notification category/type for filtering and routing';
COMMENT ON COLUMN notifications.delivery_channels IS 'Array of channels where notification should be delivered';
COMMENT ON COLUMN notifications.action_type IS 'Deep link action when notification is clicked';
COMMENT ON COLUMN notifications.action_data IS 'JSONB data required for the action (e.g., IDs, parameters)';
COMMENT ON COLUMN notifications.expires_at IS 'Automatic expiry timestamp for time-sensitive notifications';