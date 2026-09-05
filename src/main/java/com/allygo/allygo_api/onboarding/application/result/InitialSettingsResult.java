package com.allygo.allygo_api.onboarding.application.result;

import java.time.Instant;
import java.util.List;

public record InitialSettingsResult(
        String nationalityCode,
        String defaultLanguageCode,
        boolean onboardingCompleted,
        Instant onboardingCompletedAt,
        Settings settings,
        List<NotificationPreference> notificationPreferences
) {
    public record Settings(
            String currentUiMode,
            boolean locationSharingDefault,
            String timezoneName
    ) {
    }

    public record NotificationPreference(
            String notificationCategory,
            boolean pushEnabled,
            boolean inAppEnabled
    ) {
    }
}
