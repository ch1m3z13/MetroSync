-- V10__Add_Financial_System.sql
-- Add wallet and transaction tables for payment management

-- ==================== WALLETS TABLE ====================
CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    
    -- Balance (in NGN)
    balance NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    
    -- Ledger Balance (includes pending transactions)
    ledger_balance NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    
    -- Limits
    daily_transaction_limit NUMERIC(12, 2) DEFAULT 100000.00,  -- ₦100,000
    daily_withdrawal_limit NUMERIC(12, 2) DEFAULT 50000.00,    -- ₦50,000
    
    -- Status
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_blocked BOOLEAN NOT NULL DEFAULT false,
    blocked_reason TEXT,
    blocked_at TIMESTAMP,
    
    -- Optimistic locking for concurrent updates
    version BIGINT DEFAULT 0,
    
    -- Standard audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_wallet_balance CHECK (balance >= 0),
    CONSTRAINT chk_wallet_ledger_balance CHECK (ledger_balance >= 0),
    CONSTRAINT chk_wallet_limits CHECK (
        daily_transaction_limit > 0 AND 
        daily_withdrawal_limit > 0 AND
        daily_withdrawal_limit <= daily_transaction_limit
    )
);

-- Indexes
CREATE UNIQUE INDEX idx_wallets_user_id ON wallets(user_id);
CREATE INDEX idx_wallets_active ON wallets(is_active) WHERE is_active = true;
CREATE INDEX idx_wallets_blocked ON wallets(is_blocked) WHERE is_blocked = true;

-- Trigger
CREATE TRIGGER wallets_updated_at
    BEFORE UPDATE ON wallets
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- ==================== TRANSACTIONS TABLE ====================
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Transaction Details
    reference_number VARCHAR(100) NOT NULL UNIQUE,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    user_id UUID NOT NULL REFERENCES users(id),
    
    -- Transaction Type
    type VARCHAR(20) NOT NULL,
    category VARCHAR(30) NOT NULL,
    
    -- Amount
    amount NUMERIC(12, 2) NOT NULL,
    fee NUMERIC(12, 2) DEFAULT 0.00,
    total_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'NGN',
    
    -- Balance Snapshot
    balance_before NUMERIC(12, 2) NOT NULL,
    balance_after NUMERIC(12, 2) NOT NULL,
    
    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    
    -- Description
    description TEXT,
    
    -- Related Entities
    booking_id UUID REFERENCES bookings(id),
    related_user_id UUID REFERENCES users(id),  -- For transfers
    
    -- Payment Gateway Details (Paystack)
    payment_reference VARCHAR(100),  -- Paystack reference
    payment_channel VARCHAR(20),     -- card, bank_transfer, ussd
    payment_gateway_response JSONB,
    
    -- Metadata
    metadata JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    
    -- Timestamps
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    reversed_at TIMESTAMP,
    
    -- Standard audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_transaction_type CHECK (
        type IN ('CREDIT', 'DEBIT')
    ),
    CONSTRAINT chk_transaction_category CHECK (
        category IN (
            'TOPUP',              -- Wallet top-up
            'WITHDRAWAL',         -- Withdrawal to bank
            'BOOKING_PAYMENT',    -- Payment for booking
            'BOOKING_REFUND',     -- Refund for cancelled booking
            'DRIVER_PAYOUT',      -- Payment to driver
            'COMMISSION',         -- Platform commission
            'TRANSFER',           -- P2P transfer
            'REVERSAL',           -- Transaction reversal
            'PENALTY',            -- Penalty fee
            'BONUS'               -- Promotional bonus
        )
    ),
    CONSTRAINT chk_transaction_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED')
    ),
    CONSTRAINT chk_transaction_amount CHECK (amount > 0),
    CONSTRAINT chk_transaction_fee CHECK (fee >= 0),
    CONSTRAINT chk_transaction_total CHECK (total_amount = amount + fee)
);

