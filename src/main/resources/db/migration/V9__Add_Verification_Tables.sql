-- V9: Add User Verification Tables
-- Tables: user_profiles, employment_info, driver_documents

-- User Profiles (NIN, Selfie, Identity Verification)
CREATE TABLE user_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,  -- CHANGED: BIGINT → UUID
    
    -- Identity Information
    nin VARCHAR(11),  -- Nigerian National ID Number (11 digits)
    nin_verified BOOLEAN DEFAULT FALSE,
    nin_verified_at TIMESTAMP,
    
    -- Selfie Verification
    selfie_url VARCHAR(500),
    selfie_verified BOOLEAN DEFAULT FALSE,
    selfie_verified_at TIMESTAMP,
    
    -- Personal Details
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    date_of_birth DATE,
    gender VARCHAR(10),  -- MALE, FEMALE, OTHER
    
    -- Address
    home_address TEXT,
    home_city VARCHAR(100),
    home_state VARCHAR(50),
    home_location GEOMETRY(Point, 4326),  -- PostGIS point for home
    
    -- Verification Status
    verification_status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, VERIFIED, REJECTED
    verification_notes TEXT,
    verified_by UUID,  -- CHANGED: BIGINT → UUID
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_profiles_verified_by FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Indexes for user_profiles
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_nin ON user_profiles(nin);
CREATE INDEX idx_user_profiles_verification_status ON user_profiles(verification_status);
CREATE INDEX idx_user_profiles_home_location ON user_profiles USING GIST(home_location);

-- Employment Information (Work Verification)
CREATE TABLE employment_info (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,  -- CHANGED: BIGINT → UUID
    
    -- Company Details
    company_name VARCHAR(200) NOT NULL,
    company_email VARCHAR(255),  -- Official company email
    company_phone VARCHAR(20),
    
    -- Work Location
    work_address TEXT NOT NULL,
    work_city VARCHAR(100),
    work_state VARCHAR(50),
    work_location GEOMETRY(Point, 4326),  -- PostGIS point for office
    
    -- Employment Proof Documents
    id_card_url VARCHAR(500),  -- Company ID card photo
    employment_letter_url VARCHAR(500),  -- Employment letter/offer letter
    
    -- Employment Details
    job_title VARCHAR(150),
    department VARCHAR(100),
    employee_id VARCHAR(50),
    start_date DATE,
    
    -- Work Schedule
    work_days VARCHAR(50),  -- e.g., "MON,TUE,WED,THU,FRI"
    work_start_time TIME,
    work_end_time TIME,
    
    -- Verification Status
    verification_status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, VERIFIED, REJECTED
    verification_notes TEXT,
    verified_by UUID,  -- CHANGED: BIGINT → UUID
    verified_at TIMESTAMP,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_employment_info_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_employment_info_verified_by FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Indexes for employment_info
CREATE INDEX idx_employment_info_user_id ON employment_info(user_id);
CREATE INDEX idx_employment_info_company ON employment_info(company_name);
CREATE INDEX idx_employment_info_work_location ON employment_info USING GIST(work_location);
CREATE INDEX idx_employment_info_verification_status ON employment_info(verification_status);

-- Driver Documents (License, Vehicle Registration, Insurance)
CREATE TABLE driver_documents (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,  -- CHANGED: BIGINT → UUID
    
    -- Driver's License
    license_number VARCHAR(50) NOT NULL,
    license_expiry_date DATE NOT NULL,
    license_front_url VARCHAR(500),
    license_back_url VARCHAR(500),
    license_verified BOOLEAN DEFAULT FALSE,
    license_verified_at TIMESTAMP,
    
    -- Vehicle Registration
    vehicle_registration_number VARCHAR(50),
    vehicle_registration_url VARCHAR(500),
    vehicle_registration_verified BOOLEAN DEFAULT FALSE,
    vehicle_registration_verified_at TIMESTAMP,
    
    -- Vehicle Insurance
    insurance_provider VARCHAR(150),
    insurance_policy_number VARCHAR(100),
    insurance_expiry_date DATE,
    insurance_document_url VARCHAR(500),
    insurance_verified BOOLEAN DEFAULT FALSE,
    insurance_verified_at TIMESTAMP,
    
    -- Vehicle Photos
    vehicle_front_photo_url VARCHAR(500),
    vehicle_back_photo_url VARCHAR(500),
    vehicle_side_photo_url VARCHAR(500),
    vehicle_interior_photo_url VARCHAR(500),
    
    -- Background Check
    police_clearance_url VARCHAR(500),
    police_clearance_verified BOOLEAN DEFAULT FALSE,
    police_clearance_verified_at TIMESTAMP,
    
    -- Overall Verification Status
    verification_status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, VERIFIED, REJECTED
    verification_notes TEXT,
    verified_by UUID,  -- CHANGED: BIGINT → UUID
    verified_at TIMESTAMP,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_driver_documents_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_driver_documents_verified_by FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Indexes for driver_documents
CREATE INDEX idx_driver_documents_user_id ON driver_documents(user_id);
CREATE INDEX idx_driver_documents_license ON driver_documents(license_number);
CREATE INDEX idx_driver_documents_verification_status ON driver_documents(verification_status);
CREATE INDEX idx_driver_documents_license_expiry ON driver_documents(license_expiry_date);
CREATE INDEX idx_driver_documents_insurance_expiry ON driver_documents(insurance_expiry_date);

-- Update trigger for updated_at columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_employment_info_updated_at BEFORE UPDATE ON employment_info
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_driver_documents_updated_at BEFORE UPDATE ON driver_documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();