package com.allygo.allygo_api.auth.phoneverification.infrastructure.persistence;

import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationChallenge;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "phone_verification_challenges")
class PhoneVerificationChallengeJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "challenge_id")
    private Long challengeId;

    @Column(name = "phone_e164", nullable = false, length = 20)
    private String phoneE164;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private VerificationPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount;

    @Column(name = "send_count", nullable = false)
    private short sendCount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PhoneVerificationChallengeJpaEntity() {
    }

    PhoneVerificationChallengeJpaEntity(
            String phoneE164,
            VerificationPurpose purpose,
            String codeHash,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.phoneE164 = phoneE164;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.attemptCount = 0;
        this.sendCount = 1;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    void resend(String newCodeHash, Instant newExpiresAt) {
        this.codeHash = newCodeHash;
        this.attemptCount = 0;
        this.sendCount++;
        this.expiresAt = newExpiresAt;
    }

    void expire(Instant instant) {
        this.expiresAt = instant;
    }

    int registerMismatch(Instant now, int maxAttempts) {
        this.attemptCount++;
        if (attemptCount >= maxAttempts) {
            this.expiresAt = now;
        }
        return Math.max(0, maxAttempts - attemptCount);
    }

    void markVerified(Instant instant) {
        this.verifiedAt = instant;
    }

    PhoneVerificationChallenge toDomain() {
        return new PhoneVerificationChallenge(
                challengeId, phoneE164, purpose, codeHash, attemptCount, sendCount,
                expiresAt, verifiedAt, consumedAt, createdAt
        );
    }
}
