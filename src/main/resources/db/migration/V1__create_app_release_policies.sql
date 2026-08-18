CREATE TABLE IF NOT EXISTS app_release_policies (
    release_policy_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    platform VARCHAR(10) NOT NULL UNIQUE CHECK (platform IN ('IOS', 'ANDROID')),
    minimum_supported_version VARCHAR(30) NOT NULL,
    latest_version VARCHAR(30) NOT NULL,
    maintenance_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    maintenance_message TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
