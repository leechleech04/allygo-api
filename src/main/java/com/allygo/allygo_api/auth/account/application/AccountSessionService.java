package com.allygo.allygo_api.auth.account.application;

import com.allygo.allygo_api.auth.account.application.command.AccountWithdrawalCommand;
import com.allygo.allygo_api.auth.account.application.port.AccountAuthStore;
import com.allygo.allygo_api.auth.account.application.port.AccountTokenPort;
import com.allygo.allygo_api.auth.account.application.port.ProfileImageUrlPort;
import com.allygo.allygo_api.auth.account.application.port.TokenRefreshAttemptPort;
import com.allygo.allygo_api.auth.account.application.result.AuthTokenResult;
import com.allygo.allygo_api.auth.account.application.result.AccountWithdrawalResult;
import com.allygo.allygo_api.auth.account.application.result.CurrentUserResult;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException.Reason;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class AccountSessionService {
    private static final Set<Reason> RATE_LIMITED_REFRESH_FAILURES = Set.of(
            Reason.INVALID_REFRESH_TOKEN_FORMAT,
            Reason.INVALID_REFRESH_TOKEN,
            Reason.REFRESH_TOKEN_EXPIRED,
            Reason.REFRESH_TOKEN_REVOKED,
            Reason.REFRESH_TOKEN_REUSE_DETECTED
    );

    private final AccountAuthStore store;
    private final AccountTokenPort tokenPort;
    private final TokenRefreshAttemptPort refreshAttemptPort;
    private final ProfileImageUrlPort profileImageUrlPort;
    private final TransactionOperations transactions;
    private final Clock clock;

    public AccountSessionService(
            AccountAuthStore store,
            AccountTokenPort tokenPort,
            TokenRefreshAttemptPort refreshAttemptPort,
            ProfileImageUrlPort profileImageUrlPort,
            TransactionOperations transactions,
            Clock clock
    ) {
        this.store = store;
        this.tokenPort = tokenPort;
        this.refreshAttemptPort = refreshAttemptPort;
        this.profileImageUrlPort = profileImageUrlPort;
        this.transactions = transactions;
        this.clock = clock;
    }

    public CurrentUserResult currentUser(String authorizationHeader) {
        long userId = tokenPort.requireAccessUserId(authorizationHeader);
        Instant now = clock.instant();
        AccountAuthStore.CurrentUserAccount account = store.findCurrentUser(userId, now)
                .orElseThrow(() -> error(Reason.USER_NOT_FOUND, "사용자 계정을 찾을 수 없습니다."));
        validateCurrentUserIntegrity(account);
        validateAccountStatus(account.accountStatus());
        if (account.activeRestrictionScopes().contains("LOGIN")) {
            throw AccountAuthException.loginRestricted(account.loginRestrictionEndsAt());
        }

        boolean helperRoleValid = account.helperProfileId() != null
                && "APPROVED".equals(account.helperApprovalStatus())
                && account.requiredHelperVerificationsValid();
        String roleType = helperRoleValid ? "BOTH" : "TRAVELER";
        boolean helperModeAllowed = helperRoleValid
                && !account.activeRestrictionScopes().contains("HELPER_ACTIVITY");
        String currentUiMode = "HELPER".equals(account.currentUiMode()) && helperModeAllowed
                ? "HELPER"
                : "TRAVELER";
        ProfileImageUrlPort.SignedImageUrl image = account.profileImageStorageKey() == null
                ? null
                : profileImageUrlPort.sign(account.profileImageStorageKey(), now);
        List<String> activeRestrictions = account.activeRestrictionScopes().stream()
                .filter(scope -> !"LOGIN".equals(scope))
                .sorted()
                .toList();

        return new CurrentUserResult(
                account.userId(), account.travelerProfileId(), account.helperProfileId(),
                account.loginId(), account.name(), account.nickname(), account.phoneNumber(),
                account.nationalityCode(), account.defaultLanguageCode(), account.accountStatus(),
                roleType, account.helperApprovalStatus(), account.helperAvailabilityStatus(),
                currentUiMode, account.onboardingCompleted(), account.phoneVerifiedAt(),
                account.profileImageStorageKey(), image == null ? null : image.url(),
                image == null ? null : image.expiresAt(), account.lastLoginAt(), account.createdAt(),
                activeRestrictions
        );
    }

    public AuthTokenResult refresh(String rawRefreshToken, String ipAddress) {
        String normalizedIpAddress = normalizeIp(ipAddress);
        int blockedFor = refreshAttemptPort.blockedForSeconds(normalizedIpAddress);
        if (blockedFor > 0) {
            throw AccountAuthException.tooManyTokenRefreshAttempts(blockedFor);
        }

        try {
            validateRefreshToken(rawRefreshToken);
        } catch (AccountAuthException exception) {
            throw recordRefreshFailure(exception, normalizedIpAddress);
        }

        String tokenHash = tokenPort.hashRefreshToken(rawRefreshToken);
        Instant now = clock.instant();
        RefreshOutcome outcome;
        try {
            outcome = Objects.requireNonNull(transactions.execute(status -> rotateLocked(tokenHash, now)));
        } catch (AccountAuthException exception) {
            if (RATE_LIMITED_REFRESH_FAILURES.contains(exception.reason())) {
                throw recordRefreshFailure(exception, normalizedIpAddress);
            }
            throw exception;
        }
        if (outcome.reuseDetected()) {
            throw recordRefreshFailure(error(
                    Reason.REFRESH_TOKEN_REUSE_DETECTED,
                    "이미 교체된 Refresh Token이 재사용되어 해당 토큰 패밀리를 폐기했습니다."
            ), normalizedIpAddress);
        }
        return outcome.tokens();
    }

    public void logout(String authorizationHeader, String rawRefreshToken) {
        validateLogoutRequest(rawRefreshToken);
        long userId = tokenPort.requireAccessUserId(authorizationHeader);
        String tokenHash = tokenPort.hashRefreshToken(rawRefreshToken);
        Instant now = clock.instant();
        Objects.requireNonNull(transactions.execute(status -> {
            logoutLocked(userId, tokenHash, now);
            return Boolean.TRUE;
        }));
    }

    public AccountWithdrawalResult withdraw(
            String authorizationHeader,
            AccountWithdrawalCommand rawCommand
    ) {
        ValidatedWithdrawal command = validateWithdrawal(rawCommand);
        long userId = tokenPort.requireAccessUserId(authorizationHeader);
        AccountTokenPort.VerificationTokenClaims tokenClaims = tokenPort.parseVerificationToken(
                command.verificationToken()
        );
        Instant now = clock.instant();
        if (!now.isBefore(tokenClaims.expiresAt())) {
            throw error(Reason.VERIFICATION_TOKEN_EXPIRED, "휴대폰 인증 토큰이 만료되었습니다.");
        }
        if (tokenClaims.purpose() != VerificationPurpose.WITHDRAW_ACCOUNT) {
            throw error(
                    Reason.VERIFICATION_TOKEN_PURPOSE_MISMATCH,
                    "회원 탈퇴용 휴대폰 인증 토큰이 아닙니다."
            );
        }
        return Objects.requireNonNull(transactions.execute(
                status -> withdrawLocked(userId, command, tokenClaims, now)
        ));
    }

    private void logoutLocked(long userId, String tokenHash, Instant now) {
        AccountAuthStore.RefreshToken initial = store.findRefreshToken(tokenHash).orElse(null);
        if (initial == null) {
            return;
        }
        store.lockRefreshTokenFamily(initial.tokenFamilyId());
        AccountAuthStore.RefreshToken token = store.lockRefreshToken(tokenHash).orElse(null);
        if (token == null) {
            return;
        }
        if (token.userId() != userId) {
            throw error(
                    Reason.REFRESH_TOKEN_OWNERSHIP_MISMATCH,
                    "다른 사용자의 Refresh Token으로 로그아웃할 수 없습니다."
            );
        }
        store.revokeActiveRefreshTokenFamily(token.tokenFamilyId(), now, "LOGOUT");
    }

    private AccountWithdrawalResult withdrawLocked(
            long userId,
            ValidatedWithdrawal command,
            AccountTokenPort.VerificationTokenClaims tokenClaims,
            Instant now
    ) {
        store.lockAccountLifecycle(userId);
        AccountAuthStore.WithdrawalAccount account = store.lockWithdrawalAccount(userId)
                .orElseThrow(() -> error(Reason.USER_NOT_FOUND, "사용자 계정을 찾을 수 없습니다."));
        validateAccountStatus(account.accountStatus());

        AccountAuthStore.LoginRestriction restriction = store.findLoginRestriction(userId, now);
        if (restriction.restricted()) {
            throw AccountAuthException.loginRestricted(restriction.endsAt());
        }

        AccountAuthStore.PhoneChallenge challenge = store.lockPhoneChallenge(tokenClaims.challengeId())
                .orElseThrow(() -> error(
                        Reason.PHONE_VERIFICATION_NOT_FOUND,
                        "휴대폰 인증 요청을 찾을 수 없습니다."
                ));
        if (challenge.purpose() != VerificationPurpose.WITHDRAW_ACCOUNT) {
            throw error(
                    Reason.VERIFICATION_TOKEN_PURPOSE_MISMATCH,
                    "회원 탈퇴용 휴대폰 인증 결과가 아닙니다."
            );
        }
        if (challenge.verifiedAt() == null || !challenge.verifiedAt().truncatedTo(ChronoUnit.SECONDS)
                .equals(tokenClaims.issuedAt().truncatedTo(ChronoUnit.SECONDS))) {
            throw error(Reason.INVALID_VERIFICATION_TOKEN, "유효한 휴대폰 인증 결과와 연결되지 않은 토큰입니다.");
        }
        if (challenge.consumedAt() != null) {
            throw error(Reason.PHONE_VERIFICATION_ALREADY_CONSUMED, "이미 사용된 휴대폰 인증 결과입니다.");
        }
        if (!account.phoneE164().equals(challenge.phoneE164())) {
            throw error(Reason.PHONE_NUMBER_MISMATCH, "인증한 휴대폰 번호가 사용자 계정과 일치하지 않습니다.");
        }

        store.lockActiveHelpRequest(userId).ifPresent(resource -> {
            throw AccountAuthException.activeHelpRequest(resource.resourceId(), resource.status());
        });
        store.lockActiveHelpSession(userId).ifPresent(resource -> {
            throw AccountAuthException.activeHelpSession(resource.resourceId(), resource.status());
        });

        Instant retentionUntil = store.findWithdrawalRetentionUntil(userId, now);
        AccountAuthStore.CreatedWithdrawal created = store.completeWithdrawal(
                new AccountAuthStore.NewWithdrawal(
                        userId, challenge.challengeId(), command.reasonCode(), command.reasonDetail(),
                        now, retentionUntil
                )
        );
        return new AccountWithdrawalResult(
                created.withdrawalId(), "WITHDRAWN", created.completedAt(), created.retentionUntil()
        );
    }

    private RefreshOutcome rotateLocked(String tokenHash, Instant now) {
        AccountAuthStore.RefreshToken initial = store.findRefreshToken(tokenHash)
                .orElseThrow(() -> error(Reason.INVALID_REFRESH_TOKEN, "유효하지 않은 Refresh Token입니다."));
        store.lockAccountLifecycle(initial.userId());
        store.lockRefreshTokenFamily(initial.tokenFamilyId());
        AccountAuthStore.RefreshToken token = store.lockRefreshToken(tokenHash)
                .orElseThrow(() -> error(Reason.INVALID_REFRESH_TOKEN, "유효하지 않은 Refresh Token입니다."));

        if (token.revokedAt() != null) {
            if ("ROTATED".equals(token.revokeReason())) {
                store.revokeActiveRefreshTokenFamily(token.tokenFamilyId(), now, "REUSE_DETECTED");
                return RefreshOutcome.reuse();
            }
            throw error(Reason.REFRESH_TOKEN_REVOKED, "폐기된 Refresh Token입니다.");
        }
        if (!now.isBefore(token.expiresAt())) {
            throw error(Reason.REFRESH_TOKEN_EXPIRED, "Refresh Token이 만료되었습니다.");
        }

        String accountStatus = store.lockAccountStatus(token.userId())
                .orElseThrow(() -> error(Reason.INVALID_REFRESH_TOKEN, "유효하지 않은 Refresh Token입니다."));
        validateAccountStatus(accountStatus);
        AccountAuthStore.LoginRestriction restriction = store.findLoginRestriction(token.userId(), now);
        if (restriction.restricted()) {
            throw AccountAuthException.loginRestricted(restriction.endsAt());
        }

        AccountTokenPort.IssuedTokens issued = tokenPort.rotate(
                token.userId(), token.tokenFamilyId(), now, token.expiresAt()
        );
        store.rotateRefreshToken(
                token.userId(),
                token.refreshTokenId(),
                new AccountAuthStore.StoredRefreshToken(
                        token.tokenFamilyId(), issued.refreshTokenHash(), token.deviceId(),
                        token.expiresAt(), now
                ),
                now
        );
        return RefreshOutcome.success(new AuthTokenResult(
                issued.accessToken(), issued.refreshToken(),
                Math.toIntExact(issued.accessTokenTtl().toSeconds()),
                Math.toIntExact(issued.refreshTokenTtl().toSeconds())
        ));
    }

    private AccountAuthException recordRefreshFailure(AccountAuthException exception, String ipAddress) {
        int retryAfter = refreshAttemptPort.recordFailure(ipAddress);
        return retryAfter > 0
                ? AccountAuthException.tooManyTokenRefreshAttempts(retryAfter)
                : exception;
    }

    private static void validateRefreshToken(String refreshToken) {
        if (refreshToken == null) {
            throw error(Reason.INVALID_TOKEN_REFRESH_REQUEST, "Refresh Token을 입력해 주세요.");
        }
        if (refreshToken.isBlank() || refreshToken.length() > 2_048) {
            throw error(Reason.INVALID_REFRESH_TOKEN_FORMAT, "Refresh Token 형식이 올바르지 않습니다.");
        }
    }

    private static void validateLogoutRequest(String refreshToken) {
        if (refreshToken == null) {
            throw error(Reason.INVALID_LOGOUT_REQUEST, "Refresh Token을 입력해 주세요.");
        }
        if (refreshToken.isBlank() || refreshToken.length() > 2_048) {
            throw error(Reason.INVALID_REFRESH_TOKEN_FORMAT, "Refresh Token 형식이 올바르지 않습니다.");
        }
    }

    private static ValidatedWithdrawal validateWithdrawal(AccountWithdrawalCommand command) {
        if (command == null || command.verificationToken() == null
                || command.verificationToken().isBlank()
                || command.verificationToken().length() > 2_048
                || !Boolean.TRUE.equals(command.confirmed())) {
            throw error(Reason.INVALID_ACCOUNT_WITHDRAWAL_REQUEST, "회원 탈퇴 요청 형식이 올바르지 않습니다.");
        }
        String reasonCode = trim(command.reasonCode());
        if (command.reasonCode() != null
                && (reasonCode.isEmpty() || reasonCode.length() > 50)) {
            throw error(Reason.INVALID_ACCOUNT_WITHDRAWAL_REQUEST, "탈퇴 사유 코드 형식이 올바르지 않습니다.");
        }
        String reasonDetail = trim(command.reasonDetail());
        if (reasonDetail != null && reasonDetail.isEmpty()) {
            reasonDetail = null;
        }
        return new ValidatedWithdrawal(command.verificationToken(), reasonCode, reasonDetail);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static void validateCurrentUserIntegrity(AccountAuthStore.CurrentUserAccount account) {
        if (account.travelerProfileId() == null || account.currentUiMode() == null
                || account.phoneVerifiedAt() == null || account.createdAt() == null) {
            throw new IllegalStateException("Current user is missing required profile or settings data");
        }
        boolean helperMissing = account.helperProfileId() == null;
        if (helperMissing != (account.helperApprovalStatus() == null)
                || helperMissing != (account.helperAvailabilityStatus() == null)) {
            throw new IllegalStateException("Current user has inconsistent helper profile data");
        }
    }

    private static void validateAccountStatus(String status) {
        switch (status) {
            case "ACTIVE" -> { }
            case "SUSPENDED" -> throw error(Reason.ACCOUNT_SUSPENDED, "정지된 계정입니다.");
            case "BANNED" -> throw error(Reason.ACCOUNT_BANNED, "차단된 계정입니다.");
            case "WITHDRAWN" -> throw error(Reason.ACCOUNT_WITHDRAWN, "탈퇴한 계정입니다.");
            default -> throw new IllegalStateException("Unknown account status: " + status);
        }
    }

    private static String normalizeIp(String ipAddress) {
        return ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress;
    }

    private static AccountAuthException error(Reason reason, String message) {
        return AccountAuthException.of(reason, message);
    }

    private record RefreshOutcome(AuthTokenResult tokens, boolean reuseDetected) {
        static RefreshOutcome success(AuthTokenResult tokens) {
            return new RefreshOutcome(tokens, false);
        }

        static RefreshOutcome reuse() {
            return new RefreshOutcome(null, true);
        }
    }

    private record ValidatedWithdrawal(String verificationToken, String reasonCode, String reasonDetail) {
    }
}
