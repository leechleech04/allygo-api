package com.allygo.allygo_api.onboarding.application.command;

public record SavePermissionStatusCommand(
        String locationStatus,
        String notificationStatus,
        String cameraStatus,
        String microphoneStatus,
        String checkedAt
) {
}
