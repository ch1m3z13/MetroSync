-- V8: Add Financial System
-- Tables: wallets, transactions

-- User Wallets (Balance Management)
CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,  -- CHANGED: BIGINT → UUID
    
    -- Balance (in Naira kobo - smallest unit, 1 Naira = 100 kobo)
    balance BIGINT DEFAULT 0 NOT NULL,  -- Stored in kobo (cents)
    
    -- Wallet Status
    status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, FROZEN, SUSPENDED
    
    -- Limits
    daily_withdrawal_limit BIGINT DEFAULT 50000000,  -- 500,000 NGN in kobo
    daily_withdrawal_used BIGINT DEFAULT 0,
    daily_withdrawal_reset_date DATE DEFAULT CURRENT_DATE,
    
    -- Currency
    currency VARCHAR(3) DEFAULT 'NGN',
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_transaction_at TIMESTAMP,
    
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_wallets_balance_positive CHECK (balance >= 0),
    CONSTRAINT chk_wallets_daily_limit_positive CHECK (daily_withdrawal_limit >= 0)
);

-- Indexes for wallets
CREATE INDEX idx_wallets_user_id ON wallets(user_id);
CREATE INDEX idx_wallets_status ON wallets(status);

-- Transactions (Audit Trail for All Financial Activities)
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,  -- CHANGED: BIGINT → UUID
    wallet_id BIGINT NOT NULL,
    
    -- Transaction Details
    type VARCHAR(30) NOT NULL,  -- TOP_UP, WITHDRAWAL, RIDE_PAYMENT, RIDE_EARNING, REFUND, COMMISSION
    amount BIGINT NOT NULL,  -- In kobo (can be negative for debits)
    
    -- Status
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, COMPLETED, FAILED, REVERSED
    
    -- Reference
    reference VARCHAR(100) UNIQUE NOT NULL,  -- Unique transaction reference (e.g., TXN-20240204-XXXXX)
    external_reference VARCHAR(200),  -- Paystack/Bank reference
    
    -- Payment Gateway Details (for TOP_UP/WITHDRAWAL)
    payment_provider VARCHAR(50),  -- PAYSTACK, BANK_TRANSFER, CASH
    payment_channel VARCHAR(50),  -- CARD, BANK_TRANSFER, USSD, QR
    
    -- Related Entities
    booking_id UUID,  -- CHANGED: BIGINT → UUID (For ride payments/earnings)
    
    -- Balance Snapshots
    balance_before BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    
    -- Metadata
    description TEXT,
    metadata JSONB,  -- Additional flexible data (e.g., card details, bank info)
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE SET NULL
);

-- Indexes for transactions
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_wallet_id ON transactions(wallet_id);
CREATE INDEX idx_transactions_booking_id ON transactions(booking_id);
CREATE INDEX idx_transactions_reference ON transactions(reference);
CREATE INDEX idx_transactions_external_reference ON transactions(external_reference);
CREATE INDEX idx_transactions_type ON transactions(type);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);
CREATE INDEX idx_transactions_user_date ON transactions(user_id, created_at DESC);
CREATE INDEX idx_transactions_provider ON transactions(payment_provider);

-- Composite index for user transaction history queries
CREATE INDEX idx_transactions_user_type_status ON transactions(user_id, type, status, created_at DESC);

-- Update trigger for wallets
CREATE TRIGGER update_wallets_updated_at BEFORE UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to update wallet balance (ensures atomicity)
CREATE OR REPLACE FUNCTION update_wallet_balance(
    p_wallet_id BIGINT,
    p_amount BIGINT,
    p_transaction_id BIGINT
) RETURNS BOOLEAN AS $$
DECLARE
    v_new_balance BIGINT;
    v_old_balance BIGINT;
BEGIN
    -- Lock the wallet row for update
    SELECT balance INTO v_old_balance FROM wallets WHERE id = p_wallet_id FOR UPDATE;
    
    -- Calculate new balance
    v_new_balance := v_old_balance + p_amount;
    
    -- Ensure balance doesn't go negative
    IF v_new_balance < 0 THEN
        RAISE EXCEPTION 'Insufficient balance';
    END IF;
    
    -- Update wallet balance
    UPDATE wallets 
    SET balance = v_new_balance,
        last_transaction_at = CURRENT_TIMESTAMP
    WHERE id = p_wallet_id;
    
    -- Update transaction record with balance snapshots
    UPDATE transactions
    SET balance_before = v_old_balance,
        balance_after = v_new_balance
    WHERE id = p_transaction_id;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- Function to reset daily withdrawal limits (to be called by cron job daily)
CREATE OR REPLACE FUNCTION reset_daily_withdrawal_limits() RETURNS VOID AS $$
BEGIN
    UPDATE wallets
    SET daily_withdrawal_used = 0,
        daily_withdrawal_reset_date = CURRENT_DATE
    WHERE daily_withdrawal_reset_date < CURRENT_DATE;
END;
$$ LANGUAGE plpgsql;

-- Add comments for documentation
COMMENT ON TABLE wallets IS 'User wallet balances and withdrawal limits';
COMMENT ON TABLE transactions IS 'Audit trail for all financial transactions';
COMMENT ON COLUMN wallets.balance IS 'Balance in kobo (1 NGN = 100 kobo)';
COMMENT ON COLUMN transactions.amount IS 'Transaction amount in kobo (positive for credit, can be negative for debit)';
COMMENT ON COLUMN transactions.reference IS 'Unique internal transaction reference';
COMMENT ON COLUMN transactions.external_reference IS 'Payment gateway or bank reference';