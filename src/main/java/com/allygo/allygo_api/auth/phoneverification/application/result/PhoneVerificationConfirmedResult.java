package com.allygo.allygo_api.auth.phoneverification.application.result;

import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;

import java.time.Instant;

public record PhoneVerificationConfirmedResult(
        Long verificationId,
        String phoneNumber,
        VerificationPurpose purpose,
        boolean verified,
        Instant verifiedAt,
        String verificationToken,
        int tokenExpiresIn
) {
}
