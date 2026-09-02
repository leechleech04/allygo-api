package com.allygo.allygo_api.auth.account.application.result;

import java.time.Instant;
import java.util.List;

public record CurrentUserResult(
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
        Instant phoneVerifiedAt,
        String profileImageStorageKey,
        String profileImageUrl,
        Instant profileImageUrlExpiresAt,
        Instant lastLoginAt,
        Instant createdAt,
        List<String> activeRestrictionScopes
) {
}
