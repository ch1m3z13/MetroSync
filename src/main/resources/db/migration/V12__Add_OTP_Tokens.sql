-- V12__Add_OTP_Tokens.sql
-- Add OTP tokens table for SMS-based phone verification
-- CommuteNG Specification Requirement

-- ==================== OTP TOKENS TABLE ====================

CREATE TABLE otp_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- User identification
    phone_number VARCHAR(20) NOT NULL,  -- E.164 format: +234XXXXXXXXXX
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,  -- NULL for new registrations
    
    -- OTP details
    otp_code VARCHAR(6) NOT NULL,  -- 6-digit numeric code
    otp_hash VARCHAR(255) NOT NULL,  -- Hashed version for security
    
    -- Purpose of OTP
    purpose VARCHAR(50) NOT NULL,  -- REGISTRATION, LOGIN, PHONE_VERIFICATION, PASSWORD_RESET, TRANSACTION_CONFIRM
    
    -- Expiration and attempt tracking
    expires_at TIMESTAMP NOT NULL,
    attempts INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 3,
    
    -- Status tracking
    is_verified BOOLEAN DEFAULT false,
    verified_at TIMESTAMP,
    
    -- Rate limiting
    ip_address VARCHAR(45),  -- IPv4 or IPv6
    device_info JSONB,  -- Device fingerprint, user agent, etc.
    
    -- SMS delivery tracking
    sms_provider VARCHAR(50) DEFAULT 'TERMII',  -- TERMII, TWILIO, etc.
    sms_message_id VARCHAR(100),  -- Provider's message ID
    sms_status VARCHAR(50),  -- QUEUED, SENT, DELIVERED, FAILED
    sms_sent_at TIMESTAMP,
    sms_delivered_at TIMESTAMP,
    sms_error TEXT,
    
    -- Standard audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_otp_code_format CHECK (otp_code ~ '^[0-9]{6}$'),
    CONSTRAINT chk_phone_number_format CHECK (phone_number ~ '^\+[1-9][0-9]{1,14}$'),
    CONSTRAINT chk_otp_purpose CHECK (purpose IN (
        'REGISTRATION',
        'LOGIN',
        'PHONE_VERIFICATION',
        'PASSWORD_RESET',
        'TRANSACTION_CONFIRM',
        'PHONE_CHANGE'
    )),
    CONSTRAINT chk_sms_status CHECK (sms_status IN (
        'QUEUED',
        'SENT',
        'DELIVERED',
        'FAILED',
        'REJECTED'
    ))
);

-- ==================== INDEXES ====================

-- Phone number lookup - CRITICAL for OTP verification
CREATE INDEX idx_otp_phone ON otp_tokens(phone_number);

-- Active OTP lookup (not expired, not verified)
CREATE INDEX idx_otp_active ON otp_tokens(phone_number, purpose, is_verified, expires_at) 
    WHERE is_verified = false AND expires_at > CURRENT_TIMESTAMP;

-- User OTP history
CREATE INDEX idx_otp_user ON otp_tokens(user_id)
    WHERE user_id IS NOT NULL;

-- Expiration cleanup
CREATE INDEX idx_otp_expires ON otp_tokens(expires_at);

-- IP-based rate limiting
CREATE INDEX idx_otp_ip_created ON otp_tokens(ip_address, created_at);

-- SMS delivery status tracking
CREATE INDEX idx_otp_sms_status ON otp_tokens(sms_status, sms_sent_at);

-- ==================== TRIGGERS ====================

-- Update timestamp trigger
CREATE TRIGGER otp_tokens_updated_at
    BEFORE UPDATE ON otp_tokens
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Auto-set verified_at when is_verified is changed to true
CREATE OR REPLACE FUNCTION set_otp_verified_time()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_verified = true AND OLD.is_verified = false THEN
        NEW.verified_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER otp_set_verified_time
    BEFORE UPDATE ON otp_tokens
    FOR EACH ROW
    WHEN (NEW.is_verified = true AND OLD.is_verified = false)
    EXECUTE FUNCTION set_otp_verified_time();

-- ==================== HELPER FUNCTIONS ====================

/**
 * Generate a random 6-digit OTP code
 */
CREATE OR REPLACE FUNCTION generate_otp_code()
RETURNS VARCHAR AS $$
BEGIN
    RETURN LPAD(FLOOR(RANDOM() * 1000000)::TEXT, 6, '0');
END;
$$ LANGUAGE plpgsql;

/**
 * Hash OTP code for secure storage
 */
