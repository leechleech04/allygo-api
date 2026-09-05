package com.allygo.allygo_api.onboarding.presentation.response;

import com.allygo.allygo_api.onboarding.application.result.InitialSettingsResult;

import java.time.Instant;
import java.util.List;

public record InitialSettingsResponse(
        String nationalityCode,
        String defaultLanguageCode,
        boolean onboardingCompleted,
        Instant onboardingCompletedAt,
        SettingsResponse settings,
        List<NotificationPreferenceResponse> notificationPreferences
) {
    public static InitialSettingsResponse from(InitialSettingsResult result) {
        return new InitialSettingsResponse(
                result.nationalityCode(), result.defaultLanguageCode(),
                result.onboardingCompleted(), result.onboardingCompletedAt(),
                new SettingsResponse(
                        result.settings().currentUiMode(),
                        result.settings().locationSharingDefault(),
                        result.settings().timezoneName()
                ),
                result.notificationPreferences().stream()
                        .map(preference -> new NotificationPreferenceResponse(
                                preference.notificationCategory(),
                                preference.pushEnabled(),
                                preference.inAppEnabled()
                        ))
                        .toList()
        );
    }

    public record SettingsResponse(
            String currentUiMode,
            boolean locationSharingDefault,
            String timezoneName
    ) {
    }

    public record NotificationPreferenceResponse(
            String notificationCategory,
            boolean pushEnabled,
            boolean inAppEnabled
    ) {
    }
}
