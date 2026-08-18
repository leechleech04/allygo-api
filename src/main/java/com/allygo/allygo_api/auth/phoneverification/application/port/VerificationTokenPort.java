package com.allygo.allygo_api.auth.phoneverification.application.port;

import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;

import java.time.Duration;
import java.time.Instant;

public interface VerificationTokenPort {
    String issue(Long challengeId, VerificationPurpose purpose, Instant verifiedAt);
    Duration ttl();
}
