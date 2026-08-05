package com.allygo.allygo_api.auth.infrastructure.persistence.verification;

import com.allygo.allygo_api.auth.domain.verification.VerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PhoneVerificationChallengeJpaRepository
        extends JpaRepository<PhoneVerificationChallengeJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PhoneVerificationChallengeJpaEntity c where c.challengeId = :id")
    Optional<PhoneVerificationChallengeJpaEntity> findByIdForUpdate(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PhoneVerificationChallengeJpaEntity>
    findFirstByPhoneE164AndPurposeAndVerifiedAtIsNullAndConsumedAtIsNullOrderByCreatedAtDesc(
            String phoneE164,
            VerificationPurpose purpose
    );

    @Query("""
            select coalesce(sum(c.sendCount), 0)
            from PhoneVerificationChallengeJpaEntity c
            where c.phoneE164 = :phone and c.purpose = :purpose and c.createdAt >= :start
            """)
    long countSendsSince(
            @Param("phone") String phoneE164,
            @Param("purpose") VerificationPurpose purpose,
            @Param("start") OffsetDateTime start
    );
}
