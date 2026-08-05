package com.allygo.allygo_api.auth.domain.user;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

public record User(
        Long userId,
        String loginId,
        String phoneE164,
        String passwordHash,
        String name,
        String nickname,
        String profileImageStorageKey,
        String nationalityCode,
        String defaultLanguageCode,
        AccountStatus accountStatus,
        OffsetDateTime phoneVerifiedAt,
        OffsetDateTime lastLoginAt,
        OffsetDateTime withdrawnAt,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public User {
        loginId = normalizeLoginId(loginId);
        requireLength(phoneE164, "phoneE164", 20);
        requireLength(passwordHash, "passwordHash", 255);
        requireLength(name, "name", 100);
        requireLength(nickname, "nickname", 30);
        requireLength(nationalityCode, "nationalityCode", 2);
        requireLength(defaultLanguageCode, "defaultLanguageCode", 35);
        Objects.requireNonNull(accountStatus, "accountStatus must not be null");
        Objects.requireNonNull(phoneVerifiedAt, "phoneVerifiedAt must not be null");
        if (!phoneE164.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new IllegalArgumentException("phoneE164 must use E.164 format");
        }
        nationalityCode = nationalityCode.toUpperCase(Locale.ROOT);
        if (accountStatus == AccountStatus.WITHDRAWN && withdrawnAt == null) {
            throw new IllegalArgumentException("withdrawnAt is required for a withdrawn account");
        }
    }

    public static String normalizeLoginId(String loginId) {
        if (loginId == null || loginId.isBlank() || loginId.length() > 50) {
            throw new IllegalArgumentException("loginId must contain 1 to 50 characters");
        }
        return loginId.strip().toLowerCase(Locale.ROOT);
    }

    private static void requireLength(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maxLength + " characters");
        }
    }
}
