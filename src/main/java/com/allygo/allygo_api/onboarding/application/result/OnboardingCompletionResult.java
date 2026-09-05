package com.allygo.allygo_api.onboarding.application.result;

import java.time.Instant;

public record OnboardingCompletionResult(
        long userId,
        long travelerProfileId,
        String roleType,
        String currentUiMode,
        boolean onboardingCompleted,
        Instant onboardingCompletedAt
) {
}
