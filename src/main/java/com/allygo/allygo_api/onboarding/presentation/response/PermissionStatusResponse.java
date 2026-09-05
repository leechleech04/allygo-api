package com.allygo.allygo_api.onboarding.presentation.response;

import com.allygo.allygo_api.onboarding.application.result.PermissionStatusResult;

import java.time.Instant;

public record PermissionStatusResponse(PermissionSnapshot permissionSnapshot) {
    public static PermissionStatusResponse from(PermissionStatusResult result) {
        if (result.permissionSnapshot() == null) {
            return new PermissionStatusResponse(null);
        }
        PermissionStatusResult.PermissionSnapshot snapshot = result.permissionSnapshot();
        return new PermissionStatusResponse(new PermissionSnapshot(
                snapshot.locationStatus(), snapshot.notificationStatus(),
                snapshot.cameraStatus(), snapshot.microphoneStatus(),
                snapshot.checkedAt(), snapshot.updatedAt()
        ));
    }

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