-- Indexes
CREATE INDEX idx_transactions_wallet ON transactions(wallet_id);
CREATE INDEX idx_transactions_user ON transactions(user_id);
CREATE INDEX idx_transactions_reference ON transactions(reference_number);
CREATE INDEX idx_transactions_payment_ref ON transactions(payment_reference) 
    WHERE payment_reference IS NOT NULL;
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_type ON transactions(type);
CREATE INDEX idx_transactions_category ON transactions(category);
CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);
CREATE INDEX idx_transactions_booking ON transactions(booking_id) 
    WHERE booking_id IS NOT NULL;

-- Composite indexes for common queries
CREATE INDEX idx_transactions_user_date ON transactions(user_id, created_at DESC);
CREATE INDEX idx_transactions_wallet_status ON transactions(wallet_id, status);
CREATE INDEX idx_transactions_user_status ON transactions(user_id, status);

-- Trigger
CREATE TRIGGER transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- ==================== WALLET TRIGGERS ====================

/**
 * Auto-create wallet when user registers
 */
CREATE OR REPLACE FUNCTION create_wallet_on_registration()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO wallets (user_id)
    VALUES (NEW.id)
    ON CONFLICT (user_id) DO NOTHING;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_create_wallet
    AFTER INSERT ON users
    FOR EACH ROW
    EXECUTE FUNCTION create_wallet_on_registration();

/**
 * Update wallet balance after transaction completion
 */
CREATE OR REPLACE FUNCTION update_wallet_balance()
RETURNS TRIGGER AS $$
DECLARE
    current_balance NUMERIC(12, 2);
BEGIN
    -- Only update balance when transaction is completed
    IF NEW.status = 'COMPLETED' AND OLD.status != 'COMPLETED' THEN
        
        -- Lock wallet row for update
        SELECT balance INTO current_balance
        FROM wallets
        WHERE id = NEW.wallet_id
        FOR UPDATE;
        
        -- Update balance based on transaction type
        IF NEW.type = 'CREDIT' THEN
            UPDATE wallets
            SET 
                balance = balance + NEW.amount,
                ledger_balance = ledger_balance + NEW.amount,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = NEW.wallet_id;
        ELSIF NEW.type = 'DEBIT' THEN
            UPDATE wallets
            SET 
                balance = balance - NEW.total_amount,
                ledger_balance = ledger_balance - NEW.total_amount,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = NEW.wallet_id;
            
            -- Check if balance went negative (should never happen)
            IF (current_balance - NEW.total_amount) < 0 THEN
                RAISE EXCEPTION 'Insufficient wallet balance';
            END IF;
        END IF;
        
        -- Set completion timestamp
        NEW.completed_at = CURRENT_TIMESTAMP;
        
        -- Update balance_after in transaction
        SELECT balance INTO NEW.balance_after
        FROM wallets
        WHERE id = NEW.wallet_id;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transactions_update_balance
    BEFORE UPDATE ON transactions
    FOR EACH ROW
    WHEN (NEW.status = 'COMPLETED' AND OLD.status != 'COMPLETED')
    EXECUTE FUNCTION update_wallet_balance();

-- ==================== HELPER FUNCTIONS ====================

/**
 * Get wallet balance for a user
 */
CREATE OR REPLACE FUNCTION get_wallet_balance(p_user_id UUID)
RETURNS NUMERIC(12, 2) AS $$
DECLARE
    wallet_balance NUMERIC(12, 2);
BEGIN
    SELECT COALESCE(balance, 0.00) INTO wallet_balance
    FROM wallets
    WHERE user_id = p_user_id;
    
    RETURN COALESCE(wallet_balance, 0.00);
END;
$$ LANGUAGE plpgsql;

/**
 * Check if user has sufficient balance
 */
CREATE OR REPLACE FUNCTION has_sufficient_balance(
    p_user_id UUID,
    p_amount NUMERIC(12, 2)
)
RETURNS BOOLEAN AS $$
DECLARE
    current_balance NUMERIC(12, 2);
