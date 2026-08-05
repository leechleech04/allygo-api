package com.allygo.allygo_api.auth;

import com.allygo.allygo_api.auth.application.result.IssuedTokenPair;
import com.allygo.allygo_api.auth.infrastructure.config.TokenProperties;
import com.allygo.allygo_api.auth.infrastructure.security.JwtConfiguration;
import com.allygo.allygo_api.auth.infrastructure.security.JwtTokenIssuer;
import com.allygo.allygo_api.auth.infrastructure.security.Sha256TokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenIssuerTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void issuesSignedAccessAndRefreshTokensAndKeepsAbsoluteFamilyExpiryOnRotation() {
        TokenProperties properties = new TokenProperties(
                "allygo-api-test", SECRET, Duration.ofHours(1), Duration.ofDays(14), Duration.ofMinutes(10)
        );
        JwtTokenIssuer issuer = new JwtTokenIssuer(
                new JwtConfiguration().jwtEncoder(properties),
                new Sha256TokenHasher(),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        IssuedTokenPair issued = issuer.issueNewSession(42L, Set.of("TRAVELER"), "device-1");
        Jwt access = decoder().decode(issued.accessToken());
        Jwt refresh = decoder().decode(issued.refreshToken());

        assertThat(access.getSubject()).isEqualTo("42");
        assertThat(access.getClaimAsString("token_type")).isEqualTo("access");
        assertThat(access.getClaimAsStringList("roles")).containsExactly("TRAVELER");
        assertThat(access.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(refresh.getClaimAsString("token_type")).isEqualTo("refresh");
        assertThat(refresh.getClaimAsString("family_id")).isEqualTo(issued.tokenFamilyId().toString());
        assertThat(issued.refreshTokenHash()).isEqualTo(new Sha256TokenHasher().hash(issued.refreshToken()));

        IssuedTokenPair rotated = issuer.rotate(
                42L, Set.of("TRAVELER"), "device-1", issued.tokenFamilyId(), issued.refreshTokenExpiresAt()
        );
        assertThat(rotated.tokenFamilyId()).isEqualTo(issued.tokenFamilyId());
        assertThat(rotated.refreshTokenExpiresAt()).isEqualTo(issued.refreshTokenExpiresAt());
        assertThat(rotated.refreshToken()).isNotEqualTo(issued.refreshToken());
    }

    private JwtDecoder decoder() {
        byte[] key = Base64.getDecoder().decode(SECRET);
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256")).build();
    }
}
