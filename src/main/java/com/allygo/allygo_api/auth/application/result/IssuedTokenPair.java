package com.allygo.allygo_api.auth.application.result;

import java.time.Instant;
import java.util.UUID;

public record IssuedTokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        String refreshTokenHash,
        Instant refreshTokenExpiresAt,
        UUID tokenFamilyId
) {
}
