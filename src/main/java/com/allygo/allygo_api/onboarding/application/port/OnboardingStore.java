package com.allygo.allygo_api.onboarding.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OnboardingStore {
    Optional<OnboardingState> find(long userId, Instant now);

    Optional<OnboardingState> lock(long userId, Instant now);

    Optional<Language> findLanguage(String languageCode);

    Optional<PermissionState> findPermissionState(long userId, Instant now);

    Optional<PermissionState> lockPermissionState(long userId, Instant now);

    void saveInitialSettings(
            long userId,
            String nationalityCode,
            String defaultLanguageCode,
            boolean locationSharingDefault,
            String timezoneName,
            List<NotificationPreference> notificationPreferences,
            Instant now
    );

    void complete(long userId, Instant completedAt);

    void savePermissionSnapshot(long userId, PermissionSnapshot snapshot, Instant updatedAt);

    record Language(String languageCode, boolean active) {
    }

    record NotificationPreference(
            String notificationCategory,
            boolean pushEnabled,
            boolean inAppEnabled
    ) {
    }

    record PermissionSnapshot(
            String locationStatus,
            String notificationStatus,
            String cameraStatus,
            String microphoneStatus,
            Instant checkedAt,
            Instant updatedAt
    ) {
    }

    record PermissionState(
            long userId,
            String accountStatus,
            boolean loginRestricted,
            Instant restrictionEndsAt,
            PermissionSnapshot permissionSnapshot
    ) {
    }

    record OnboardingState(
            long userId,
            String nationalityCode,
            String defaultLanguageCode,
            String accountStatus,
            Long travelerProfileId,
            Instant onboardingCompletedAt,
            String currentUiMode,
            Boolean locationSharingDefault,
            String timezoneName,
            List<NotificationPreference> notificationPreferences,
            boolean loginRestricted,
            Instant restrictionEndsAt
    ) {
    }
}
