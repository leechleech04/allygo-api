package com.allygo.allygo_api.auth.phoneverification.infrastructure.persistence;

import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface PhoneVerificationChallengeJpaRepository
        extends JpaRepository<PhoneVerificationChallengeJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from PhoneVerificationChallengeJpaEntity challenge where challenge.challengeId = :challengeId")
    Optional<PhoneVerificationChallengeJpaEntity> findByIdForUpdate(@Param("challengeId") Long challengeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PhoneVerificationChallengeJpaEntity> findFirstByPhoneE164AndPurposeOrderByCreatedAtDesc(
            String phoneE164,
            VerificationPurpose purpose
    );

    @Query("""
            select coalesce(sum(challenge.sendCount), 0)
            from PhoneVerificationChallengeJpaEntity challenge
            where challenge.phoneE164 = :phoneE164
              and challenge.purpose = :purpose
              and challenge.createdAt >= :fromInclusive
              and challenge.createdAt < :toExclusive
            """)
    Long sumSendCount(
            @Param("phoneE164") String phoneE164,
            @Param("purpose") VerificationPurpose purpose,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );
}
