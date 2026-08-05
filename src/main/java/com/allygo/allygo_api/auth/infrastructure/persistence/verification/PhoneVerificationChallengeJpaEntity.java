package com.allygo.allygo_api.auth.infrastructure.persistence.verification;

import com.allygo.allygo_api.auth.domain.verification.VerificationPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "phone_verification_challenges")
public class PhoneVerificationChallengeJpaEntity {

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
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PhoneVerificationChallengeJpaEntity() {
    }

    public static PhoneVerificationChallengeJpaEntity create(
            String phoneE164,
            VerificationPurpose purpose,
            String codeHash,
            OffsetDateTime now,
            OffsetDateTime expiresAt
    ) {
        validatePhone(phoneE164);
        PhoneVerificationChallengeJpaEntity entity = new PhoneVerificationChallengeJpaEntity();
        entity.phoneE164 = phoneE164;
        entity.purpose = purpose;
        entity.codeHash = codeHash;
        entity.attemptCount = 0;
        entity.sendCount = 1;
        entity.createdAt = now;
        entity.expiresAt = expiresAt;
        return entity;
    }

    public void resend(String newCodeHash, OffsetDateTime newExpiresAt) {
        if (verifiedAt != null || consumedAt != null) {
            throw new IllegalStateException("completed challenge cannot be resent");
        }
        this.codeHash = newCodeHash;
        this.attemptCount = 0;
        this.sendCount++;
        this.expiresAt = newExpiresAt;
    }

    public int registerMismatch(OffsetDateTime now, int maxAttempts) {
        requireVerifiable(now, maxAttempts);
        attemptCount++;
        if (attemptCount >= maxAttempts) {
            expiresAt = now;
        }
        return Math.max(0, maxAttempts - attemptCount);
    }

    public void markVerified(OffsetDateTime now, int maxAttempts) {
        requireVerifiable(now, maxAttempts);
        verifiedAt = now;
    }

    public void consume(OffsetDateTime now) {
        if (verifiedAt == null || consumedAt != null) {
            throw new IllegalStateException("challenge must be verified and unconsumed");
        }
        consumedAt = now;
    }

    public void invalidate(OffsetDateTime now) {
        expiresAt = now;
    }

    public Long getChallengeId() {
        return challengeId;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public VerificationPurpose getPurpose() {
        return purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getSendCount() {
        return sendCount;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }

    private void requireVerifiable(OffsetDateTime now, int maxAttempts) {
        if (verifiedAt != null || consumedAt != null || !expiresAt.isAfter(now) || attemptCount >= maxAttempts) {
            throw new IllegalStateException("challenge is not verifiable");
        }
    }

    private static void validatePhone(String phoneE164) {
        if (phoneE164 == null || !phoneE164.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new IllegalArgumentException("phoneE164 must use E.164 format");
        }
    }
}