CREATE OR REPLACE FUNCTION hash_otp_code(p_otp_code VARCHAR)
RETURNS VARCHAR AS $$
BEGIN
    -- Use PostgreSQL's crypt function with bcrypt
    -- Note: This requires pgcrypto extension
    RETURN crypt(p_otp_code, gen_salt('bf', 8));
END;
$$ LANGUAGE plpgsql;

/**
 * Verify OTP code against hash
 */
CREATE OR REPLACE FUNCTION verify_otp_code(p_otp_code VARCHAR, p_otp_hash VARCHAR)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN p_otp_hash = crypt(p_otp_code, p_otp_hash);
END;
$$ LANGUAGE plpgsql;

/**
 * Create a new OTP token
 */
CREATE OR REPLACE FUNCTION create_otp_token(
    p_phone_number VARCHAR,
    p_purpose VARCHAR,
    p_user_id UUID DEFAULT NULL,
    p_ip_address VARCHAR DEFAULT NULL,
    p_device_info JSONB DEFAULT NULL,
    p_validity_minutes INTEGER DEFAULT 10
)
RETURNS TABLE(
    token_id UUID,
    otp_code VARCHAR,
    expires_at TIMESTAMP
) AS $$
DECLARE
    v_token_id UUID;
    v_otp_code VARCHAR;
    v_otp_hash VARCHAR;
    v_expires_at TIMESTAMP;
BEGIN
    -- Generate OTP code
    v_otp_code := generate_otp_code();
    v_otp_hash := hash_otp_code(v_otp_code);
    v_expires_at := CURRENT_TIMESTAMP + (p_validity_minutes || ' minutes')::INTERVAL;
    
    -- Invalidate any existing active OTP for this phone/purpose
    UPDATE otp_tokens
    SET is_verified = true  -- Mark as "consumed" to prevent reuse
    WHERE phone_number = p_phone_number
    AND purpose = p_purpose
    AND is_verified = false
    AND expires_at > CURRENT_TIMESTAMP;
    
    -- Create new OTP token
    INSERT INTO otp_tokens (
        phone_number,
        user_id,
        otp_code,
        otp_hash,
        purpose,
        expires_at,
        ip_address,
        device_info,
        sms_status
    ) VALUES (
        p_phone_number,
        p_user_id,
        v_otp_code,
        v_otp_hash,
        p_purpose,
        v_expires_at,
        p_ip_address,
        p_device_info,
        'QUEUED'
    )
    RETURNING id INTO v_token_id;
    
    -- Return the token details (OTP code should be sent via SMS, not stored)
    RETURN QUERY SELECT v_token_id, v_otp_code, v_expires_at;
END;
$$ LANGUAGE plpgsql;

/**
 * Verify an OTP token
 */
CREATE OR REPLACE FUNCTION verify_otp_token(
    p_phone_number VARCHAR,
    p_otp_code VARCHAR,
    p_purpose VARCHAR
)
RETURNS TABLE(
    success BOOLEAN,
    token_id UUID,
    user_id UUID,
    error_message TEXT
) AS $$
DECLARE
    v_token RECORD;
BEGIN
    -- Find the most recent active OTP for this phone/purpose
    SELECT * INTO v_token
    FROM otp_tokens
    WHERE phone_number = p_phone_number
    AND purpose = p_purpose
    AND is_verified = false
    AND expires_at > CURRENT_TIMESTAMP
    ORDER BY created_at DESC
    LIMIT 1;
    
    -- Check if token exists
    IF v_token IS NULL THEN
        RETURN QUERY SELECT false, NULL::UUID, NULL::UUID, 'No valid OTP found'::TEXT;
        RETURN;
    END IF;
    
    -- Check if max attempts exceeded
    IF v_token.attempts >= v_token.max_attempts THEN
        RETURN QUERY SELECT false, v_token.id, v_token.user_id, 'Maximum verification attempts exceeded'::TEXT;
        RETURN;
    END IF;
    
    -- Increment attempt counter
    UPDATE otp_tokens
    SET attempts = attempts + 1
    WHERE id = v_token.id;
    
    -- Verify OTP code
    IF verify_otp_code(p_otp_code, v_token.otp_hash) THEN
        -- Mark as verified
        UPDATE otp_tokens
        SET is_verified = true,
            verified_at = CURRENT_TIMESTAMP
        WHERE id = v_token.id;
        
        RETURN QUERY SELECT true, v_token.id, v_token.user_id, NULL::TEXT;
    ELSE
        RETURN QUERY SELECT false, v_token.id, v_token.user_id, 'Invalid OTP code'::TEXT;
    END IF;
