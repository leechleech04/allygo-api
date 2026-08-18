package com.allygo.allygo_api.auth.phoneverification;

import com.allygo.allygo_api.auth.phoneverification.application.PhoneVerificationCommandService;
import com.allygo.allygo_api.auth.phoneverification.application.PhoneVerificationProperties;
import com.allygo.allygo_api.auth.phoneverification.application.VerificationCodeGenerator;
import com.allygo.allygo_api.auth.phoneverification.application.VerificationCodeHasher;
import com.allygo.allygo_api.auth.phoneverification.application.port.AccessTokenPort;
import com.allygo.allygo_api.auth.phoneverification.application.port.PhoneVerificationChallengePort;
import com.allygo.allygo_api.auth.phoneverification.application.port.SmsSenderPort;
import com.allygo.allygo_api.auth.phoneverification.application.port.UserPhonePort;
import com.allygo.allygo_api.auth.phoneverification.application.port.VerificationTokenPort;
import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationChallenge;
import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationException;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationConfirmationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-12T03:00:00Z");
    private static final String PHONE = "+821012345678";

    @Mock PhoneVerificationChallengePort challengePort;
    @Mock UserPhonePort userPhonePort;
    @Mock SmsSenderPort smsSenderPort;
    @Mock AccessTokenPort accessTokenPort;
    @Mock VerificationCodeGenerator codeGenerator;
    @Mock VerificationCodeHasher codeHasher;
    @Mock VerificationTokenPort verificationTokenPort;
    @Mock TransactionOperations transactionOperations;

    private PhoneVerificationCommandService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionOperations.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().when(verificationTokenPort.ttl()).thenReturn(Duration.ofMinutes(10));
        service = new PhoneVerificationCommandService(
                challengePort, userPhonePort, smsSenderPort, accessTokenPort,
                new PhoneVerificationProperties(
                        Duration.ofMinutes(3), Duration.ofMinutes(1), 5, 5,
                        ZoneId.of("Asia/Seoul"),
                        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
                ),
                codeGenerator, codeHasher, verificationTokenPort, transactionOperations,
                Clock.fixed(NOW, ZoneId.of("UTC"))
        );
    }

    @Test
    void confirmsMatchingCodeAndIssuesPurposeBoundToken() {
        PhoneVerificationChallenge challenge = challenge((short) 0, NOW.plusSeconds(180), null, null);
        PhoneVerificationChallenge verified = challenge((short) 0, NOW.plusSeconds(180), NOW, null);
        when(challengePort.findByIdForUpdate(10L)).thenReturn(Optional.of(challenge));
        when(codeHasher.hash(PHONE, VerificationPurpose.SIGN_UP, "123456")).thenReturn("stored-hash");
        when(challengePort.markVerified(10L, NOW)).thenReturn(verified);
        when(verificationTokenPort.issue(10L, VerificationPurpose.SIGN_UP, NOW)).thenReturn("pvt_token");

        var result = service.confirm("10", "123456", null);

        assertThat(result.verificationId()).isEqualTo(10L);
        assertThat(result.verified()).isTrue();
        assertThat(result.verifiedAt()).isEqualTo(NOW);
        assertThat(result.verificationToken()).isEqualTo("pvt_token");
        assertThat(result.tokenExpiresIn()).isEqualTo(600);
        verify(challengePort).markVerified(10L, NOW);
    }

    @Test
    void returnsSameLogicalResultForUnconsumedVerifiedChallengeWithoutExtendingExpiry() {
        Instant verifiedAt = NOW.minusSeconds(60);
        PhoneVerificationChallenge verified = challenge((short) 1, NOW.minusSeconds(1), verifiedAt, null);
        when(challengePort.findByIdForUpdate(10L)).thenReturn(Optional.of(verified));
        when(verificationTokenPort.issue(10L, VerificationPurpose.SIGN_UP, verifiedAt)).thenReturn("pvt_same-token");

        var result = service.confirm("10", "999999", null);

        assertThat(result.verifiedAt()).isEqualTo(verifiedAt);
        assertThat(result.tokenExpiresIn()).isEqualTo(540);
        assertThat(result.verificationToken()).isEqualTo("pvt_same-token");
        verify(challengePort, never()).markVerified(any(), any());
        verify(challengePort, never()).registerMismatch(any(), any(), anyInt());
    }

    @Test
    void incrementsAttemptAndReturnsRemainingAttemptsForMismatch() {
        when(challengePort.findByIdForUpdate(10L)).thenReturn(Optional.of(
                challenge((short) 0, NOW.plusSeconds(180), null, null)
        ));
        when(codeHasher.hash(PHONE, VerificationPurpose.SIGN_UP, "654321")).thenReturn("different-hash");
        when(challengePort.registerMismatch(10L, NOW, 5)).thenReturn(4);

        assertThatThrownBy(() -> service.confirm("10", "654321", null))
                .isInstanceOfSatisfying(PhoneVerificationException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(PhoneVerificationException.Reason.VERIFICATION_CODE_MISMATCH);
                    assertThat(exception.remainingAttempts()).isEqualTo(4);
                });
        verify(challengePort).registerMismatch(10L, NOW, 5);
    }

    @Test
    void persistsFifthMismatchBeforeReturningAttemptLimit() {
        when(challengePort.findByIdForUpdate(10L)).thenReturn(Optional.of(
                challenge((short) 4, NOW.plusSeconds(180), null, null)
        ));
        when(codeHasher.hash(PHONE, VerificationPurpose.SIGN_UP, "654321")).thenReturn("different-hash");
        when(challengePort.registerMismatch(10L, NOW, 5)).thenReturn(0);

        assertThatThrownBy(() -> service.confirm("10", "654321", null))
                .isInstanceOfSatisfying(PhoneVerificationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                PhoneVerificationException.Reason.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED
                        ));
        verify(challengePort).registerMismatch(10L, NOW, 5);
    }

    @Test
    void rejectsMalformedCodeBeforeLoadingOrChangingChallenge() {
        assertThatThrownBy(() -> service.confirm("10", "12A456", null))
                .isInstanceOfSatisfying(PhoneVerificationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                PhoneVerificationException.Reason.INVALID_VERIFICATION_CODE_FORMAT
                        ));
        verify(challengePort, never()).findByIdForUpdate(any());
        verify(challengePort, never()).registerMismatch(any(), any(), anyInt());
    }

    private PhoneVerificationChallenge challenge(
            short attemptCount,
            Instant expiresAt,
            Instant verifiedAt,
            Instant consumedAt
    ) {
        return new PhoneVerificationChallenge(
                10L, PHONE, VerificationPurpose.SIGN_UP, "stored-hash", attemptCount, (short) 1,
                expiresAt, verifiedAt, consumedAt, NOW.minusSeconds(30)
        );
    }
}
