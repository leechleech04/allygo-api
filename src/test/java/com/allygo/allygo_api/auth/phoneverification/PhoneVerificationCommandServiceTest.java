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
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationCommandServiceTest {
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
        when(transactionOperations.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        PhoneVerificationProperties properties = new PhoneVerificationProperties(
                Duration.ofMinutes(3), Duration.ofMinutes(1), 5, 5,
                ZoneId.of("Asia/Seoul"),
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        );
        service = new PhoneVerificationCommandService(
                challengePort, userPhonePort, smsSenderPort, accessTokenPort,
                properties, codeGenerator, codeHasher, verificationTokenPort, transactionOperations,
                Clock.fixed(NOW, ZoneId.of("UTC"))
        );
        when(codeGenerator.generate()).thenReturn("123456");
        when(codeHasher.hash(PHONE, VerificationPurpose.SIGN_UP, "123456")).thenReturn("hash");
    }

    @Test
    void createsAndSendsSignupChallenge() {
        PhoneVerificationChallenge created = challenge(10L, NOW.plusSeconds(180), NOW, (short) 1);
        when(challengePort.sumSendCount(any(), any(), any(), any())).thenReturn(0);
        when(challengePort.findLatestForUpdate(PHONE, VerificationPurpose.SIGN_UP)).thenReturn(Optional.empty());
        when(challengePort.create(PHONE, VerificationPurpose.SIGN_UP, "hash", NOW.plusSeconds(180), NOW))
                .thenReturn(created);

        var result = service.send(PHONE, "SIGN_UP", null);

        assertThat(result.verificationId()).isEqualTo(10L);
        assertThat(result.expiresIn()).isEqualTo(180);
        assertThat(result.resendAvailableIn()).isEqualTo(60);
        assertThat(result.maskedPhoneNumber()).isEqualTo("+82 10-****-5678");
        verify(smsSenderPort).sendVerificationCode(PHONE, "123456", 180);
    }

    @Test
    void rejectsResendBeforeCooldownWithoutSendingSms() {
        PhoneVerificationChallenge recent = challenge(10L, NOW.plusSeconds(150), NOW.minusSeconds(30), (short) 1);
        when(challengePort.sumSendCount(any(), any(), any(), any())).thenReturn(1);
        when(challengePort.findLatestForUpdate(PHONE, VerificationPurpose.SIGN_UP))
                .thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> service.send(PHONE, "SIGN_UP", null))
                .isInstanceOfSatisfying(PhoneVerificationException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(
                            PhoneVerificationException.Reason.VERIFICATION_RESEND_TOO_EARLY
                    );
                    assertThat(exception.retryAfter()).isEqualTo(30);
                });
        verify(smsSenderPort, never()).sendVerificationCode(any(), any(), anyLong());
    }

    @Test
    void expiresCommittedChallengeWhenSmsProviderFails() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionOperations).executeWithoutResult(any());
        PhoneVerificationChallenge created = challenge(10L, NOW.plusSeconds(180), NOW, (short) 1);
        when(challengePort.sumSendCount(any(), any(), any(), any())).thenReturn(0);
        when(challengePort.findLatestForUpdate(PHONE, VerificationPurpose.SIGN_UP)).thenReturn(Optional.empty());
        when(challengePort.create(any(), any(), any(), any(), any())).thenReturn(created);
        doAnswer(invocation -> { throw PhoneVerificationException.smsSendFailed(); })
                .when(smsSenderPort).sendVerificationCode(any(), any(), anyLong());

        assertThatThrownBy(() -> service.send(PHONE, "SIGN_UP", null))
                .isInstanceOfSatisfying(PhoneVerificationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(PhoneVerificationException.Reason.SMS_SEND_FAILED));
        verify(challengePort).expire(10L, NOW);
    }

    private PhoneVerificationChallenge challenge(Long id, Instant expiresAt, Instant createdAt, short sendCount) {
        return new PhoneVerificationChallenge(
                id, PHONE, VerificationPurpose.SIGN_UP, "hash", (short) 0, sendCount,
                expiresAt, null, null, createdAt
        );
    }
}
