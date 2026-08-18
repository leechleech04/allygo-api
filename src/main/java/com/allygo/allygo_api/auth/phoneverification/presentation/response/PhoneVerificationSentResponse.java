package com.allygo.allygo_api.auth.phoneverification.presentation.response;

import com.allygo.allygo_api.auth.phoneverification.application.result.PhoneVerificationSentResult;

import java.time.Instant;

public record PhoneVerificationSentResponse(
        Long verificationId,
        Instant expiresAt,
        int expiresIn,
        int resendAvailableIn,
        String maskedPhoneNumber
) {
    public static PhoneVerificationSentResponse from(PhoneVerificationSentResult result) {
        return new PhoneVerificationSentResponse(
                result.verificationId(),
                result.expiresAt(),
                result.expiresIn(),
                result.resendAvailableIn(),
                result.maskedPhoneNumber()
        );
    }
}
