package com.allygo.allygo_api.auth.infrastructure.persistence.token;

import com.allygo.allygo_api.auth.domain.token.RefreshTokenRevokeReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_family_id", nullable = false)
    private UUID tokenFamilyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 50)
    private RefreshTokenRevokeReason revokeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RefreshTokenJpaEntity() {
    }

    public static RefreshTokenJpaEntity create(
            long userId,
            UUID familyId,
            String tokenHash,
            String deviceId,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt
    ) {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash must not be blank");
        }
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.userId = userId;
        entity.tokenFamilyId = familyId;
        entity.tokenHash = tokenHash;
        entity.deviceId = deviceId;
        entity.expiresAt = expiresAt;
        entity.createdAt = createdAt;
        return entity;
    }

    public void revoke(RefreshTokenRevokeReason reason, OffsetDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
            revokeReason = reason;
        } else if (reason == RefreshTokenRevokeReason.REUSE_DETECTED) {
            revokeReason = reason;
        }
    }

    public void markRotated(OffsetDateTime now) {
        lastUsedAt = now;
        revokedAt = now;
        revokeReason = RefreshTokenRevokeReason.ROTATED;
    }

    public boolean isUsableAt(OffsetDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public Long getRefreshTokenId() {
        return refreshTokenId;
    }

    public Long getUserId() {
        return userId;
    }

    public UUID getTokenFamilyId() {
        return tokenFamilyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public RefreshTokenRevokeReason getRevokeReason() {
        return revokeReason;
    }
}
