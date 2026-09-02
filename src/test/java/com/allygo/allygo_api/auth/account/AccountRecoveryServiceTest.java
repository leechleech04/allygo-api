package com.allygo.allygo_api.auth.account;

import com.allygo.allygo_api.auth.account.application.AccountAuthCommandService;
import com.allygo.allygo_api.auth.account.application.command.FindLoginIdCommand;
import com.allygo.allygo_api.auth.account.application.command.PasswordResetCommand;
import com.allygo.allygo_api.auth.account.application.port.AccountAuthStore;
import com.allygo.allygo_api.auth.account.application.port.AccountTokenPort;
import com.allygo.allygo_api.auth.account.application.port.LoginAttemptPort;
import com.allygo.allygo_api.auth.account.application.port.ProfileImageUrlPort;
import com.allygo.allygo_api.auth.account.application.result.LoginIdLookupResult;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-02T03:00:00Z");
    private static final Instant VERIFIED_AT = NOW.minusSeconds(30);
    private static final String PHONE = "+821012345678";

    @Mock AccountAuthStore store;
    @Mock AccountTokenPort tokenPort;
    @Mock LoginAttemptPort loginAttemptPort;
    @Mock ProfileImageUrlPort profileImageUrlPort;
    @Mock TransactionOperations transactions;

    private BCryptPasswordEncoder passwordEncoder;
    private AccountAuthCommandService service;

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
    }

    @Test
    void findsMaskedLoginIdForBannedAccountAndConsumesChallenge() {
        AccountTokenPort.VerificationTokenClaims claims = claims(10L, VerificationPurpose.FIND_LOGIN_ID);
        when(tokenPort.parseVerificationToken("pvt_find")).thenReturn(claims);
        AccountAuthStore.PhoneChallenge challenge = challenge(10L, VerificationPurpose.FIND_LOGIN_ID);
        AccountAuthStore.RecoveryAccount account = new AccountAuthStore.RecoveryAccount(1L, "allygo", "BANNED");
        when(store.findPhoneChallenge(10L)).thenReturn(Optional.of(challenge));
        when(store.findRecoveryAccountByPhone(PHONE)).thenReturn(Optional.of(account));
        when(store.lockRecoveryAccountByPhone(PHONE)).thenReturn(Optional.of(account));
        when(store.lockPhoneChallenge(10L)).thenReturn(Optional.of(challenge));

        LoginIdLookupResult result = service.findLoginId(new FindLoginIdCommand("pvt_find"));

        assertThat(result.maskedLoginId()).isEqualTo("al**go");
        verify(store).consumePhoneChallenge(10L, NOW);
    }

    @Test
    void masksFourCharacterLoginIdWithOnlyEndpointsVisible() {
        when(tokenPort.parseVerificationToken("pvt_find")).thenReturn(
                claims(10L, VerificationPurpose.FIND_LOGIN_ID)
        );
        AccountAuthStore.PhoneChallenge challenge = challenge(10L, VerificationPurpose.FIND_LOGIN_ID);
        AccountAuthStore.RecoveryAccount account = new AccountAuthStore.RecoveryAccount(1L, "abcd", "ACTIVE");
        when(store.findPhoneChallenge(10L)).thenReturn(Optional.of(challenge));
        when(store.findRecoveryAccountByPhone(PHONE)).thenReturn(Optional.of(account));
        when(store.lockRecoveryAccountByPhone(PHONE)).thenReturn(Optional.of(account));
        when(store.lockPhoneChallenge(10L)).thenReturn(Optional.of(challenge));

        assertThat(service.findLoginId(new FindLoginIdCommand("pvt_find")).maskedLoginId())
                .isEqualTo("a**d");
    }

    @Test
    void doesNotConsumeLookupChallengeWhenAccountIsWithdrawn() {
        when(tokenPort.parseVerificationToken("pvt_find")).thenReturn(
                claims(10L, VerificationPurpose.FIND_LOGIN_ID)
        );
        AccountAuthStore.PhoneChallenge challenge = challenge(10L, VerificationPurpose.FIND_LOGIN_ID);
        AccountAuthStore.RecoveryAccount account = new AccountAuthStore.RecoveryAccount(1L, "allygo", "WITHDRAWN");
        when(store.findPhoneChallenge(10L)).thenReturn(Optional.of(challenge));
        when(store.findRecoveryAccountByPhone(PHONE)).thenReturn(Optional.of(account));
        when(store.lockRecoveryAccountByPhone(PHONE)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.findLoginId(new FindLoginIdCommand("pvt_find")))
                .isInstanceOfSatisfying(AccountAuthException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(AccountAuthException.Reason.ACCOUNT_WITHDRAWN));
        verify(store, never()).consumePhoneChallenge(any(Long.class), any(Instant.class));
    }

    @Test
    void resetsPasswordAfterLockingUserAndAllResetChallenges() {
        AccountAuthStore.PhoneChallenge target = challenge(20L, VerificationPurpose.RESET_PASSWORD);
        when(tokenPort.parseVerificationToken("pvt_reset")).thenReturn(
                claims(20L, VerificationPurpose.RESET_PASSWORD)
        );
        when(store.findPhoneChallenge(20L)).thenReturn(Optional.of(target));
        AccountAuthStore.RecoveryAccount account = new AccountAuthStore.RecoveryAccount(1L, "allygo", "SUSPENDED");
        when(store.findRecoveryAccountByPhone(PHONE)).thenReturn(Optional.of(account));
        when(store.lockRecoveryAccountByPhone(PHONE)).thenReturn(Optional.of(account));
        when(store.lockPhoneChallenges(PHONE, VerificationPurpose.RESET_PASSWORD)).thenReturn(List.of(
                challenge(19L, VerificationPurpose.RESET_PASSWORD), target
        ));

        service.resetPassword(new PasswordResetCommand(
                "pvt_reset", "NewAllyGo!2026", "NewAllyGo!2026"
        ));

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(store).completePasswordReset(eq(1L), eq(PHONE), passwordHash.capture(), eq(NOW));
        assertThat(passwordEncoder.matches("NewAllyGo!2026", passwordHash.getValue())).isTrue();

        InOrder order = inOrder(store);
        order.verify(store).findPhoneChallenge(20L);
        order.verify(store).findRecoveryAccountByPhone(PHONE);
        order.verify(store).lockAccountLifecycle(1L);
        order.verify(store).lockRecoveryAccountByPhone(PHONE);
        order.verify(store).lockPhoneChallenges(PHONE, VerificationPurpose.RESET_PASSWORD);
        order.verify(store).completePasswordReset(eq(1L), eq(PHONE), any(String.class), eq(NOW));
    }

    @Test
    void rejectsWrongPurposeBeforeOpeningRecoveryTransaction() {
        when(tokenPort.parseVerificationToken("pvt_signup")).thenReturn(
                claims(10L, VerificationPurpose.SIGN_UP)
        );

        assertThatThrownBy(() -> service.findLoginId(new FindLoginIdCommand("pvt_signup")))
                .isInstanceOfSatisfying(AccountAuthException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                AccountAuthException.Reason.VERIFICATION_TOKEN_PURPOSE_MISMATCH
                        ));
        verify(store, never()).lockPhoneChallenge(any(Long.class));
    }

    @Test
    void rejectsMalformedPasswordBeforeParsingVerificationToken() {
        assertThatThrownBy(() -> service.resetPassword(new PasswordResetCommand(
                "pvt_reset", "withoutspecial2026", "withoutspecial2026"
        ))).isInstanceOfSatisfying(AccountAuthException.class, exception ->
                assertThat(exception.reason()).isEqualTo(AccountAuthException.Reason.INVALID_PASSWORD_FORMAT));
        verify(tokenPort, never()).parseVerificationToken(any());
    }

    @Test
    void rejectsPasswordConfirmationMismatch() {
        assertThatThrownBy(() -> service.resetPassword(new PasswordResetCommand(
                "pvt_reset", "NewAllyGo!2026", "Different!2026"
        ))).isInstanceOfSatisfying(AccountAuthException.class, exception ->
                assertThat(exception.reason()).isEqualTo(AccountAuthException.Reason.PASSWORD_CONFIRM_MISMATCH));
        verify(tokenPort, never()).parseVerificationToken(any());
    }

    private static AccountTokenPort.VerificationTokenClaims claims(long challengeId, VerificationPurpose purpose) {
        return new AccountTokenPort.VerificationTokenClaims(
                challengeId, purpose, VERIFIED_AT, NOW.plusSeconds(570)
        );
    }

    private static AccountAuthStore.PhoneChallenge challenge(long challengeId, VerificationPurpose purpose) {
        return new AccountAuthStore.PhoneChallenge(
                challengeId, PHONE, purpose, VERIFIED_AT, null, NOW.minusSeconds(60)
        );
    }
}
