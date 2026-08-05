package com.allygo.allygo_api.auth.application.port;

import com.allygo.allygo_api.auth.domain.verification.VerificationPurpose;

import java.time.OffsetDateTime;

public interface VerificationTokenIssuer {

    String issue(long challengeId, VerificationPurpose purpose, OffsetDateTime verifiedAt);
}
