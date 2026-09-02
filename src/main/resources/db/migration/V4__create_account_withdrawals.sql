CREATE TABLE IF NOT EXISTS account_withdrawals (
    withdrawal_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(user_id),
    reason_code VARCHAR(50),
    reason_detail TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    retention_until TIMESTAMPTZ,
    CONSTRAINT ck_account_withdrawal_completion CHECK (completed_at >= requested_at)
);
