package com.allygo.allygo_api.auth.phoneverification.application.result;

import java.time.Instant;

public record PhoneVerificationSentResult(
        Long verificationId,
        Instant expiresAt,
        int expiresIn,
        int resendAvailableIn,
        String maskedPhoneNumber
) {
}
