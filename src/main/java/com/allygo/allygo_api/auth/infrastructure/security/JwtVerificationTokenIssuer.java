package com.allygo.allygo_api.auth.infrastructure.security;

import com.allygo.allygo_api.auth.application.port.VerificationTokenIssuer;
import com.allygo.allygo_api.auth.domain.verification.VerificationPurpose;
import com.allygo.allygo_api.auth.infrastructure.config.TokenProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;

@Component
public class JwtVerificationTokenIssuer implements VerificationTokenIssuer {

    private final JwtEncoder encoder;
    private final TokenProperties properties;

    public JwtVerificationTokenIssuer(JwtEncoder encoder, TokenProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public String issue(long challengeId, VerificationPurpose purpose, OffsetDateTime verifiedAt) {
        Instant issuedAt = verifiedAt.toInstant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(Long.toString(challengeId))
                .id("phone-verification-" + challengeId)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.verificationTokenTtl()))
                .claim("token_type", "phone_verification")
                .claim("purpose", purpose.name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
