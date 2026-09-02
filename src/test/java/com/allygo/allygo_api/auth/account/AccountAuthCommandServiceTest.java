package com.allygo.allygo_api.auth.account;

import com.allygo.allygo_api.auth.account.application.AccountAuthCommandService;
import com.allygo.allygo_api.auth.account.application.command.LoginCommand;
import com.allygo.allygo_api.auth.account.application.command.PolicyAgreementCommand;
import com.allygo.allygo_api.auth.account.application.command.SignUpCommand;
import com.allygo.allygo_api.auth.account.application.port.AccountAuthStore;
import com.allygo.allygo_api.auth.account.application.port.AccountTokenPort;
import com.allygo.allygo_api.auth.account.application.port.LoginAttemptPort;
import com.allygo.allygo_api.auth.account.application.port.ProfileImageUrlPort;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAuthCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-01T03:00:00Z");
    private static final String PHONE = "+821012345678";

    @Mock AccountAuthStore store;
    @Mock AccountTokenPort tokenPort;
    @Mock LoginAttemptPort loginAttemptPort;
    @Mock ProfileImageUrlPort profileImageUrlPort;
    @Mock TransactionOperations transactions;

    private BCryptPasswordEncoder passwordEncoder;
    private AccountAuthCommandService service;
    private AccountTokenPort.IssuedTokens issuedTokens;

    @BeforeEach
    void setUp() {
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new AccountAuthCommandService(
                store, tokenPort, loginAttemptPort, profileImageUrlPort, passwordEncoder,
                transactions, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        issuedTokens = new AccountTokenPort.IssuedTokens(
                "access", "refresh", "refresh-hash", UUID.randomUUID(),
                Duration.ofHours(1), Duration.ofDays(14)
        );
    }

    @Test
    void createsAllSignupDefaultsAndConsumesVerifiedChallenge() {
        SignUpCommand command = signUpCommand(List.of(
                new PolicyAgreementCommand(101L, true),
                new PolicyAgreementCommand(102L, true),
                new PolicyAgreementCommand(103L, false)
        ));
        when(tokenPort.parseVerificationToken("pvt_token")).thenReturn(new AccountTokenPort.VerificationTokenClaims(
                10L, VerificationPurpose.SIGN_UP, NOW.minusSeconds(30), NOW.plusSeconds(570)
        ));
        when(store.lockPhoneChallenge(10L)).thenReturn(Optional.of(new AccountAuthStore.PhoneChallenge(
                10L, PHONE, VerificationPurpose.SIGN_UP, NOW.minusSeconds(30), null, NOW.minusSeconds(90)
        )));
        when(store.resolveActiveLanguage("ko")).thenReturn("ko");
        when(store.lockPolicyDocuments(Set.of(101L, 102L, 103L))).thenReturn(List.of(
                policy(101L, true), policy(102L, true), policy(103L, false)
        ));
        when(store.findEffectiveRequiredPolicyIds("ko", NOW)).thenReturn(Set.of(101L, 102L));
        when(store.createAccount(any())).thenReturn(new AccountAuthStore.CreatedAccount(1L, 2L, 3L, NOW));
        when(tokenPort.issue(1L, NOW)).thenReturn(issuedTokens);

        var result = service.signUp(command);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.defaultLanguageCode()).isEqualTo("ko");
        assertThat(result.roleType()).isEqualTo("TRAVELER");
        assertThat(result.onboardingCompleted()).isFalse();
        assertThat(result.settings().timezoneName()).isEqualTo("UTC");
        assertThat(result.notificationPreferences()).hasSize(7).allSatisfy(preference -> {
            assertThat(preference.pushEnabled()).isTrue();
            assertThat(preference.inAppEnabled()).isTrue();
        });
        assertThat(result.tokens().refreshTokenExpiresIn()).isEqualTo(1_209_600);
        verify(store).consumePhoneChallenge(10L, NOW);
        verify(store).saveRefreshToken(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    @Test
    void rejectsSignupWhenCurrentRequiredPolicyIsMissing() {
        when(tokenPort.parseVerificationToken("pvt_token")).thenReturn(new AccountTokenPort.VerificationTokenClaims(
                10L, VerificationPurpose.SIGN_UP, NOW.minusSeconds(30), NOW.plusSeconds(570)
        ));
        when(store.lockPhoneChallenge(10L)).thenReturn(Optional.of(new AccountAuthStore.PhoneChallenge(
                10L, PHONE, VerificationPurpose.SIGN_UP, NOW.minusSeconds(30), null, NOW.minusSeconds(90)
        )));
        when(store.resolveActiveLanguage("ko")).thenReturn("ko");
        when(store.lockPolicyDocuments(Set.of(101L))).thenReturn(List.of(policy(101L, true)));
        when(store.findEffectiveRequiredPolicyIds("ko", NOW)).thenReturn(Set.of(101L, 102L));

        assertThatThrownBy(() -> service.signUp(signUpCommand(List.of(
                new PolicyAgreementCommand(101L, true)
        )))).isInstanceOfSatisfying(AccountAuthException.class, exception ->
                assertThat(exception.reason()).isEqualTo(
                        AccountAuthException.Reason.REQUIRED_POLICY_NOT_AGREED
                ));
        verify(store, never()).createAccount(any());
        verify(store, never()).consumePhoneChallenge(any(Long.class), any());
    }

    @Test
    void rejectsAConsumedVerificationBeforeCreatingAnything() {
        when(tokenPort.parseVerificationToken("pvt_token")).thenReturn(new AccountTokenPort.VerificationTokenClaims(
                10L, VerificationPurpose.SIGN_UP, NOW.minusSeconds(30), NOW.plusSeconds(570)
        ));
        when(store.lockPhoneChallenge(10L)).thenReturn(Optional.of(new AccountAuthStore.PhoneChallenge(
                10L, PHONE, VerificationPurpose.SIGN_UP, NOW.minusSeconds(30), NOW.minusSeconds(1),
                NOW.minusSeconds(90)
        )));

        assertThatThrownBy(() -> service.signUp(signUpCommand(List.of(
                new PolicyAgreementCommand(101L, true)
        )))).isInstanceOfSatisfying(AccountAuthException.class, exception ->
                assertThat(exception.reason()).isEqualTo(
                        AccountAuthException.Reason.PHONE_VERIFICATION_ALREADY_CONSUMED
                ));
        verify(store, never()).createAccount(any());
    }

    @Test
    void logsInAndPersistsAnIndependentRefreshTokenFamily() {
        String passwordHash = passwordEncoder.encode("AllyGo!2026");
        AccountAuthStore.LoginAccount account = loginAccount(passwordHash, "TRAVELER", null);
        when(store.findLoginAccount("jiwon_2026")).thenReturn(Optional.of(account));
        when(store.lockLoginAccount(1L)).thenReturn(Optional.of(account));
        when(store.findLoginRestriction(1L, NOW)).thenReturn(AccountAuthStore.LoginRestriction.none());
        when(tokenPort.issue(1L, NOW)).thenReturn(issuedTokens);

        var result = service.login(new LoginCommand(
                " JIWON_2026 ", "AllyGo!2026", "device-1", "127.0.0.1"
        ));

        assertThat(result.roleType()).isEqualTo("TRAVELER");
        assertThat(result.currentUiMode()).isEqualTo("TRAVELER");
        assertThat(result.lastLoginAt()).isEqualTo(NOW);
        verify(store).completeLogin(org.mockito.ArgumentMatchers.eq(1L), any(), org.mockito.ArgumentMatchers.eq(NOW));
        verify(loginAttemptPort).clear("jiwon_2026", "127.0.0.1");
    }

    @Test
    void correctsInvalidHelperModeAtomicallyDuringLogin() {
        AccountAuthStore.LoginAccount account = loginAccount(
                passwordEncoder.encode("AllyGo!2026"), "HELPER", "APPROVED"
        );
        when(store.findLoginAccount("jiwon_2026")).thenReturn(Optional.of(account));
        when(store.lockLoginAccount(1L)).thenReturn(Optional.of(account));
        when(store.findLoginRestriction(1L, NOW)).thenReturn(AccountAuthStore.LoginRestriction.none());
        when(store.hasRequiredHelperVerification(1L, NOW)).thenReturn(false);
        when(tokenPort.issue(1L, NOW)).thenReturn(issuedTokens);

        var result = service.login(new LoginCommand(
                "jiwon_2026", "AllyGo!2026", null, "127.0.0.1"
        ));

        assertThat(result.roleType()).isEqualTo("BOTH");
        assertThat(result.currentUiMode()).isEqualTo("TRAVELER");
        verify(store).changeCurrentUiMode(1L, "TRAVELER", NOW);
    }

    @Test
    void usesSameCredentialErrorForUnknownUserAndRecordsFailure() {
        when(store.findLoginAccount("missing_user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginCommand(
                "missing_user", "AllyGo!2026", null, "127.0.0.1"
        ))).isInstanceOfSatisfying(AccountAuthException.class, exception ->
                assertThat(exception.reason()).isEqualTo(AccountAuthException.Reason.INVALID_LOGIN_CREDENTIALS));
        verify(loginAttemptPort).recordFailure("missing_user", "127.0.0.1");
        verify(tokenPort, never()).issue(any(Long.class), any());
    }

    @Test
    void blocksBeforeReadingAccountWhenRateLimitIsActive() {
        when(loginAttemptPort.blockedForSeconds("jiwon_2026", "127.0.0.1")).thenReturn(120);

        assertThatThrownBy(() -> service.login(new LoginCommand(
                "jiwon_2026", "AllyGo!2026", null, "127.0.0.1"
        ))).isInstanceOfSatisfying(AccountAuthException.class, exception -> {
            assertThat(exception.reason()).isEqualTo(AccountAuthException.Reason.TOO_MANY_LOGIN_ATTEMPTS);
            assertThat(exception.retryAfter()).isEqualTo(120);
        });
        verify(store, never()).findLoginAccount(any());
    }

    private static SignUpCommand signUpCommand(List<PolicyAgreementCommand> policies) {
        return new SignUpCommand(
                "pvt_token", " JIWON_2026 ", "AllyGo!2026", "AllyGo!2026",
                " 김지원 ", " Jiwon ", "kr", policies, "device-1", "127.0.0.1", "test-agent"
        );
    }

    private static AccountAuthStore.PolicyDocument policy(long id, boolean required) {
        return new AccountAuthStore.PolicyDocument(
                id, "ko", required, NOW.minusSeconds(3600), null
        );
    }

    private static AccountAuthStore.LoginAccount loginAccount(
            String passwordHash,
            String currentUiMode,
            String helperApproval
    ) {
        return new AccountAuthStore.LoginAccount(
                1L, 2L, helperApproval == null ? null : 21L,
                "jiwon_2026", passwordHash, "김지원", "Jiwon", PHONE,
                "KR", "ko", "ACTIVE", helperApproval,
                helperApproval == null ? null : "UNAVAILABLE", currentUiMode, true, null
        );
    }
}
