package com.allygo.allygo_api.auth.account.presentation.response;

import com.allygo.allygo_api.auth.account.application.result.LoginResult;

import java.time.Instant;

public record LoginResponse(
        long userId,
        long travelerProfileId,
        Long helperProfileId,
        String loginId,
        String name,
        String nickname,
        String phoneNumber,
        String nationalityCode,
        String defaultLanguageCode,
        String accountStatus,
        String roleType,
        String helperApprovalStatus,
        String helperAvailabilityStatus,
        String currentUiMode,
        boolean onboardingCompleted,
        String profileImageUrl,
        Instant profileImageUrlExpiresAt,
        Instant lastLoginAt,
        String accessToken,
        String refreshToken,
        int accessTokenExpiresIn,
        int refreshTokenExpiresIn
) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.userId(), result.travelerProfileId(), result.helperProfileId(), result.loginId(),
                result.name(), result.nickname(), result.phoneNumber(), result.nationalityCode(),
                result.defaultLanguageCode(), result.accountStatus(), result.roleType(),
                result.helperApprovalStatus(), result.helperAvailabilityStatus(), result.currentUiMode(),
                result.onboardingCompleted(), result.profileImageUrl(), result.profileImageUrlExpiresAt(),
                result.lastLoginAt(), result.tokens().accessToken(), result.tokens().refreshToken(),
                result.tokens().accessTokenExpiresIn(), result.tokens().refreshTokenExpiresIn()
        );
    }
}
