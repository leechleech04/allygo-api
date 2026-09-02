package com.allygo.allygo_api.auth.account.application.result;

import java.time.Instant;
import java.util.List;

public record SignUpResult(
        long userId,
        long travelerProfileId,
        long phoneVerificationId,
        String loginId,
        String phoneNumber,
        String name,
        String nickname,
        String nationalityCode,
        String defaultLanguageCode,
        String accountStatus,
        String roleType,
        boolean onboardingCompleted,
        SettingsResult settings,
        List<NotificationPreferenceResult> notificationPreferences,
        Instant createdAt,
        AuthTokenResult tokens
) {
    public record SettingsResult(String currentUiMode, boolean locationSharingDefault, String timezoneName) {
    }
}
