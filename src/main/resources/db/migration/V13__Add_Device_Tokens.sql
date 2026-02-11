-- V13__Add_Device_Tokens.sql
-- Add device token support for push notifications

-- Add device token columns to users table
ALTER TABLE users ADD COLUMN device_token VARCHAR(500);
ALTER TABLE users ADD COLUMN device_platform VARCHAR(20);
ALTER TABLE users ADD COLUMN device_updated_at TIMESTAMP;
ALTER TABLE users ADD COLUMN push_enabled BOOLEAN DEFAULT true;

-- Index for faster lookups
CREATE INDEX idx_users_device_token ON users(device_token) WHERE device_token IS NOT NULL;

-- Comments
COMMENT ON COLUMN users.device_token IS 'FCM device token for push notifications';
COMMENT ON COLUMN users.device_platform IS 'Device platform: ANDROID, IOS, WEB';
COMMENT ON COLUMN users.device_updated_at IS 'Last time device token was updated';
COMMENT ON COLUMN users.push_enabled IS 'Whether user has enabled push notifications';

-- Create notification delivery log table
CREATE TABLE notification_delivery_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_id UUID REFERENCES notifications(id) ON DELETE SET NULL,
    device_token VARCHAR(500) NOT NULL,
    fcm_message_id VARCHAR(255),
    title VARCHAR(255),
    message TEXT,
    notification_type VARCHAR(50),
    priority VARCHAR(20),
    data JSONB,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered BOOLEAN,
    delivery_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for notification delivery log
CREATE INDEX idx_notification_log_user ON notification_delivery_log(user_id, sent_at DESC);
CREATE INDEX idx_notification_log_sent_at ON notification_delivery_log(sent_at DESC);
CREATE INDEX idx_notification_log_fcm_id ON notification_delivery_log(fcm_message_id) WHERE fcm_message_id IS NOT NULL;

-- Comments
COMMENT ON TABLE notification_delivery_log IS 'Tracks push notification delivery status';
COMMENT ON COLUMN notification_delivery_log.fcm_message_id IS 'Message ID returned from Firebase Cloud Messaging';
COMMENT ON COLUMN notification_delivery_log.delivered IS 'Whether notification was successfully delivered';

-- Create topic subscriptions table (for broadcast notifications)
CREATE TABLE topic_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_token VARCHAR(500) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    subscribed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unsubscribed_at TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    UNIQUE(user_id, topic, device_token)
);

-- Indexes for topic subscriptions
CREATE INDEX idx_topic_subscriptions_user ON topic_subscriptions(user_id);
CREATE INDEX idx_topic_subscriptions_topic ON topic_subscriptions(topic, is_active);

-- Comments
COMMENT ON TABLE topic_subscriptions IS 'Tracks FCM topic subscriptions for broadcast notifications';
COMMENT ON COLUMN topic_subscriptions.topic IS 'Topic name (e.g., "drivers", "riders", "all_users")';

-- Common topics setup
-- Insert default topics (these are examples, adjust as needed)
-- Users can subscribe to these via app settings

-- View for active device tokens
CREATE VIEW active_device_tokens AS
SELECT 
    u.id as user_id,
    u.full_name,
    u.email,
    u.device_token,
    u.device_platform,
    u.device_updated_at,
    u.push_enabled,
    u.roles
FROM users u
WHERE u.device_token IS NOT NULL 
  AND u.push_enabled = true
  AND u.device_updated_at > CURRENT_TIMESTAMP - INTERVAL '30 days';

COMMENT ON VIEW active_device_tokens IS 'Users with valid, active device tokens for push notifications';

-- Function to clean up old device tokens
CREATE OR REPLACE FUNCTION cleanup_inactive_device_tokens()
RETURNS INTEGER AS $$
DECLARE
    cleaned_count INTEGER;
BEGIN
    -- Clear device tokens that haven't been updated in 60 days
    UPDATE users
    SET device_token = NULL,
        device_platform = NULL
    WHERE device_token IS NOT NULL
      AND (device_updated_at IS NULL 
           OR device_updated_at < CURRENT_TIMESTAMP - INTERVAL '60 days');
    
    GET DIAGNOSTICS cleaned_count = ROW_COUNT;
    
    RETURN cleaned_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_inactive_device_tokens IS 
'Removes device tokens that have not been updated in 60 days (likely inactive devices)';

-- Function to log notification delivery
CREATE OR REPLACE FUNCTION log_notification_delivery(
    p_user_id UUID,
    p_notification_id UUID,
    p_device_token VARCHAR,
    p_fcm_message_id VARCHAR,
    p_title VARCHAR,
    p_message TEXT,
    p_type VARCHAR,
    p_priority VARCHAR,
    p_data JSONB,
    p_delivered BOOLEAN,
    p_error TEXT
)
RETURNS UUID AS $$
DECLARE
    v_log_id UUID;
BEGIN
    INSERT INTO notification_delivery_log (
        user_id,
        notification_id,
        device_token,
        fcm_message_id,
        title,
        message,
        notification_type,
        priority,
        data,
        delivered,
        delivery_error
    ) VALUES (
        p_user_id,
        p_notification_id,
        p_device_token,
        p_fcm_message_id,
        p_title,
        p_message,
        p_type,
        p_priority,
        p_data,
        p_delivered,
        p_error
    )
    RETURNING id INTO v_log_id;
    
    RETURN v_log_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION log_notification_delivery IS 
'Creates a log entry for push notification delivery tracking';