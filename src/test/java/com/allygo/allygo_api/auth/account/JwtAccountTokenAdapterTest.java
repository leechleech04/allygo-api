package com.allygo.allygo_api.auth.account;

import com.allygo.allygo_api.auth.account.infrastructure.security.JwtAccountTokenAdapter;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import com.allygo.allygo_api.auth.phoneverification.infrastructure.security.JwtAccessTokenAdapter;
import com.allygo.allygo_api.auth.phoneverification.infrastructure.security.JwtTokenProperties;
import com.allygo.allygo_api.auth.phoneverification.infrastructure.security.JwtVerificationTokenAdapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccountTokenAdapterTest {
    private final JwtTokenProperties properties = new JwtTokenProperties(
            "allygo-test",
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            Duration.ofHours(1),
            Duration.ofDays(14),
            Duration.ofMinutes(10)
    );

    @Test
    void readsExistingPhoneVerificationTokenFormat() {
        Instant verifiedAt = Instant.now().minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String token = new JwtVerificationTokenAdapter(properties)
                .issue(10241L, VerificationPurpose.SIGN_UP, verifiedAt);

        var claims = new JwtAccountTokenAdapter(properties).parseVerificationToken(token);

        assertThat(claims.challengeId()).isEqualTo(10241L);
        assertThat(claims.purpose()).isEqualTo(VerificationPurpose.SIGN_UP);
        assertThat(claims.issuedAt()).isEqualTo(verifiedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void issuesAccessTokenCompatibleWithExistingAuthorizationAdapter() {
        JwtAccountTokenAdapter adapter = new JwtAccountTokenAdapter(properties);
        var tokens = adapter.issue(77L, Instant.now());

        assertThat(new JwtAccessTokenAdapter(properties).requireUserId("Bearer " + tokens.accessToken()))
                .isEqualTo(77L);
        assertThat(adapter.requireAccessUserId("Bearer " + tokens.accessToken())).isEqualTo(77L);
        assertThat(tokens.refreshToken()).startsWith("rt_");
        assertThat(tokens.refreshTokenHash()).doesNotContain(tokens.refreshToken());
        assertThat(adapter.hashRefreshToken(tokens.refreshToken())).isEqualTo(tokens.refreshTokenHash());
        assertThat(tokens.refreshTokenTtl()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void rotatesWithinExistingFamilyAndKeepsAbsoluteExpiration() {
        JwtAccountTokenAdapter adapter = new JwtAccountTokenAdapter(properties);
        Instant issuedAt = Instant.now();
        Instant familyExpiresAt = issuedAt.plusSeconds(10_000);
        UUID familyId = UUID.fromString("11111111-2222-3333-4444-555555555555");

        var tokens = adapter.rotate(77L, familyId, issuedAt, familyExpiresAt);

        assertThat(tokens.tokenFamilyId()).isEqualTo(familyId);
        assertThat(tokens.refreshTokenTtl()).isEqualTo(Duration.ofSeconds(10_000));
        assertThat(adapter.requireAccessUserId("Bearer " + tokens.accessToken())).isEqualTo(77L);
    }
}
