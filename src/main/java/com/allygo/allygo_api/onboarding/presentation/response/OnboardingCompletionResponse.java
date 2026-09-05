package com.allygo.allygo_api.onboarding.presentation.response;

import com.allygo.allygo_api.onboarding.application.result.OnboardingCompletionResult;

import java.time.Instant;

public record OnboardingCompletionResponse(
        long userId,
        long travelerProfileId,
        String roleType,
        String currentUiMode,
        boolean onboardingCompleted,
        Instant onboardingCompletedAt
) {
    public static OnboardingCompletionResponse from(OnboardingCompletionResult result) {
        return new OnboardingCompletionResponse(
                result.userId(), result.travelerProfileId(), result.roleType(),
                result.currentUiMode(), result.onboardingCompleted(), result.onboardingCompletedAt()
        );
    }
}
