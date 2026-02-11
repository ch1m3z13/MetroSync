-- V9__Add_Verification_Tables.sql
-- Add user verification system (NIN, employment, driver documents)

-- ==================== USER PROFILES TABLE ====================
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    
    -- NIN Verification
    nin VARCHAR(11),  -- Nigerian National Identification Number
    nin_verified BOOLEAN NOT NULL DEFAULT false,
    nin_verified_at TIMESTAMP,
    
    -- Selfie Verification
    selfie_url VARCHAR(500),
    selfie_verified BOOLEAN NOT NULL DEFAULT false,
    selfie_verified_at TIMESTAMP,
    
    -- Address Verification
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100) DEFAULT 'FCT',  -- Default to Federal Capital Territory
    postal_code VARCHAR(10),
    address_verified BOOLEAN NOT NULL DEFAULT false,
    address_verified_at TIMESTAMP,
    
    -- Emergency Contact
    emergency_contact_name VARCHAR(100),
    emergency_contact_phone VARCHAR(20),
    emergency_contact_relationship VARCHAR(50),
    
    -- Overall Verification Status
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    verification_notes TEXT,
    
    -- Standard audit fields
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_verification_status CHECK (
        verification_status IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED')
    )
);

-- Indexes
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_nin ON user_profiles(nin) WHERE nin IS NOT NULL;
CREATE INDEX idx_user_profiles_status ON user_profiles(verification_status);

-- Trigger
CREATE TRIGGER user_profiles_updated_at
    BEFORE UPDATE ON user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- ==================== EMPLOYMENT INFO TABLE ====================
CREATE TABLE employment_info (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    
    -- Company Information
    company_name VARCHAR(255) NOT NULL,
    company_address VARCHAR(500),
    company_phone VARCHAR(20),
    company_email VARCHAR(100),
    
    -- Work Location (for route matching)
    work_location geometry(Point, 4326),
    work_address VARCHAR(500),
    
    -- Employment Details
    job_title VARCHAR(100),
    department VARCHAR(100),
    employee_id VARCHAR(50),
    employment_type VARCHAR(20),  -- FULL_TIME, PART_TIME, CONTRACT
    start_date DATE,
    
    -- Verification
    is_verified BOOLEAN NOT NULL DEFAULT false,
    verified_at TIMESTAMP,
    verification_method VARCHAR(50),  -- EMAIL, PHONE, LETTER, ID_CARD
    verification_document_url VARCHAR(500),
    
    -- Standard audit fields
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_employment_type CHECK (
        employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'SELF_EMPLOYED')
    )
);

-- Indexes
CREATE INDEX idx_employment_user_id ON employment_info(user_id);
CREATE INDEX idx_employment_verified ON employment_info(is_verified) WHERE is_verified = true;
CREATE INDEX idx_employment_work_location ON employment_info USING GIST(work_location);

-- Trigger
CREATE TRIGGER employment_info_updated_at
    BEFORE UPDATE ON employment_info
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- ==================== DRIVER DOCUMENTS TABLE ====================
CREATE TABLE driver_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- Driver's License
    license_number VARCHAR(50),
    license_issue_date DATE,
    license_expiry_date DATE,
    license_front_url VARCHAR(500),
    license_back_url VARCHAR(500),
    license_verified BOOLEAN NOT NULL DEFAULT false,
    license_verified_at TIMESTAMP,
    
    -- Vehicle Registration
    vehicle_registration_number VARCHAR(50),
    vehicle_registration_url VARCHAR(500),
    vehicle_registration_expiry DATE,
    registration_verified BOOLEAN NOT NULL DEFAULT false,
    registration_verified_at TIMESTAMP,
    
    -- Vehicle Insurance
    insurance_policy_number VARCHAR(50),
    insurance_provider VARCHAR(100),
    insurance_document_url VARCHAR(500),
    insurance_expiry_date DATE,
    insurance_verified BOOLEAN NOT NULL DEFAULT false,
    insurance_verified_at TIMESTAMP,
    
    -- Vehicle Photos
    vehicle_front_photo_url VARCHAR(500),
    vehicle_back_photo_url VARCHAR(500),
    vehicle_side_photo_url VARCHAR(500),
    vehicle_interior_photo_url VARCHAR(500),
    vehicle_photos_verified BOOLEAN NOT NULL DEFAULT false,
    vehicle_photos_verified_at TIMESTAMP,
    
    -- Road Worthiness Certificate (Nigeria-specific)
    roadworthiness_certificate_number VARCHAR(50),
    roadworthiness_document_url VARCHAR(500),
    roadworthiness_expiry_date DATE,
    roadworthiness_verified BOOLEAN NOT NULL DEFAULT false,
    roadworthiness_verified_at TIMESTAMP,
    
    -- Overall Document Status
    document_status VARCHAR(20) NOT NULL DEFAULT 'INCOMPLETE',
    verification_notes TEXT,
    
    -- Standard audit fields
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_document_status CHECK (
        document_status IN ('INCOMPLETE', 'PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')
    )
);

-- Indexes
CREATE INDEX idx_driver_docs_user_id ON driver_documents(user_id);
CREATE INDEX idx_driver_docs_status ON driver_documents(document_status);
CREATE INDEX idx_driver_docs_license_expiry ON driver_documents(license_expiry_date);
CREATE INDEX idx_driver_docs_insurance_expiry ON driver_documents(insurance_expiry_date);

-- Trigger
CREATE TRIGGER driver_documents_updated_at
    BEFORE UPDATE ON driver_documents
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- ==================== HELPER FUNCTIONS ====================

/**
 * Calculate overall user verification status
 * Returns true if user has completed all required verifications
 */
