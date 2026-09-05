package com.allygo.allygo_api.onboarding.application.command;

import java.util.List;

public record SaveInitialSettingsCommand(
        String nationalityCode,
        String defaultLanguageCode,
        Boolean locationSharingDefault,
        String timezoneName,
        List<NotificationPreference> notificationPreferences
) {
    public record NotificationPreference(
            String notificationCategory,
            Boolean pushEnabled,
            Boolean inAppEnabled
    ) {
    }
}
