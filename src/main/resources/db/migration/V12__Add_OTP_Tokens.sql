-- V12: Add OTP Tokens System
-- Table: otp_tokens

-- OTP Tokens (SMS Verification Codes)
CREATE TABLE otp_tokens (
    id BIGSERIAL PRIMARY KEY,
    
    -- User Information
    phone_number VARCHAR(15) NOT NULL,  -- Can be used before user registration
    user_id UUID,  -- CHANGED: BIGINT → UUID (NULL for pre-registration OTPs)
    
    -- OTP Details
    code VARCHAR(6) NOT NULL,  -- 6-digit numeric code
    
    -- Purpose/Type
    purpose VARCHAR(50) NOT NULL,  -- REGISTRATION, LOGIN, PHONE_VERIFICATION, PASSWORD_RESET, TRANSACTION_VERIFICATION
    
    -- Validation
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    
    attempts INTEGER DEFAULT 0,  -- Failed verification attempts
    max_attempts INTEGER DEFAULT 3,
    
    -- Expiry
    expires_at TIMESTAMP NOT NULL,
    
    -- IP and Device Info (security)
    ip_address VARCHAR(45),  -- IPv6 compatible
    user_agent TEXT,
    device_id VARCHAR(255),
    
    -- Metadata
    metadata JSONB,  -- Additional context data
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_otp_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes for otp_tokens
CREATE INDEX idx_otp_tokens_phone_number ON otp_tokens(phone_number);
CREATE INDEX idx_otp_tokens_user_id ON otp_tokens(user_id);
CREATE INDEX idx_otp_tokens_code ON otp_tokens(code);
CREATE INDEX idx_otp_tokens_purpose ON otp_tokens(purpose);
CREATE INDEX idx_otp_tokens_expires_at ON otp_tokens(expires_at);
CREATE INDEX idx_otp_tokens_created_at ON otp_tokens(created_at DESC);

-- Composite index for OTP verification queries
CREATE INDEX idx_otp_tokens_phone_code_purpose ON otp_tokens(phone_number, code, purpose, expires_at) 
    WHERE is_used = FALSE;

-- Partial index for active (unused) OTPs
-- Note: Can't use expires_at > CURRENT_TIMESTAMP in index predicate (not IMMUTABLE)
-- Queries will still filter by expiry at runtime
CREATE INDEX idx_otp_tokens_active ON otp_tokens(phone_number, purpose, created_at DESC) 
    WHERE is_used = FALSE;

-- Function to generate OTP code
CREATE OR REPLACE FUNCTION generate_otp_code() RETURNS VARCHAR(6) AS $$
BEGIN
    RETURN LPAD(FLOOR(RANDOM() * 1000000)::TEXT, 6, '0');
END;
$$ LANGUAGE plpgsql;

-- Function to create OTP token
CREATE OR REPLACE FUNCTION create_otp_token(
    p_phone_number VARCHAR(15),
    p_user_id UUID,  -- CHANGED: BIGINT → UUID
    p_purpose VARCHAR(50),
    p_validity_minutes INTEGER DEFAULT 10,
    p_ip_address VARCHAR(45) DEFAULT NULL,
    p_user_agent TEXT DEFAULT NULL,
    p_device_id VARCHAR(255) DEFAULT NULL
) RETURNS TABLE(otp_id BIGINT, otp_code VARCHAR(6)) AS $$
DECLARE
    v_otp_id BIGINT;
    v_otp_code VARCHAR(6);
    v_expires_at TIMESTAMP;
BEGIN
    -- Generate OTP code
    v_otp_code := generate_otp_code();
    
    -- Calculate expiry
    v_expires_at := CURRENT_TIMESTAMP + (p_validity_minutes || ' minutes')::INTERVAL;
    
    -- Invalidate any existing active OTPs for same phone + purpose
    UPDATE otp_tokens
    SET is_used = TRUE,
        used_at = CURRENT_TIMESTAMP
    WHERE phone_number = p_phone_number 
    AND purpose = p_purpose 
    AND is_used = FALSE 
    AND expires_at > CURRENT_TIMESTAMP;
    
    -- Insert new OTP
    INSERT INTO otp_tokens (
        phone_number, user_id, code, purpose, expires_at,
        ip_address, user_agent, device_id
    ) VALUES (
        p_phone_number, p_user_id, v_otp_code, p_purpose, v_expires_at,
        p_ip_address, p_user_agent, p_device_id
    ) RETURNING id INTO v_otp_id;
    
    RETURN QUERY SELECT v_otp_id, v_otp_code;
END;
$$ LANGUAGE plpgsql;

-- Function to verify OTP
CREATE OR REPLACE FUNCTION verify_otp(
    p_phone_number VARCHAR(15),
    p_code VARCHAR(6),
    p_purpose VARCHAR(50)
) RETURNS TABLE(
    is_valid BOOLEAN,
    otp_id BIGINT,
    user_id UUID,  -- CHANGED: BIGINT → UUID
    message TEXT
) AS $$
DECLARE
    v_otp_record RECORD;
BEGIN
    -- Find the OTP record
    SELECT * INTO v_otp_record
    FROM otp_tokens
    WHERE phone_number = p_phone_number
    AND code = p_code
    AND purpose = p_purpose
    AND is_used = FALSE
    ORDER BY created_at DESC
    LIMIT 1;
    
    -- OTP not found
    IF NOT FOUND THEN
        RETURN QUERY SELECT FALSE, NULL::BIGINT, NULL::UUID, 'Invalid OTP code'::TEXT;  -- CHANGED: NULL::BIGINT → NULL::UUID
        RETURN;
    END IF;
    
    -- Check if expired
    IF v_otp_record.expires_at < CURRENT_TIMESTAMP THEN
        RETURN QUERY SELECT FALSE, v_otp_record.id, v_otp_record.user_id, 'OTP has expired'::TEXT;
        RETURN;
    END IF;
    
    -- Check max attempts
    IF v_otp_record.attempts >= v_otp_record.max_attempts THEN
        RETURN QUERY SELECT FALSE, v_otp_record.id, v_otp_record.user_id, 'Maximum verification attempts exceeded'::TEXT;
        RETURN;
    END IF;
    
    -- Mark as used
    UPDATE otp_tokens
    SET is_used = TRUE,
        used_at = CURRENT_TIMESTAMP
    WHERE id = v_otp_record.id;
    
    -- Return success
    RETURN QUERY SELECT TRUE, v_otp_record.id, v_otp_record.user_id, 'OTP verified successfully'::TEXT;
END;
$$ LANGUAGE plpgsql;

-- Function to increment failed attempts
CREATE OR REPLACE FUNCTION increment_otp_attempts(
    p_phone_number VARCHAR(15),
    p_code VARCHAR(6),
    p_purpose VARCHAR(50)
) RETURNS BOOLEAN AS $$
BEGIN
    UPDATE otp_tokens
    SET attempts = attempts + 1
    WHERE phone_number = p_phone_number
    AND code = p_code
    AND purpose = p_purpose
    AND is_used = FALSE
    AND expires_at > CURRENT_TIMESTAMP;
    
    RETURN FOUND;
END;
$$ LANGUAGE plpgsql;

-- Function to clean up expired OTPs (to be called by cron job)
CREATE OR REPLACE FUNCTION cleanup_expired_otps() RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    DELETE FROM otp_tokens
    WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '24 hours';
    
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- Function to get recent OTP statistics for rate limiting
CREATE OR REPLACE FUNCTION get_otp_send_count(
    p_phone_number VARCHAR(15),
    p_minutes INTEGER DEFAULT 60
) RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM otp_tokens
    WHERE phone_number = p_phone_number
    AND created_at > CURRENT_TIMESTAMP - (p_minutes || ' minutes')::INTERVAL;
    
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- Add comments for documentation
COMMENT ON TABLE otp_tokens IS 'One-Time Password tokens for phone verification and authentication';
COMMENT ON COLUMN otp_tokens.code IS '6-digit numeric OTP code';
COMMENT ON COLUMN otp_tokens.purpose IS 'Purpose of OTP: REGISTRATION, LOGIN, PHONE_VERIFICATION, PASSWORD_RESET, etc.';
COMMENT ON COLUMN otp_tokens.attempts IS 'Number of failed verification attempts';
COMMENT ON COLUMN otp_tokens.max_attempts IS 'Maximum allowed verification attempts before OTP is invalidated';
COMMENT ON COLUMN otp_tokens.expires_at IS 'OTP expiry timestamp (typically 10 minutes from creation)';
COMMENT ON FUNCTION create_otp_token IS 'Creates a new OTP token and invalidates any existing active OTPs for the same phone+purpose';
COMMENT ON FUNCTION verify_otp IS 'Verifies an OTP code and marks it as used if valid';
COMMENT ON FUNCTION get_otp_send_count IS 'Returns count of OTPs sent to a phone number in the last N minutes (for rate limiting)';