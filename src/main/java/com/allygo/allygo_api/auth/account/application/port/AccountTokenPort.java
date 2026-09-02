package com.allygo.allygo_api.auth.account.application.port;

import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface AccountTokenPort {
    VerificationTokenClaims parseVerificationToken(String token);

    long requireAccessUserId(String authorizationHeader);

    String hashRefreshToken(String refreshToken);

    IssuedTokens issue(long userId, Instant issuedAt);

    IssuedTokens rotate(long userId, UUID tokenFamilyId, Instant issuedAt, Instant refreshTokenExpiresAt);

    record VerificationTokenClaims(
            long challengeId,
            VerificationPurpose purpose,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }

    record IssuedTokens(
            String accessToken,
            String refreshToken,
            String refreshTokenHash,
            UUID tokenFamilyId,
            Duration accessTokenTtl,
            Duration refreshTokenTtl
    ) {
    }
}
