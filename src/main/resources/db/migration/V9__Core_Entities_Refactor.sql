-- V9__Core_Entities_Refactor.sql
-- Migration to adapt core entities to new structure, preserving data

-- Enable PostGIS extension (safe if already enabled)
CREATE EXTENSION IF NOT EXISTS postgis;

-- ==================== USERS TABLE ====================
-- Add new columns if not present
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS role VARCHAR(20),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add/modify constraints
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_role') THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('RIDER', 'DRIVER'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_status') THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'BANNED'));
    END IF;
END $$;

-- Add indexes
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone_number);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- ==================== USER PROFILES TABLE ====================
CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    date_of_birth DATE NOT NULL,
    nin VARCHAR(11) UNIQUE NOT NULL,
    selfie_verified BOOLEAN DEFAULT FALSE,
    selfie_url VARCHAR(500),
    rating DECIMAL(3,2) DEFAULT 0.0,
    total_trips INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_profiles_nin ON user_profiles(nin);
CREATE INDEX IF NOT EXISTS idx_profiles_user ON user_profiles(user_id);

-- ==================== EMPLOYMENT INFO TABLE ====================
CREATE TABLE IF NOT EXISTS employment_info (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    company_name VARCHAR(255) NOT NULL,
    job_title VARCHAR(255) NOT NULL,
    work_address TEXT NOT NULL,
    work_location GEOGRAPHY(POINT, 4326),
    work_start_time TIME NOT NULL,
    work_end_time TIME NOT NULL,
    verification_type VARCHAR(20) NOT NULL CHECK (verification_type IN ('ID_CARD', 'EMAIL', 'LETTER')),
    verification_status VARCHAR(20) DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    document_url VARCHAR(500),
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_employment_user ON employment_info(user_id);
CREATE INDEX IF NOT EXISTS idx_employment_location ON employment_info USING GIST(work_location);

-- ==================== VEHICLES TABLE ====================
ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS make VARCHAR(100),
    ADD COLUMN IF NOT EXISTS model VARCHAR(100),
    ADD COLUMN IF NOT EXISTS year INTEGER,
    ADD COLUMN IF NOT EXISTS color VARCHAR(50),
    ADD COLUMN IF NOT EXISTS plate_number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS available_seats INTEGER,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add/modify constraints
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_vehicles_available_seats') THEN
        ALTER TABLE vehicles ADD CONSTRAINT chk_vehicles_available_seats CHECK (available_seats > 0 AND available_seats <= 8);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_vehicles_status') THEN
        ALTER TABLE vehicles ADD CONSTRAINT chk_vehicles_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_vehicles_plate ON vehicles(plate_number);
CREATE INDEX IF NOT EXISTS idx_vehicles_user ON vehicles(user_id);

-- ==================== ROUTES TABLE ====================
ALTER TABLE routes
    ADD COLUMN IF NOT EXISTS from_location VARCHAR(255),
    ADD COLUMN IF NOT EXISTS from_point GEOGRAPHY(POINT, 4326),
    ADD COLUMN IF NOT EXISTS to_location VARCHAR(255),
    ADD COLUMN IF NOT EXISTS to_point GEOGRAPHY(POINT, 4326),
    ADD COLUMN IF NOT EXISTS route_path GEOGRAPHY(LINESTRING, 4326),
    ADD COLUMN IF NOT EXISTS departure_time TIME,
    ADD COLUMN IF NOT EXISTS total_seats INTEGER,
    ADD COLUMN IF NOT EXISTS booked_seats INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS price_per_seat INTEGER,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS recurring BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS days_of_week INTEGER[],
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_routes_driver ON routes(driver_id);
CREATE INDEX IF NOT EXISTS idx_routes_active ON routes(is_active);
CREATE INDEX IF NOT EXISTS idx_routes_from_point ON routes USING GIST(from_point);
CREATE INDEX IF NOT EXISTS idx_routes_to_point ON routes USING GIST(to_point);
CREATE INDEX IF NOT EXISTS idx_routes_path ON routes USING GIST(route_path);

-- ==================== BOOKINGS TABLE ====================
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS ride_id UUID,
    ADD COLUMN IF NOT EXISTS pickup_location VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pickup_point GEOGRAPHY(POINT, 4326),
    ADD COLUMN IF NOT EXISTS dropoff_location VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dropoff_point GEOGRAPHY(POINT, 4326),
    ADD COLUMN IF NOT EXISTS seats_requested INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS price INTEGER,
    ADD COLUMN IF NOT EXISTS safety_pin VARCHAR(4),
    ADD COLUMN IF NOT EXISTS booking_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

-- Add/modify constraints
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_bookings_status') THEN
        ALTER TABLE bookings ADD CONSTRAINT chk_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'ACTIVE', 'COMPLETED', 'CANCELLED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_bookings_rider ON bookings(rider_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_bookings_pin ON bookings(safety_pin);

-- ==================== WALLETS TABLE ====================
CREATE TABLE IF NOT EXISTS wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) UNIQUE,
    balance INTEGER DEFAULT 0 CHECK (balance >= 0),
    total_earned INTEGER DEFAULT 0,
    total_spent INTEGER DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wallets_user ON wallets(user_id);

-- ==================== TRANSACTIONS TABLE ====================
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    booking_id UUID REFERENCES bookings(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    amount INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    description TEXT,
    reference VARCHAR(100) UNIQUE,
    payment_method VARCHAR(50),
    payment_gateway VARCHAR(50),
    gateway_reference VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_reference ON transactions(reference);
CREATE INDEX IF NOT EXISTS idx_transactions_created ON transactions(created_at DESC);

-- ==================== OTP TOKENS TABLE ====================
CREATE TABLE IF NOT EXISTS otp_tokens (
    id BIGSERIAL PRIMARY KEY,
    phone_number VARCHAR(15) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    attempts INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_otp_phone ON otp_tokens(phone_number);
CREATE INDEX IF NOT EXISTS idx_otp_expires ON otp_tokens(expires_at);


CREATE TABLE rides (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT REFERENCES routes(id) ON DELETE CASCADE,
    driver_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    scheduled_start_time TIMESTAMP NOT NULL,
    available_seats INTEGER NOT NULL,
    status VARCHAR(20) DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_rides_route ON rides(route_id);
CREATE INDEX idx_rides_driver ON rides(driver_id);
CREATE INDEX idx_rides_status ON rides(status);
CREATE INDEX idx_rides_start_time ON rides(scheduled_start_time);