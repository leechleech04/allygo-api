package com.allygo.allygo_api.auth.account.presentation.response;

import com.allygo.allygo_api.auth.account.application.result.NotificationPreferenceResult;
import com.allygo.allygo_api.auth.account.application.result.SignUpResult;

import java.time.Instant;
import java.util.List;

public record SignUpResponse(
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
        SettingsResponse settings,
        List<NotificationPreferenceResult> notificationPreferences,
        Instant createdAt,
        String accessToken,
        String refreshToken,
        int accessTokenExpiresIn,
        int refreshTokenExpiresIn
) {
    public static SignUpResponse from(SignUpResult result) {
        return new SignUpResponse(
                result.userId(), result.travelerProfileId(), result.phoneVerificationId(),
                result.loginId(), result.phoneNumber(), result.name(), result.nickname(),
                result.nationalityCode(), result.defaultLanguageCode(), result.accountStatus(),
                result.roleType(), result.onboardingCompleted(),
                new SettingsResponse(
                        result.settings().currentUiMode(),
                        result.settings().locationSharingDefault(),
                        result.settings().timezoneName()
                ),
                result.notificationPreferences(), result.createdAt(),
                result.tokens().accessToken(), result.tokens().refreshToken(),
                result.tokens().accessTokenExpiresIn(), result.tokens().refreshTokenExpiresIn()
        );
    }

    public record SettingsResponse(String currentUiMode, boolean locationSharingDefault, String timezoneName) {
    }
}
