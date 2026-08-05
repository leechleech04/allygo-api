package com.allygo.allygo_api.auth.infrastructure.persistence.token;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RefreshTokenJpaEntity> findAllByTokenFamilyIdOrderByRefreshTokenId(UUID tokenFamilyId);

    @Modifying
    @Query("""
            update RefreshTokenJpaEntity t
            set t.revokedAt = :now, t.revokeReason = :reason
            where t.userId = :userId and t.revokedAt is null and t.expiresAt > :now
            """)
    int revokeAllActiveByUserId(
            @Param("userId") long userId,
            @Param("reason") com.allygo.allygo_api.auth.domain.token.RefreshTokenRevokeReason reason,
            @Param("now") OffsetDateTime now
    );
}
