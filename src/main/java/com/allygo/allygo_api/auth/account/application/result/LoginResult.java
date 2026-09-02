package com.allygo.allygo_api.auth.account.application.result;

import java.time.Instant;

public record LoginResult(
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
        AuthTokenResult tokens
) {
}