CREATE OR REPLACE FUNCTION is_user_fully_verified(p_user_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    is_verified BOOLEAN;
    user_role VARCHAR;
BEGIN
    -- Get user role
    SELECT roles INTO user_role
    FROM users
    WHERE id = p_user_id;
    
    -- Check basic profile verification
    SELECT 
        COALESCE(nin_verified, false) AND 
        COALESCE(selfie_verified, false) AND
        verification_status = 'VERIFIED'
    INTO is_verified
    FROM user_profiles
    WHERE user_id = p_user_id;
    
    IF NOT is_verified THEN
        RETURN false;
    END IF;
    
    -- If driver, check driver documents
    IF user_role LIKE '%DRIVER%' THEN
        SELECT 
            COALESCE(license_verified, false) AND
            COALESCE(registration_verified, false) AND
            COALESCE(insurance_verified, false) AND
            document_status = 'VERIFIED'
        INTO is_verified
        FROM driver_documents
        WHERE user_id = p_user_id;
        
        IF NOT is_verified THEN
            RETURN false;
        END IF;
    END IF;
    
    RETURN true;
END;
$$ LANGUAGE plpgsql;

/**
 * Get verification summary for a user
 */
CREATE OR REPLACE FUNCTION get_verification_summary(p_user_id UUID)
RETURNS TABLE (
    nin_verified BOOLEAN,
    selfie_verified BOOLEAN,
    employment_verified BOOLEAN,
    license_verified BOOLEAN,
    vehicle_verified BOOLEAN,
    overall_status VARCHAR
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COALESCE(up.nin_verified, false) as nin_verified,
        COALESCE(up.selfie_verified, false) as selfie_verified,
        COALESCE(ei.is_verified, false) as employment_verified,
        COALESCE(dd.license_verified, false) as license_verified,
        COALESCE(
            dd.registration_verified AND dd.insurance_verified, 
            false
        ) as vehicle_verified,
        CASE 
            WHEN is_user_fully_verified(p_user_id) THEN 'FULLY_VERIFIED'
            WHEN up.verification_status = 'PENDING' THEN 'PENDING'
            WHEN up.verification_status = 'REJECTED' THEN 'REJECTED'
            ELSE 'INCOMPLETE'
        END as overall_status
    FROM users u
    LEFT JOIN user_profiles up ON up.user_id = u.id
    LEFT JOIN employment_info ei ON ei.user_id = u.id
    LEFT JOIN driver_documents dd ON dd.user_id = u.id
    WHERE u.id = p_user_id;
END;
$$ LANGUAGE plpgsql;

/**
 * Auto-create user profile when user registers
 */
CREATE OR REPLACE FUNCTION create_user_profile_on_registration()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO user_profiles (user_id)
    VALUES (NEW.id)
    ON CONFLICT (user_id) DO NOTHING;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_create_profile
    AFTER INSERT ON users
    FOR EACH ROW
    EXECUTE FUNCTION create_user_profile_on_registration();

/**
 * Check for expired documents and update status
 */
CREATE OR REPLACE FUNCTION check_expired_documents()
RETURNS INTEGER AS $$
DECLARE
    updated_count INTEGER;
BEGIN
    UPDATE driver_documents
    SET document_status = 'EXPIRED'
    WHERE document_status = 'VERIFIED'
    AND (
        license_expiry_date < CURRENT_DATE OR
        insurance_expiry_date < CURRENT_DATE OR
        roadworthiness_expiry_date < CURRENT_DATE
    );
    
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

-- ==================== VIEWS ====================

-- View for driver verification status dashboard
CREATE VIEW driver_verification_status AS
SELECT 
    u.id as driver_id,
    u.full_name,
    u.email,
    u.phone_number,
    
    -- Profile verification
    up.nin_verified,
    up.selfie_verified,
    up.verification_status as profile_status,
    
    -- Employment verification
    ei.is_verified as employment_verified,
    ei.company_name,
    
    -- Driver documents
    dd.license_verified,
    dd.registration_verified,
    dd.insurance_verified,
    dd.roadworthiness_verified,
    dd.document_status,
    
    -- Expiry warnings
    CASE 
        WHEN dd.license_expiry_date < CURRENT_DATE + INTERVAL '30 days' THEN true
        ELSE false
    END as license_expiring_soon,
    
    CASE 
        WHEN dd.insurance_expiry_date < CURRENT_DATE + INTERVAL '30 days' THEN true
        ELSE false
    END as insurance_expiring_soon,
    
    -- Overall status
    is_user_fully_verified(u.id) as fully_verified
    
FROM users u
LEFT JOIN user_profiles up ON up.user_id = u.id
LEFT JOIN employment_info ei ON ei.user_id = u.id
LEFT JOIN driver_documents dd ON dd.user_id = u.id
WHERE u.roles LIKE '%DRIVER%';

-- ==================== COMMENTS ====================

COMMENT ON TABLE user_profiles IS 
'Stores user verification data including NIN, selfie, and address verification.
All users (riders and drivers) must complete profile verification.';

COMMENT ON TABLE employment_info IS 
'Stores employment verification for trust and safety.
Helps verify user identity and enables workplace carpooling.';

COMMENT ON TABLE driver_documents IS 
'Stores all driver-specific documents (license, registration, insurance, etc.).
Required for drivers to activate their account and start offering rides.';

COMMENT ON COLUMN driver_documents.roadworthiness_certificate_number IS 
'Nigeria-specific requirement: Road Worthiness Certificate from VIO.
Required for all commercial vehicles.';

COMMENT ON FUNCTION is_user_fully_verified IS 
'Returns true if user has completed ALL required verifications.
For drivers, includes profile + documents. For riders, just profile.';