package com.allygo.allygo_api.auth.account.application.result;

public record NotificationPreferenceResult(
        String notificationCategory,
        boolean pushEnabled,
        boolean inAppEnabled
) {
}
