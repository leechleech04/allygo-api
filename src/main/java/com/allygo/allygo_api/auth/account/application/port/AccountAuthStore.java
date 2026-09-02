package com.allygo.allygo_api.auth.account.application.port;

import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AccountAuthStore {
    Optional<PhoneChallenge> findPhoneChallenge(long challengeId);

    Optional<PhoneChallenge> lockPhoneChallenge(long challengeId);

    List<PhoneChallenge> lockPhoneChallenges(String phoneE164, VerificationPurpose purpose);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByPhone(String phoneE164);

    String resolveActiveLanguage(String preferredLanguageCode);

    List<PolicyDocument> lockPolicyDocuments(Set<Long> policyDocumentIds);

    Set<Long> findEffectiveRequiredPolicyIds(String languageCode, Instant now);

    CreatedAccount createAccount(NewAccount account);

    void saveRefreshToken(long userId, StoredRefreshToken refreshToken);

    void consumePhoneChallenge(long challengeId, Instant consumedAt);

    Optional<RecoveryAccount> findRecoveryAccountByPhone(String phoneE164);

    Optional<RecoveryAccount> lockRecoveryAccountByPhone(String phoneE164);

    void completePasswordReset(long userId, String phoneE164, String passwordHash, Instant resetAt);

    Optional<LoginAccount> findLoginAccount(String normalizedLoginId);

    Optional<LoginAccount> lockLoginAccount(long userId);

    LoginRestriction findLoginRestriction(long userId, Instant now);

    boolean hasHelperActivityRestriction(long userId, Instant now);

    boolean hasRequiredHelperVerification(long userId, Instant now);

    void changeCurrentUiMode(long userId, String currentUiMode, Instant now);

    void completeLogin(long userId, StoredRefreshToken refreshToken, Instant loginAt);

    Optional<CurrentUserAccount> findCurrentUser(long userId, Instant now);

    Optional<RefreshToken> findRefreshToken(String tokenHash);

    void lockRefreshTokenFamily(UUID tokenFamilyId);

    Optional<RefreshToken> lockRefreshToken(String tokenHash);

    Optional<String> lockAccountStatus(long userId);

    void rotateRefreshToken(long userId, long refreshTokenId, StoredRefreshToken refreshToken, Instant rotatedAt);

    void revokeActiveRefreshTokenFamily(UUID tokenFamilyId, Instant revokedAt, String reason);

    void lockAccountLifecycle(long userId);

    Optional<WithdrawalAccount> lockWithdrawalAccount(long userId);

    Optional<ActiveResource> lockActiveHelpRequest(long userId);

    Optional<ActiveResource> lockActiveHelpSession(long userId);

    Instant findWithdrawalRetentionUntil(long userId, Instant withdrawnAt);

    CreatedWithdrawal completeWithdrawal(NewWithdrawal withdrawal);

    record PhoneChallenge(
            long challengeId,
            String phoneE164,
            VerificationPurpose purpose,
            Instant verifiedAt,
            Instant consumedAt,
            Instant createdAt
    ) {
    }

    record RecoveryAccount(long userId, String loginId, String accountStatus) {
    }

    record PolicyDocument(
            long policyDocumentId,
            String languageCode,
            boolean required,
            Instant effectiveAt,
            Instant retiredAt
    ) {
        public boolean isEffectiveAt(Instant instant) {
            return !effectiveAt.isAfter(instant) && (retiredAt == null || retiredAt.isAfter(instant));
        }
    }

    record PolicyAgreement(long policyDocumentId, boolean agreed) {
    }

    record NewAccount(
            String loginId,
            String phoneE164,
            String passwordHash,
            String name,
            String nickname,
            String nationalityCode,
            String defaultLanguageCode,
            Instant phoneVerifiedAt,
            long challengeId,
            List<PolicyAgreement> policyAgreements,
            String ipAddress,
            String userAgent,
            Instant now
    ) {
    }

    record CreatedAccount(long userId, long travelerProfileId, long phoneVerificationId, Instant createdAt) {
    }

    record StoredRefreshToken(
            UUID tokenFamilyId,
            String tokenHash,
            String deviceId,
            Instant expiresAt,
            Instant createdAt
    ) {
    }

    record LoginAccount(
            long userId,
            long travelerProfileId,
            Long helperProfileId,
            String loginId,
            String passwordHash,
            String name,
            String nickname,
            String phoneNumber,
            String nationalityCode,
            String defaultLanguageCode,
            String accountStatus,
            String helperApprovalStatus,
            String helperAvailabilityStatus,
            String currentUiMode,
            boolean onboardingCompleted,
            String profileImageStorageKey
    ) {
    }

    record LoginRestriction(boolean restricted, Instant endsAt) {
        public static LoginRestriction none() {
            return new LoginRestriction(false, null);
        }
    }

    record CurrentUserAccount(
            long userId,
            Long travelerProfileId,
            Long helperProfileId,
            String loginId,
            String name,
            String nickname,
            String phoneNumber,
            String nationalityCode,
            String defaultLanguageCode,
            String accountStatus,
            String helperApprovalStatus,
            String helperAvailabilityStatus,
            String currentUiMode,
            boolean onboardingCompleted,
            Instant phoneVerifiedAt,
            String profileImageStorageKey,
            Instant lastLoginAt,
            Instant createdAt,
            boolean requiredHelperVerificationsValid,
            Set<String> activeRestrictionScopes,
            Instant loginRestrictionEndsAt
    ) {
    }

    record RefreshToken(
            long refreshTokenId,
            long userId,
            UUID tokenFamilyId,
            String tokenHash,
            String deviceId,
            Instant expiresAt,
            Instant lastUsedAt,
            Instant revokedAt,
            String revokeReason,
            Instant createdAt
    ) {
    }

    record WithdrawalAccount(long userId, String phoneE164, String accountStatus) {
    }

    record ActiveResource(long resourceId, String status) {
    }

    record NewWithdrawal(
            long userId,
            long challengeId,
            String reasonCode,
            String reasonDetail,
            Instant requestedAt,
            Instant retentionUntil
    ) {
    }

    record CreatedWithdrawal(long withdrawalId, Instant completedAt, Instant retentionUntil) {
    }
}