END;
$$ LANGUAGE plpgsql;

/**
 * Check rate limit for OTP requests
 * Returns true if rate limit is not exceeded
 */
CREATE OR REPLACE FUNCTION check_otp_rate_limit(
    p_phone_number VARCHAR,
    p_ip_address VARCHAR,
    p_max_per_hour INTEGER DEFAULT 5,
    p_max_per_day INTEGER DEFAULT 10
)
RETURNS TABLE(
    allowed BOOLEAN,
    hourly_count INTEGER,
    daily_count INTEGER,
    error_message TEXT
) AS $$
DECLARE
    v_hourly_count INTEGER;
    v_daily_count INTEGER;
BEGIN
    -- Count OTPs sent in last hour
    SELECT COUNT(*) INTO v_hourly_count
    FROM otp_tokens
    WHERE (phone_number = p_phone_number OR ip_address = p_ip_address)
    AND created_at > CURRENT_TIMESTAMP - INTERVAL '1 hour';
    
    -- Count OTPs sent in last 24 hours
    SELECT COUNT(*) INTO v_daily_count
    FROM otp_tokens
    WHERE (phone_number = p_phone_number OR ip_address = p_ip_address)
    AND created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours';
    
    -- Check limits
    IF v_hourly_count >= p_max_per_hour THEN
        RETURN QUERY SELECT 
            false, 
            v_hourly_count, 
            v_daily_count, 
            'Too many OTP requests. Please try again in an hour.'::TEXT;
    ELSIF v_daily_count >= p_max_per_day THEN
        RETURN QUERY SELECT 
            false, 
            v_hourly_count, 
            v_daily_count, 
            'Daily OTP limit exceeded. Please try again tomorrow.'::TEXT;
    ELSE
        RETURN QUERY SELECT 
            true, 
            v_hourly_count, 
            v_daily_count, 
            NULL::TEXT;
    END IF;
END;
$$ LANGUAGE plpgsql;

/**
 * Update SMS delivery status
 */
CREATE OR REPLACE FUNCTION update_sms_status(
    p_token_id UUID,
    p_status VARCHAR,
    p_message_id VARCHAR DEFAULT NULL,
    p_error TEXT DEFAULT NULL
)
RETURNS BOOLEAN AS $$
BEGIN
    UPDATE otp_tokens
    SET sms_status = p_status,
        sms_message_id = COALESCE(p_message_id, sms_message_id),
        sms_error = p_error,
        sms_sent_at = CASE WHEN p_status IN ('SENT', 'QUEUED') 
                          THEN COALESCE(sms_sent_at, CURRENT_TIMESTAMP) 
                          ELSE sms_sent_at END,
        sms_delivered_at = CASE WHEN p_status = 'DELIVERED' 
                                THEN CURRENT_TIMESTAMP 
                                ELSE sms_delivered_at END
    WHERE id = p_token_id;
    
    RETURN FOUND;
END;
$$ LANGUAGE plpgsql;

/**
 * Clean up expired OTP tokens
 * Should be run periodically (e.g., daily)
 */
CREATE OR REPLACE FUNCTION cleanup_expired_otps()
RETURNS INTEGER AS $$
DECLARE
    v_deleted_count INTEGER;
BEGIN
    -- Delete OTPs expired more than 7 days ago
    DELETE FROM otp_tokens
    WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '7 days';
    
    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
    
    RETURN v_deleted_count;
END;
$$ LANGUAGE plpgsql;

/**
 * Get OTP statistics for monitoring
 */
