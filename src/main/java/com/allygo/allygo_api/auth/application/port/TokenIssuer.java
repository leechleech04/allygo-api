package com.allygo.allygo_api.auth.application.port;

import com.allygo.allygo_api.auth.application.result.IssuedTokenPair;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface TokenIssuer {

    IssuedTokenPair issueNewSession(long userId, Set<String> roles, String deviceId);

    IssuedTokenPair rotate(
            long userId,
            Set<String> roles,
            String deviceId,
            UUID tokenFamilyId,
            Instant refreshTokenExpiresAt
    );
}
