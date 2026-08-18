package com.allygo.allygo_api.auth.phoneverification.presentation.response;

import com.allygo.allygo_api.auth.phoneverification.application.result.PhoneVerificationConfirmedResult;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;

import java.time.Instant;

public record PhoneVerificationConfirmedResponse(
        Long verificationId,
        String phoneNumber,
        VerificationPurpose purpose,
        boolean verified,
        Instant verifiedAt,
        String verificationToken,
        int tokenExpiresIn
) {
    public static PhoneVerificationConfirmedResponse from(PhoneVerificationConfirmedResult result) {
        return new PhoneVerificationConfirmedResponse(
                result.verificationId(), result.phoneNumber(), result.purpose(), result.verified(),
                result.verifiedAt(), result.verificationToken(), result.tokenExpiresIn()
        );
    }
}
