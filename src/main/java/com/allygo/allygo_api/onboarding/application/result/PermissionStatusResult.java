package com.allygo.allygo_api.onboarding.application.result;

import java.time.Instant;

public record PermissionStatusResult(PermissionSnapshot permissionSnapshot) {
    public record PermissionSnapshot(
            String locationStatus,
            String notificationStatus,
            String cameraStatus,
            String microphoneStatus,
            Instant checkedAt,
            Instant updatedAt
    ) {
    }
}