BEGIN
    SELECT balance INTO current_balance
    FROM wallets
    WHERE user_id = p_user_id
    AND is_active = true
    AND is_blocked = false;
    
    RETURN COALESCE(current_balance, 0) >= p_amount;
END;
$$ LANGUAGE plpgsql;

/**
 * Generate unique transaction reference
 */
CREATE OR REPLACE FUNCTION generate_transaction_reference()
RETURNS VARCHAR AS $$
DECLARE
    ref VARCHAR(100);
    exists BOOLEAN;
BEGIN
    LOOP
        -- Format: TXN-YYYYMMDD-RANDOM
        ref := 'TXN-' || 
               TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || 
               UPPER(SUBSTRING(MD5(RANDOM()::TEXT) FROM 1 FOR 10));
        
        -- Check if reference exists
        SELECT EXISTS(
            SELECT 1 FROM transactions WHERE reference_number = ref
        ) INTO exists;
        
        EXIT WHEN NOT exists;
    END LOOP;
    
    RETURN ref;
END;
$$ LANGUAGE plpgsql;

/**
 * Get transaction summary for a user
 */
CREATE OR REPLACE FUNCTION get_transaction_summary(
    p_user_id UUID,
    p_start_date TIMESTAMP DEFAULT NULL,
    p_end_date TIMESTAMP DEFAULT NULL
)
RETURNS TABLE (
    total_credits NUMERIC,
    total_debits NUMERIC,
    total_fees NUMERIC,
    transaction_count BIGINT,
    pending_amount NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COALESCE(SUM(amount) FILTER (WHERE type = 'CREDIT' AND status = 'COMPLETED'), 0) as total_credits,
        COALESCE(SUM(amount) FILTER (WHERE type = 'DEBIT' AND status = 'COMPLETED'), 0) as total_debits,
        COALESCE(SUM(fee) FILTER (WHERE status = 'COMPLETED'), 0) as total_fees,
        COUNT(*) FILTER (WHERE status = 'COMPLETED') as transaction_count,
        COALESCE(SUM(amount) FILTER (WHERE status = 'PENDING'), 0) as pending_amount
    FROM transactions
    WHERE user_id = p_user_id
    AND (p_start_date IS NULL OR created_at >= p_start_date)
    AND (p_end_date IS NULL OR created_at <= p_end_date);
END;
$$ LANGUAGE plpgsql;

/**
 * Check daily transaction limit
 */
CREATE OR REPLACE FUNCTION check_daily_limit(
    p_user_id UUID,
    p_amount NUMERIC(12, 2),
    p_is_withdrawal BOOLEAN DEFAULT false
)
RETURNS BOOLEAN AS $$
DECLARE
    daily_total NUMERIC(12, 2);
    transaction_limit NUMERIC(12, 2);
    withdrawal_limit NUMERIC(12, 2);
BEGIN
    -- Get wallet limits
    SELECT 
        daily_transaction_limit,
        daily_withdrawal_limit
    INTO transaction_limit, withdrawal_limit
    FROM wallets
    WHERE user_id = p_user_id;
    
    -- Calculate today's total transactions
    SELECT COALESCE(SUM(total_amount), 0) INTO daily_total
    FROM transactions
    WHERE user_id = p_user_id
    AND type = 'DEBIT'
    AND status = 'COMPLETED'
    AND created_at >= CURRENT_DATE;
    
    -- Check appropriate limit
    IF p_is_withdrawal THEN
        RETURN (daily_total + p_amount) <= withdrawal_limit;
    ELSE
        RETURN (daily_total + p_amount) <= transaction_limit;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ==================== VIEWS ====================

-- View for wallet overview
CREATE VIEW wallet_overview AS
SELECT 
    w.id as wallet_id,
    w.user_id,
    u.full_name,
    u.email,
    w.balance,
    w.ledger_balance,
    w.is_active,
    w.is_blocked,
    
    -- Today's transactions
    COALESCE(
        (SELECT SUM(amount) FROM transactions 
         WHERE wallet_id = w.id 
         AND type = 'CREDIT' 
         AND status = 'COMPLETED'
         AND created_at >= CURRENT_DATE),
        0
    ) as today_credits,
    
    COALESCE(
        (SELECT SUM(total_amount) FROM transactions 
         WHERE wallet_id = w.id 
         AND type = 'DEBIT' 
         AND status = 'COMPLETED'
         AND created_at >= CURRENT_DATE),
        0
    ) as today_debits,
    
    -- Pending transactions
    COALESCE(
        (SELECT SUM(amount) FROM transactions 
         WHERE wallet_id = w.id 
         AND status = 'PENDING'),
        0
    ) as pending_amount,
    
    w.created_at,
    w.updated_at
FROM wallets w
JOIN users u ON w.user_id = u.id;

-- View for recent transactions
CREATE VIEW recent_transactions AS
SELECT 
    t.id,
    t.reference_number,
    t.user_id,
    u.full_name as user_name,
    t.type,
    t.category,
    t.amount,
    t.fee,
    t.total_amount,
    t.status,
    t.description,
    t.balance_before,
    t.balance_after,
    t.created_at,
    t.completed_at
FROM transactions t
JOIN users u ON t.user_id = u.id
WHERE t.created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
ORDER BY t.created_at DESC;

-- ==================== BOOKING PAYMENT INTEGRATION ====================

-- Add payment fields to bookings table
ALTER TABLE bookings 
ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) DEFAULT 'PENDING',
ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(100),
ADD COLUMN IF NOT EXISTS transaction_id UUID REFERENCES transactions(id),
ADD COLUMN IF NOT EXISTS safety_pin VARCHAR(4);

