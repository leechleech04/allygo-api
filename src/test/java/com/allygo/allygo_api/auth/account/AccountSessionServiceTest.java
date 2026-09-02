package com.allygo.allygo_api.auth.account;

import com.allygo.allygo_api.auth.account.application.AccountSessionService;
import com.allygo.allygo_api.auth.account.application.command.AccountWithdrawalCommand;
import com.allygo.allygo_api.auth.account.application.port.AccountAuthStore;
import com.allygo.allygo_api.auth.account.application.port.AccountTokenPort;
import com.allygo.allygo_api.auth.account.application.port.ProfileImageUrlPort;
import com.allygo.allygo_api.auth.account.application.port.TokenRefreshAttemptPort;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AccountSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-02T03:00:00Z");
    private static final UUID FAMILY_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock AccountAuthStore store;
    @Mock AccountTokenPort tokenPort;
    @Mock TokenRefreshAttemptPort refreshAttemptPort;
    @Mock ProfileImageUrlPort profileImageUrlPort;
    @Mock TransactionOperations transactions;

    private AccountSessionService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        service = new AccountSessionService(
                store, tokenPort, refreshAttemptPort, profileImageUrlPort,
                transactions, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void retrievesCurrentUserWithoutChangingStoredHelperMode() {
        when(tokenPort.requireAccessUserId("Bearer access")).thenReturn(1L);
        when(store.findCurrentUser(1L, NOW)).thenReturn(Optional.of(currentUser(
                "HELPER", true, Set.of("CREATE_REQUEST", "HELPER_ACTIVITY")
        )));
        when(profileImageUrlPort.sign("profiles/1/avatar.jpg", NOW)).thenReturn(
                new ProfileImageUrlPort.SignedImageUrl(
                        "https://signed.example/avatar", NOW.plusSeconds(300)
                )
        );

        var result = service.currentUser("Bearer access");

        assertThat(result.roleType()).isEqualTo("BOTH");
        assertThat(result.currentUiMode()).isEqualTo("TRAVELER");
        assertThat(result.profileImageStorageKey()).isEqualTo("profiles/1/avatar.jpg");
        assertThat(result.profileImageUrl()).isEqualTo("https://signed.example/avatar");
        assertThat(result.activeRestrictionScopes()).containsExactly("CREATE_REQUEST", "HELPER_ACTIVITY");
    }

    @Test
    void blocksCurrentUserWhenLoginRestrictionIsActive() {
        when(tokenPort.requireAccessUserId("Bearer access")).thenReturn(1L);
        when(store.findCurrentUser(1L, NOW)).thenReturn(Optional.of(currentUser(
                "TRAVELER", true, Set.of("LOGIN")
        )));

        assertThatThrownBy(() -> service.currentUser("Bearer access"))
                .isInstanceOfSatisfying(AccountAuthException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(AccountAuthException.Reason.LOGIN_RESTRICTED);
                    assertThat(exception.restrictionEndsAt()).isEqualTo(NOW.plusSeconds(600));
                });
    }

    @Test
    void treatsMissingRequiredCurrentUserRowsAsIntegrityFailure() {
        when(tokenPort.requireAccessUserId("Bearer access")).thenReturn(1L);
        AccountAuthStore.CurrentUserAccount account = new AccountAuthStore.CurrentUserAccount(
                1L, null, null, "jiwon_2026", "김지원", "Jiwon", "+821012345678",
                "KR", "ko", "ACTIVE", null, null, null, false,
                NOW.minusSeconds(3600), null, NOW.minusSeconds(30), NOW.minusSeconds(7200),
                false, Set.of(), null
        );
        when(store.findCurrentUser(1L, NOW)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.currentUser("Bearer access"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required profile or settings");
    }

    @Test
    void rotatesRefreshTokenWithoutExtendingFamilyExpiration() {
        Instant familyExpiresAt = NOW.plusSeconds(10_000);
        AccountAuthStore.RefreshToken token = refreshToken(null, null, familyExpiresAt);
        when(tokenPort.hashRefreshToken("rt_old")).thenReturn("old-hash");
        when(store.findRefreshToken("old-hash")).thenReturn(Optional.of(token));
        when(store.lockRefreshToken("old-hash")).thenReturn(Optional.of(token));
        when(store.lockAccountStatus(1L)).thenReturn(Optional.of("ACTIVE"));
        when(store.findLoginRestriction(1L, NOW)).thenReturn(AccountAuthStore.LoginRestriction.none());
        when(tokenPort.rotate(1L, FAMILY_ID, NOW, familyExpiresAt)).thenReturn(
                new AccountTokenPort.IssuedTokens(
                        "new-access", "rt_new", "new-hash", FAMILY_ID,
                        Duration.ofHours(1), Duration.ofSeconds(10_000)
                )
        );

        var result = service.refresh("rt_old", "127.0.0.1");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("rt_new");
        assertThat(result.refreshTokenExpiresIn()).isEqualTo(10_000);
        ArgumentCaptor<AccountAuthStore.StoredRefreshToken> stored =
                ArgumentCaptor.forClass(AccountAuthStore.StoredRefreshToken.class);
        verify(store).rotateRefreshToken(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                stored.capture(),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
        assertThat(stored.getValue().tokenFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(stored.getValue().expiresAt()).isEqualTo(familyExpiresAt);
        assertThat(stored.getValue().deviceId()).isEqualTo("device-1");
    }

    @Test
    void revokesActiveFamilyWhenRotatedTokenIsReused() {
        AccountAuthStore.RefreshToken token = refreshToken(
                NOW.minusSeconds(5), "ROTATED", NOW.plusSeconds(10_000)
        );
        when(tokenPort.hashRefreshToken("rt_old")).thenReturn("old-hash");
        when(store.findRefreshToken("old-hash")).thenReturn(Optional.of(token));
        when(store.lockRefreshToken("old-hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh("rt_old", "127.0.0.1"))
                .isInstanceOfSatisfying(AccountAuthException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                AccountAuthException.Reason.REFRESH_TOKEN_REUSE_DETECTED
                        ));
        verify(store).revokeActiveRefreshTokenFamily(FAMILY_ID, NOW, "REUSE_DETECTED");
        verify(refreshAttemptPort).recordFailure("127.0.0.1");
    }

    @Test
    void rejectsBlockedRefreshBeforeHashingOrReadingDatabase() {
        when(refreshAttemptPort.blockedForSeconds("127.0.0.1")).thenReturn(120);

        assertThatThrownBy(() -> service.refresh("rt_old", "127.0.0.1"))
                .isInstanceOfSatisfying(AccountAuthException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(
                            AccountAuthException.Reason.TOO_MANY_TOKEN_REFRESH_ATTEMPTS
                    );
                    assertThat(exception.retryAfter()).isEqualTo(120);
                });
    }

    @Test
    void logsOutOnlyTheMatchingRefreshTokenFamily() {
        AccountAuthStore.RefreshToken token = refreshToken(null, null, NOW.plusSeconds(10_000));
        when(tokenPort.requireAccessUserId("Bearer access")).thenReturn(1L);
        when(tokenPort.hashRefreshToken("rt_current")).thenReturn("current-hash");
        when(store.findRefreshToken("current-hash")).thenReturn(Optional.of(token));
        when(store.lockRefreshToken("current-hash")).thenReturn(Optional.of(token));

        service.logout("Bearer access", "rt_current");

        verify(store).lockRefreshTokenFamily(FAMILY_ID);
        verify(store).revokeActiveRefreshTokenFamily(FAMILY_ID, NOW, "LOGOUT");
    }

    @Test
    void treatsUnknownRefreshTokenAsIdempotentLogoutSuccess() {
        when(tokenPort.requireAccessUserId("Bearer access")).thenReturn(1L);
        when(tokenPort.hashRefreshToken("rt_unknown")).thenReturn("unknown-hash");
        when(store.findRefreshToken("unknown-hash")).thenReturn(Optional.empty());

        service.logout("Bearer access", "rt_unknown");

        verify(store, never()).revokeActiveRefreshTokenFamily(any(), any(), any());
    }

    @Test
    void rejectsLogoutWithAnotherUsersRefreshTokenWithoutRevocation() {
        AccountAuthStore.RefreshToken token = new AccountAuthStore.RefreshToken(
                10L, 2L, FAMILY_ID, "other-hash", "device-2", NOW.plusSeconds(10_000),
                null, null, null, NOW.minusSeconds(600)
        );
        when(tokenPort.requireAccessUserId("Bearer access")).thenReturn(1L);
        when(tokenPort.hashRefreshToken("rt_other")).thenReturn("other-hash");
        when(store.findRefreshToken("other-hash")).thenReturn(Optional.of(token));
        when(store.lockRefreshToken("other-hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.logout("Bearer access", "rt_other"))
                .isInstanceOfSatisfying(AccountAuthException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                AccountAuthException.Reason.REFRESH_TOKEN_OWNERSHIP_MISMATCH
                        ));
        verify(store, never()).revokeActiveRefreshTokenFamily(any(), any(), any());
    }

    @Test
    void withdrawsAccountAfterRevalidatingIdentityAndActiveResources() {
        Instant verifiedAt = NOW.minusSeconds(60);
        Instant retentionUntil = NOW.plus(Duration.ofDays(30));
        stubWithdrawalIdentity(verifiedAt);
        when(store.lockActiveHelpRequest(1L)).thenReturn(Optional.empty());
        when(store.lockActiveHelpSession(1L)).thenReturn(Optional.empty());
        when(store.findWithdrawalRetentionUntil(1L, NOW)).thenReturn(retentionUntil);
        when(store.completeWithdrawal(any())).thenReturn(
                new AccountAuthStore.CreatedWithdrawal(501L, NOW, retentionUntil)
        );

        var result = service.withdraw("Bearer access", new AccountWithdrawalCommand(
                "pvt_withdraw", "  NO_LONGER_NEEDED  ", "  더 이상 사용하지 않음  ", true
        ));

        assertThat(result.withdrawalId()).isEqualTo(501L);
        assertThat(result.accountStatus()).isEqualTo("WITHDRAWN");
        assertThat(result.withdrawnAt()).isEqualTo(NOW);
        assertThat(result.retentionUntil()).isEqualTo(retentionUntil);
        ArgumentCaptor<AccountAuthStore.NewWithdrawal> withdrawal =
                ArgumentCaptor.forClass(AccountAuthStore.NewWithdrawal.class);
        verify(store).completeWithdrawal(withdrawal.capture());
        assertThat(withdrawal.getValue().userId()).isEqualTo(1L);
        assertThat(withdrawal.getValue().challengeId()).isEqualTo(99L);
        assertThat(withdrawal.getValue().reasonCode()).isEqualTo("NO_LONGER_NEEDED");
        assertThat(withdrawal.getValue().reasonDetail()).isEqualTo("더 이상 사용하지 않음");
    }

    @Test
    void doesNotCompleteOrConsumeWithdrawalWhenActiveRequestExists() {
        stubWithdrawalIdentity(NOW.minusSeconds(60));
        when(store.lockActiveHelpRequest(1L)).thenReturn(Optional.of(
                new AccountAuthStore.ActiveResource(77L, "MATCHING")
        ));

        assertThatThrownBy(() -> service.withdraw("Bearer access", new AccountWithdrawalCommand(
                "pvt_withdraw", null, null, true
        ))).isInstanceOfSatisfying(AccountAuthException.class, exception -> {
            assertThat(exception.reason()).isEqualTo(AccountAuthException.Reason.ACTIVE_HELP_REQUEST_EXISTS);
            assertThat(exception.activeResourceType()).isEqualTo("HELP_REQUEST");
            assertThat(exception.activeResourceId()).isEqualTo(77L);
            assertThat(exception.activeResourceStatus()).isEqualTo("MATCHING");
        });
        verify(store, never()).completeWithdrawal(any());
    }

    @Test
    void doesNotCompleteWithdrawalWhenUserIsActiveHelperInSession() {
        stubWithdrawalIdentity(NOW.minusSeconds(60));
        when(store.lockActiveHelpRequest(1L)).thenReturn(Optional.empty());
        when(store.lockActiveHelpSession(1L)).thenReturn(Optional.of(
                new AccountAuthStore.ActiveResource(88L, "ACTIVE")
        ));

        assertThatThrownBy(() -> service.withdraw("Bearer access", new AccountWithdrawalCommand(
                "pvt_withdraw", null, null, true
        ))).isInstanceOfSatisfying(AccountAuthException.class, exception -> {
            assertThat(exception.reason()).isEqualTo(AccountAuthException.Reason.ACTIVE_HELP_SESSION_EXISTS);
            assertThat(exception.activeResourceType()).isEqualTo("HELP_SESSION");
            assertThat(exception.activeResourceId()).isEqualTo(88L);
        });
        verify(store, never()).completeWithdrawal(any());
    }

    @Test
    void rejectsWithdrawalWhenConfirmedFlagIsNotTrue() {
        assertThatThrownBy(() -> service.withdraw("Bearer access", new AccountWithdrawalCommand(
                "pvt_withdraw", null, null, false
        ))).isInstanceOfSatisfying(AccountAuthException.class, exception ->
                assertThat(exception.reason()).isEqualTo(
                        AccountAuthException.Reason.INVALID_ACCOUNT_WITHDRAWAL_REQUEST
                ));
    }

    private void stubWithdrawalIdentity(Instant verifiedAt) {
        when(tokenPort.requireAccessUserId("Bearer access")).thenReturn(1L);
        when(tokenPort.parseVerificationToken("pvt_withdraw")).thenReturn(
                new AccountTokenPort.VerificationTokenClaims(
                        99L, VerificationPurpose.WITHDRAW_ACCOUNT,
                        verifiedAt, NOW.plusSeconds(540)
                )
        );
        when(store.lockWithdrawalAccount(1L)).thenReturn(Optional.of(
                new AccountAuthStore.WithdrawalAccount(1L, "+821012345678", "ACTIVE")
        ));
        when(store.findLoginRestriction(1L, NOW)).thenReturn(AccountAuthStore.LoginRestriction.none());
        when(store.lockPhoneChallenge(99L)).thenReturn(Optional.of(
                new AccountAuthStore.PhoneChallenge(
                        99L, "+821012345678", VerificationPurpose.WITHDRAW_ACCOUNT,
                        verifiedAt, null, NOW.minusSeconds(180)
                )
        ));
    }

    private static AccountAuthStore.CurrentUserAccount currentUser(
            String currentUiMode,
            boolean requiredVerificationsValid,
            Set<String> restrictions
    ) {
        return new AccountAuthStore.CurrentUserAccount(
                1L, 2L, 21L, "jiwon_2026", "김지원", "Jiwon", "+821012345678",
                "KR", "ko", "ACTIVE", "APPROVED", "UNAVAILABLE", currentUiMode, true,
                NOW.minusSeconds(3600), "profiles/1/avatar.jpg", NOW.minusSeconds(30),
                NOW.minusSeconds(7200), requiredVerificationsValid, restrictions,
                restrictions.contains("LOGIN") ? NOW.plusSeconds(600) : null
        );
    }

    private static AccountAuthStore.RefreshToken refreshToken(
            Instant revokedAt,
            String revokeReason,
            Instant expiresAt
    ) {
        return new AccountAuthStore.RefreshToken(
                10L, 1L, FAMILY_ID, "old-hash", "device-1", expiresAt,
                revokedAt == null ? null : revokedAt.minusSeconds(1), revokedAt, revokeReason,
                NOW.minusSeconds(600)
        );
    }
}
