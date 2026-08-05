package com.allygo.allygo_api.auth.infrastructure.persistence.user;

import com.allygo.allygo_api.auth.domain.user.AccountStatus;
import com.allygo.allygo_api.auth.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "phone_e164", nullable = false, unique = true, length = 20)
    private String phoneE164;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    @Column(name = "profile_image_storage_key", length = 500)
    private String profileImageStorageKey;

    @Column(name = "nationality_code", nullable = false, length = 2)
    private String nationalityCode;

    @Column(name = "default_language_code", nullable = false, length = 35)
    private String defaultLanguageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Column(name = "phone_verified_at", nullable = false)
    private OffsetDateTime phoneVerifiedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserJpaEntity() {
    }

    public static UserJpaEntity create(
            String loginId,
            String phoneE164,
            String passwordHash,
            String name,
            String nickname,
            String nationalityCode,
            String defaultLanguageCode,
            OffsetDateTime phoneVerifiedAt
    ) {
        User validated = new User(
                null, loginId, phoneE164, passwordHash, name, nickname, null,
                nationalityCode, defaultLanguageCode, AccountStatus.ACTIVE,
                phoneVerifiedAt, null, null, 0, null, null
        );
        UserJpaEntity entity = new UserJpaEntity();
        entity.loginId = validated.loginId();
        entity.phoneE164 = validated.phoneE164();
        entity.passwordHash = validated.passwordHash();
        entity.name = validated.name();
        entity.nickname = validated.nickname();
        entity.nationalityCode = validated.nationalityCode();
        entity.defaultLanguageCode = validated.defaultLanguageCode();
        entity.accountStatus = AccountStatus.ACTIVE;
        entity.phoneVerifiedAt = phoneVerifiedAt;
        return entity;
    }

    public User toDomain() {
        return new User(
                userId, loginId, phoneE164, passwordHash, name, nickname,
                profileImageStorageKey, nationalityCode, defaultLanguageCode,
                accountStatus, phoneVerifiedAt, lastLoginAt, withdrawnAt,
                version, createdAt, updatedAt
        );
    }

    public void recordLogin(OffsetDateTime loggedInAt) {
        this.lastLoginAt = loggedInAt;
    }

    public void changePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank() || passwordHash.length() > 255) {
            throw new IllegalArgumentException("passwordHash is invalid");
        }
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
