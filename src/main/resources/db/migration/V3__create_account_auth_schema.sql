CREATE TABLE IF NOT EXISTS traveler_profiles (
    traveler_profile_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(user_id),
    onboarding_completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id),
    current_ui_mode VARCHAR(10) NOT NULL DEFAULT 'TRAVELER'
        CHECK (current_ui_mode IN ('TRAVELER', 'HELPER')),
    location_sharing_default BOOLEAN NOT NULL DEFAULT FALSE,
    timezone_name VARCHAR(50) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    refresh_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    token_family_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_id VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_user_active
    ON refresh_tokens (user_id, revoked_at, expires_at);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_family ON refresh_tokens (token_family_id);

CREATE TABLE IF NOT EXISTS policy_documents (
    policy_document_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_type VARCHAR(30) NOT NULL CHECK (
        policy_type IN ('TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'SAFETY_NOTICE')
    ),
    version VARCHAR(30) NOT NULL,
    language_code VARCHAR(35) NOT NULL REFERENCES languages(language_code),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_required BOOLEAN NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_policy_documents_version UNIQUE (policy_type, version, language_code),
    CONSTRAINT ck_policy_document_period CHECK (retired_at IS NULL OR retired_at > effective_at)
);

CREATE TABLE IF NOT EXISTS user_policy_agreements (
    agreement_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    policy_document_id BIGINT NOT NULL REFERENCES policy_documents(policy_document_id),
    agreed BOOLEAN NOT NULL,
    agreed_at TIMESTAMPTZ NOT NULL,
    ip_address INET,
    user_agent VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS ix_user_policy_agreements_latest
    ON user_policy_agreements (user_id, policy_document_id, agreed_at DESC);

CREATE TABLE IF NOT EXISTS admin_accounts (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id),
    admin_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (admin_status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_admin_user_id BIGINT REFERENCES admin_accounts(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS helper_profiles (
    helper_profile_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(user_id),
    introduction TEXT NOT NULL,
    activity_country_code CHAR(2) NOT NULL,
    activity_city VARCHAR(100) NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (approval_status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    availability_status VARCHAR(20) NOT NULL DEFAULT 'UNAVAILABLE'
        CHECK (availability_status IN ('UNAVAILABLE', 'AVAILABLE', 'BUSY')),
    availability_changed_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    reviewed_by_admin_user_id BIGINT REFERENCES admin_accounts(user_id),
    rejection_reason TEXT,
    average_rating NUMERIC(3,2) NOT NULL DEFAULT 0 CHECK (average_rating BETWEEN 0 AND 5),
    rating_count INTEGER NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS verifications (
    verification_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    verification_type VARCHAR(30) NOT NULL CHECK (
        verification_type IN ('PHONE', 'ID_DOCUMENT', 'FACE', 'LOCAL_PRESENCE')
    ),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELED')),
    evidence_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    consented_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    review_started_at TIMESTAMPTZ,
    reviewed_by_admin_user_id BIGINT REFERENCES admin_accounts(user_id),
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    approved_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    supersedes_verification_id BIGINT REFERENCES verifications(verification_id),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_verifications_user_type
    ON verifications (user_id, verification_type, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_verifications_status ON verifications (status, submitted_at);
CREATE UNIQUE INDEX IF NOT EXISTS ux_verifications_in_progress
    ON verifications (user_id, verification_type)
    WHERE status IN ('PENDING', 'UNDER_REVIEW');
CREATE UNIQUE INDEX IF NOT EXISTS ux_verifications_active_approved
    ON verifications (user_id, verification_type)
    WHERE status = 'APPROVED';

CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    notification_category VARCHAR(30) NOT NULL CHECK (
        notification_category IN ('MATCHING', 'CHAT', 'CALL', 'REVIEW', 'REPORT', 'VERIFICATION', 'SANCTION')
    ),
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, notification_category)
);

CREATE TABLE IF NOT EXISTS sanctions (
    sanction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_user_id BIGINT NOT NULL REFERENCES users(user_id),
    report_group_id BIGINT,
    sanction_type VARCHAR(30) NOT NULL CHECK (
        sanction_type IN ('WARNING', 'TEMP_SUSPENSION', 'PERMANENT_BAN')
    ),
    status VARCHAR(15) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'ACTIVE', 'EXPIRED', 'REVOKED')),
    reason TEXT NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    issued_by_admin_user_id BIGINT NOT NULL REFERENCES admin_accounts(user_id),
    revoked_by_admin_user_id BIGINT REFERENCES admin_accounts(user_id),
    revoked_at TIMESTAMPTZ,
    revoke_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sanction_restrictions (
    sanction_restriction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sanction_id BIGINT NOT NULL REFERENCES sanctions(sanction_id) ON DELETE CASCADE,
    restriction_scope VARCHAR(30) NOT NULL CHECK (
        restriction_scope IN ('LOGIN', 'CREATE_REQUEST', 'HELPER_ACTIVITY', 'CHAT', 'CALL', 'REVIEW')
    ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_sanction_restriction UNIQUE (sanction_id, restriction_scope)
);