CREATE OR REPLACE FUNCTION get_otp_statistics(
    p_start_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP - INTERVAL '24 hours',
    p_end_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
RETURNS TABLE(
    purpose VARCHAR,
    total_sent INTEGER,
    total_verified INTEGER,
    total_expired INTEGER,
    total_failed INTEGER,
    avg_verification_time_seconds NUMERIC,
    success_rate NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        o.purpose,
        COUNT(*)::INTEGER as total_sent,
        COUNT(*) FILTER (WHERE o.is_verified = true)::INTEGER as total_verified,
        COUNT(*) FILTER (WHERE o.expires_at < CURRENT_TIMESTAMP AND o.is_verified = false)::INTEGER as total_expired,
        COUNT(*) FILTER (WHERE o.attempts >= o.max_attempts AND o.is_verified = false)::INTEGER as total_failed,
        AVG(EXTRACT(EPOCH FROM (o.verified_at - o.created_at)))::NUMERIC as avg_verification_time_seconds,
        ROUND(
            (COUNT(*) FILTER (WHERE o.is_verified = true)::NUMERIC / NULLIF(COUNT(*), 0) * 100),
            2
        ) as success_rate
    FROM otp_tokens o
    WHERE o.created_at BETWEEN p_start_date AND p_end_date
    GROUP BY o.purpose
    ORDER BY total_sent DESC;
END;
$$ LANGUAGE plpgsql;

-- ==================== VIEWS ====================

/**
 * Active OTP tokens view (for admin monitoring)
 */
CREATE VIEW active_otp_tokens AS
SELECT 
    id,
    phone_number,
    user_id,
    purpose,
    attempts,
    max_attempts,
    expires_at,
    EXTRACT(EPOCH FROM (expires_at - CURRENT_TIMESTAMP))::INTEGER as seconds_until_expiry,
    sms_status,
    sms_sent_at,
    created_at,
    CASE 
        WHEN expires_at < CURRENT_TIMESTAMP THEN 'EXPIRED'
        WHEN attempts >= max_attempts THEN 'MAX_ATTEMPTS'
        WHEN is_verified THEN 'VERIFIED'
        ELSE 'ACTIVE'
    END as token_status
FROM otp_tokens
WHERE is_verified = false
AND created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'
ORDER BY created_at DESC;

/**
 * OTP delivery success rate by provider
 */
CREATE VIEW otp_delivery_stats AS
SELECT 
    sms_provider,
    COUNT(*) as total_sent,
    COUNT(*) FILTER (WHERE sms_status = 'DELIVERED') as delivered,
    COUNT(*) FILTER (WHERE sms_status = 'FAILED') as failed,
    COUNT(*) FILTER (WHERE sms_status = 'SENT') as sent_not_confirmed,
    ROUND(
        (COUNT(*) FILTER (WHERE sms_status = 'DELIVERED')::NUMERIC / NULLIF(COUNT(*), 0) * 100),
        2
    ) as delivery_rate,
    AVG(EXTRACT(EPOCH FROM (sms_delivered_at - sms_sent_at))) as avg_delivery_time_seconds
FROM otp_tokens
WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '7 days'
GROUP BY sms_provider;

-- ==================== INITIAL DATA ====================

-- Enable pgcrypto extension for OTP hashing
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ==================== COMMENTS ====================

COMMENT ON TABLE otp_tokens IS 
'Stores OTP tokens for SMS-based verification.
OTPs are valid for 10 minutes by default.
Maximum 3 verification attempts per OTP.
Expired OTPs older than 7 days are automatically cleaned up.';

COMMENT ON COLUMN otp_tokens.otp_code IS 
'Plain text OTP code (for SMS sending only).
Should NOT be exposed via API after creation.';

COMMENT ON COLUMN otp_tokens.otp_hash IS 
'Bcrypt hash of OTP code for secure verification.
Used to verify user input without storing plain text.';

COMMENT ON COLUMN otp_tokens.phone_number IS 
'Phone number in E.164 format: +234XXXXXXXXXX
Must match the international format for SMS delivery.';

COMMENT ON COLUMN otp_tokens.purpose IS 
'Purpose of the OTP:
- REGISTRATION: New user signup
- LOGIN: Passwordless login
- PHONE_VERIFICATION: Verify/change phone number
- PASSWORD_RESET: Reset forgotten password
- TRANSACTION_CONFIRM: Confirm high-value transaction
- PHONE_CHANGE: Change phone number';

COMMENT ON FUNCTION create_otp_token IS 
'Creates a new OTP token and invalidates any existing active OTP.
Returns the OTP code (to be sent via SMS) and expiration time.
Example: SELECT * FROM create_otp_token(''+2348012345678'', ''REGISTRATION'');';

COMMENT ON FUNCTION verify_otp_token IS 
'Verifies an OTP code against the stored hash.
Increments attempt counter and marks as verified if successful.
Returns success status, token_id, user_id, and error message.
Example: SELECT * FROM verify_otp_token(''+2348012345678'', ''123456'', ''REGISTRATION'');';

COMMENT ON FUNCTION check_otp_rate_limit IS 
'Checks if user/IP has exceeded OTP request limits.
Default limits: 5 per hour, 10 per day.
Returns allowed status and current counts.';

COMMENT ON FUNCTION cleanup_expired_otps IS 
'Maintenance function to delete old expired OTP tokens.
Should be called daily by scheduled job.
Deletes OTPs expired more than 7 days ago.';