package com.allygo.allygo_api.auth.phoneverification.infrastructure.persistence;

import com.allygo.allygo_api.auth.phoneverification.application.port.PhoneVerificationChallengePort;
import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationChallenge;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class PhoneVerificationChallengePersistenceAdapter implements PhoneVerificationChallengePort {
    private final PhoneVerificationChallengeJpaRepository repository;
    private final EntityManager entityManager;

    public PhoneVerificationChallengePersistenceAdapter(
            PhoneVerificationChallengeJpaRepository repository,
            EntityManager entityManager
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public void lockPhoneAndPurpose(String phoneE164, VerificationPurpose purpose) {
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(CAST(?1 AS text), 0))"
                )
                .setParameter(1, phoneE164 + ':' + purpose.name())
                .getSingleResult();
    }

    @Override
    public Optional<PhoneVerificationChallenge> findLatestForUpdate(
            String phoneE164,
            VerificationPurpose purpose
    ) {
        return repository.findFirstByPhoneE164AndPurposeOrderByCreatedAtDesc(phoneE164, purpose)
                .map(PhoneVerificationChallengeJpaEntity::toDomain);
    }

    @Override
    public Optional<PhoneVerificationChallenge> findByIdForUpdate(Long challengeId) {
        return repository.findByIdForUpdate(challengeId).map(PhoneVerificationChallengeJpaEntity::toDomain);
    }

    @Override
    public int sumSendCount(
            String phoneE164,
            VerificationPurpose purpose,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        return Math.toIntExact(repository.sumSendCount(phoneE164, purpose, fromInclusive, toExclusive));
    }

    @Override
    public PhoneVerificationChallenge create(
            String phoneE164,
            VerificationPurpose purpose,
            String codeHash,
            Instant expiresAt,
            Instant createdAt
    ) {
        return repository.save(new PhoneVerificationChallengeJpaEntity(
                phoneE164, purpose, codeHash, expiresAt, createdAt
        )).toDomain();
    }

    @Override
    public PhoneVerificationChallenge resend(Long challengeId, String codeHash, Instant expiresAt) {
        PhoneVerificationChallengeJpaEntity entity = repository.findById(challengeId).orElseThrow();
        entity.resend(codeHash, expiresAt);
        return entity.toDomain();
    }

    @Override
    public void expire(Long challengeId, Instant expiresAt) {
        repository.findById(challengeId).ifPresent(entity -> entity.expire(expiresAt));
    }

    @Override
    public int registerMismatch(Long challengeId, Instant now, int maxAttempts) {
        PhoneVerificationChallengeJpaEntity entity = repository.findById(challengeId).orElseThrow();
        return entity.registerMismatch(now, maxAttempts);
    }

    @Override
    public PhoneVerificationChallenge markVerified(Long challengeId, Instant verifiedAt) {
        PhoneVerificationChallengeJpaEntity entity = repository.findById(challengeId).orElseThrow();
        entity.markVerified(verifiedAt);
        return entity.toDomain();
    }
}
