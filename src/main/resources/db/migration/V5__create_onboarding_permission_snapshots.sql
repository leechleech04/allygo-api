CREATE TABLE IF NOT EXISTS user_permission_snapshots (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id),
    location_status VARCHAR(20) NOT NULL CHECK (
        location_status IN ('NOT_DETERMINED', 'GRANTED', 'DENIED', 'RESTRICTED')
    ),
    notification_status VARCHAR(20) NOT NULL CHECK (
        notification_status IN ('NOT_DETERMINED', 'GRANTED', 'DENIED', 'RESTRICTED')
    ),
    camera_status VARCHAR(20) NOT NULL CHECK (
        camera_status IN ('NOT_DETERMINED', 'GRANTED', 'DENIED', 'RESTRICTED')
    ),
    microphone_status VARCHAR(20) NOT NULL CHECK (
        microphone_status IN ('NOT_DETERMINED', 'GRANTED', 'DENIED', 'RESTRICTED')
    ),
    checked_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
