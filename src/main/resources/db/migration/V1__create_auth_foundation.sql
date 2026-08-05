CREATE TABLE IF NOT EXISTS languages (
    language_code VARCHAR(35) PRIMARY KEY,
    language_name VARCHAR(100) NOT NULL,
    native_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id VARCHAR(50) NOT NULL,
    phone_e164 VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    nickname VARCHAR(30) NOT NULL,
    profile_image_storage_key VARCHAR(500),
    nationality_code CHAR(2) NOT NULL,
    default_language_code VARCHAR(35) NOT NULL REFERENCES languages(language_code),
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'BANNED', 'WITHDRAWN')),
    phone_verified_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_withdrawn_at CHECK (account_status <> 'WITHDRAWN' OR withdrawn_at IS NOT NULL),
    CONSTRAINT uk_users_phone_e164 UNIQUE (phone_e164)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_login_id_lower ON users (lower(login_id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_nickname_lower ON users (lower(nickname));

CREATE TABLE IF NOT EXISTS phone_verification_challenges (
    challenge_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone_e164 VARCHAR(20) NOT NULL,
    purpose VARCHAR(30) NOT NULL
        CHECK (purpose IN ('SIGN_UP', 'FIND_LOGIN_ID', 'RESET_PASSWORD', 'WITHDRAW_ACCOUNT')),
    code_hash VARCHAR(255) NOT NULL,
    attempt_count SMALLINT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    send_count SMALLINT NOT NULL DEFAULT 1 CHECK (send_count >= 1),
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_phone_challenge_lookup
    ON phone_verification_challenges (phone_e164, purpose, created_at DESC);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    refresh_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    token_family_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_id VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_user_status
    ON refresh_tokens (user_id, revoked_at, expires_at);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_family ON refresh_tokens (token_family_id);

CREATE TABLE IF NOT EXISTS app_release_policies (
    release_policy_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    platform VARCHAR(10) NOT NULL UNIQUE CHECK (platform IN ('IOS', 'ANDROID')),
    minimum_supported_version VARCHAR(30) NOT NULL,
    latest_version VARCHAR(30) NOT NULL,
    maintenance_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    maintenance_message TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
