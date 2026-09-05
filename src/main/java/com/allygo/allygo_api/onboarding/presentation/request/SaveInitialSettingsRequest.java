package com.allygo.allygo_api.onboarding.presentation.request;

import com.allygo.allygo_api.onboarding.application.command.SaveInitialSettingsCommand;

import java.util.List;

public record SaveInitialSettingsRequest(
        String nationalityCode,
        String defaultLanguageCode,
        Boolean locationSharingDefault,
        String timezoneName,
        List<NotificationPreferenceRequest> notificationPreferences
) {
    public SaveInitialSettingsCommand toCommand() {
        return new SaveInitialSettingsCommand(
                nationalityCode,
                defaultLanguageCode,
                locationSharingDefault,
                timezoneName,
                notificationPreferences == null ? null : notificationPreferences.stream()
                        .map(preference -> preference == null ? null : preference.toCommand())
                        .toList()
        );
    }

    public record NotificationPreferenceRequest(
            String notificationCategory,
            Object pushEnabled,
            Object inAppEnabled
    ) {
        SaveInitialSettingsCommand.NotificationPreference toCommand() {
            return new SaveInitialSettingsCommand.NotificationPreference(
                    notificationCategory, booleanValue(pushEnabled), booleanValue(inAppEnabled)
            );
        }

        private static Boolean booleanValue(Object value) {
            return value instanceof Boolean booleanValue ? booleanValue : null;
        }
    }
}