-- Add constraints
ALTER TABLE bookings 
ADD CONSTRAINT chk_payment_status CHECK (
    payment_status IN ('PENDING', 'PAID', 'REFUNDED', 'FAILED')
);

-- Index
CREATE INDEX idx_bookings_payment_status ON bookings(payment_status);
CREATE INDEX idx_bookings_transaction ON bookings(transaction_id) 
    WHERE transaction_id IS NOT NULL;

-- Function to generate 4-digit safety PIN
CREATE OR REPLACE FUNCTION generate_safety_pin()
RETURNS VARCHAR AS $$
BEGIN
    RETURN LPAD(FLOOR(RANDOM() * 10000)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql;

-- Trigger to generate safety PIN when booking is confirmed
CREATE OR REPLACE FUNCTION assign_safety_pin()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'CONFIRMED' AND (OLD.status IS NULL OR OLD.status != 'CONFIRMED') THEN
        IF NEW.safety_pin IS NULL THEN
            NEW.safety_pin = generate_safety_pin();
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER bookings_assign_safety_pin
    BEFORE INSERT OR UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION assign_safety_pin();

-- ==================== COMMENTS ====================

COMMENT ON TABLE wallets IS 
'User wallet for holding balance in Nigerian Naira (NGN).
Supports top-up, withdrawal, and booking payments.
Uses optimistic locking to prevent concurrent update conflicts.';

COMMENT ON TABLE transactions IS 
'Audit trail for all wallet transactions.
Includes payment gateway details and supports Paystack integration.';

COMMENT ON COLUMN wallets.ledger_balance IS 
'Balance including pending transactions. 
Used for holds and pending payments.';

COMMENT ON COLUMN transactions.payment_reference IS 
'External payment reference from Paystack or other payment gateway.';

COMMENT ON COLUMN bookings.safety_pin IS 
'4-digit PIN shown to passenger for driver verification at pickup.';

COMMENT ON FUNCTION generate_transaction_reference IS 
'Generates unique transaction reference in format: TXN-YYYYMMDD-RANDOM';