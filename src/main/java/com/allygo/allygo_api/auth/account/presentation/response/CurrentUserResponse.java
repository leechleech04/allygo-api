package com.allygo.allygo_api.auth.account.presentation.response;

import com.allygo.allygo_api.auth.account.application.result.CurrentUserResult;

import java.time.Instant;
import java.util.List;

public record CurrentUserResponse(
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
    public static CurrentUserResponse from(CurrentUserResult result) {
        return new CurrentUserResponse(
                result.userId(), result.travelerProfileId(), result.helperProfileId(), result.loginId(),
                result.name(), result.nickname(), result.phoneNumber(), result.nationalityCode(),
                result.defaultLanguageCode(), result.accountStatus(), result.roleType(),
                result.helperApprovalStatus(), result.helperAvailabilityStatus(), result.currentUiMode(),
                result.onboardingCompleted(), result.phoneVerifiedAt(), result.profileImageStorageKey(),
                result.profileImageUrl(), result.profileImageUrlExpiresAt(), result.lastLoginAt(),
                result.createdAt(), result.activeRestrictionScopes()
        );
    }
}
