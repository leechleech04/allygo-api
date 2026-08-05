package com.allygo.allygo_api.auth.infrastructure.security;

import com.allygo.allygo_api.auth.application.port.TokenHasher;
import com.allygo.allygo_api.auth.application.port.TokenIssuer;
import com.allygo.allygo_api.auth.application.result.IssuedTokenPair;
import com.allygo.allygo_api.auth.infrastructure.config.TokenProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtTokenIssuer implements TokenIssuer {

    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final JwtEncoder encoder;
    private final TokenHasher tokenHasher;
    private final TokenProperties properties;
    private final Clock clock;

    public JwtTokenIssuer(
            JwtEncoder encoder,
            TokenHasher tokenHasher,
            TokenProperties properties,
            Clock clock
    ) {
        this.encoder = encoder;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedTokenPair issueNewSession(long userId, Set<String> roles, String deviceId) {
        Instant issuedAt = clock.instant();
        return issue(
                userId,
                roles,
                deviceId,
                UUID.randomUUID(),
                issuedAt,
                issuedAt.plus(properties.refreshTokenTtl())
        );
    }

    @Override
    public IssuedTokenPair rotate(
            long userId,
            Set<String> roles,
            String deviceId,
            UUID tokenFamilyId,
            Instant refreshTokenExpiresAt
    ) {
        Instant issuedAt = clock.instant();
        if (!refreshTokenExpiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("refresh token family is expired");
        }
        return issue(userId, roles, deviceId, tokenFamilyId, issuedAt, refreshTokenExpiresAt);
    }

    private IssuedTokenPair issue(
            long userId,
            Set<String> roles,
            String deviceId,
            UUID familyId,
            Instant issuedAt,
            Instant refreshExpiresAt
    ) {
        Instant accessExpiresAt = issuedAt.plus(properties.accessTokenTtl());
        String accessToken = encode(userId, roles, null, deviceId, ACCESS, issuedAt, accessExpiresAt);
        String refreshToken = encode(userId, Set.of(), familyId, deviceId, REFRESH, issuedAt, refreshExpiresAt);
        return new IssuedTokenPair(
                accessToken,
                accessExpiresAt,
                refreshToken,
                tokenHasher.hash(refreshToken),
                refreshExpiresAt,
                familyId
        );
    }

    private String encode(
            long userId,
            Set<String> roles,
            UUID familyId,
            String deviceId,
            String tokenType,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(Long.toString(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("token_type", tokenType);
        if (!roles.isEmpty()) {
            claims.claim("roles", roles.stream().sorted().toList());
        }
        if (familyId != null) {
            claims.claim("family_id", familyId.toString());
        }
        if (deviceId != null && !deviceId.isBlank()) {
            claims.claim("device_id", deviceId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
