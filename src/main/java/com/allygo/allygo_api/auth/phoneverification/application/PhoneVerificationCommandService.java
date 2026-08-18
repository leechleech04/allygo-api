package com.allygo.allygo_api.auth.phoneverification.application;

import com.allygo.allygo_api.auth.phoneverification.application.port.AccessTokenPort;
import com.allygo.allygo_api.auth.phoneverification.application.port.PhoneVerificationChallengePort;
import com.allygo.allygo_api.auth.phoneverification.application.port.SmsSenderPort;
import com.allygo.allygo_api.auth.phoneverification.application.port.UserPhonePort;
import com.allygo.allygo_api.auth.phoneverification.application.port.VerificationTokenPort;
import com.allygo.allygo_api.auth.phoneverification.application.result.PhoneVerificationConfirmedResult;
import com.allygo.allygo_api.auth.phoneverification.application.result.PhoneVerificationSentResult;
import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationChallenge;
import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationException;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class PhoneVerificationCommandService {
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    private final PhoneVerificationChallengePort challengePort;
    private final UserPhonePort userPhonePort;
    private final SmsSenderPort smsSenderPort;
    private final AccessTokenPort accessTokenPort;
    private final PhoneVerificationProperties properties;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationCodeHasher codeHasher;
    private final VerificationTokenPort verificationTokenPort;
    private final TransactionOperations transactionTemplate;
    private final Clock clock;

    public PhoneVerificationCommandService(
            PhoneVerificationChallengePort challengePort,
            UserPhonePort userPhonePort,
            SmsSenderPort smsSenderPort,
            AccessTokenPort accessTokenPort,
            PhoneVerificationProperties properties,
            VerificationCodeGenerator codeGenerator,
            VerificationCodeHasher codeHasher,
            VerificationTokenPort verificationTokenPort,
            TransactionOperations transactionTemplate,
            Clock clock
    ) {
        this.challengePort = challengePort;
        this.userPhonePort = userPhonePort;
        this.smsSenderPort = smsSenderPort;
        this.accessTokenPort = accessTokenPort;
        this.properties = properties;
        this.codeGenerator = codeGenerator;
        this.codeHasher = codeHasher;
        this.verificationTokenPort = verificationTokenPort;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public PhoneVerificationSentResult send(String phoneNumber, String purposeValue, String authorizationHeader) {
        String phoneE164 = validatePhoneNumber(phoneNumber);
        VerificationPurpose purpose = VerificationPurpose.parse(purposeValue);
        Long authenticatedUserId = purpose.requiresAuthentication()
                ? accessTokenPort.requireUserId(authorizationHeader)
                : null;
        String code = codeGenerator.generate();
        String codeHash = codeHasher.hash(phoneE164, purpose, code);
        Instant now = clock.instant();

        PhoneVerificationChallenge challenge = Objects.requireNonNull(transactionTemplate.execute(status -> {
            challengePort.lockPhoneAndPurpose(phoneE164, purpose);
            validateAccountCondition(phoneE164, purpose, authenticatedUserId);
            return saveChallenge(phoneE164, purpose, codeHash, now);
        }));

        try {
            smsSenderPort.sendVerificationCode(phoneE164, code, properties.codeTtl().toSeconds());
        } catch (PhoneVerificationException exception) {
            expireFailedChallenge(challenge.challengeId());
            throw exception;
        } catch (RuntimeException exception) {
            expireFailedChallenge(challenge.challengeId());
            throw PhoneVerificationException.smsUnavailable();
        }

        return new PhoneVerificationSentResult(
                challenge.challengeId(),
                challenge.expiresAt(),
                secondsCeiling(Duration.between(clock.instant(), challenge.expiresAt())),
                secondsCeiling(Duration.between(
                        clock.instant(),
                        challenge.expiresAt().minus(properties.codeTtl()).plus(properties.resendCooldown())
                )),
                maskPhoneNumber(phoneE164)
        );
    }

    public PhoneVerificationConfirmedResult confirm(
            String verificationIdValue,
            String verificationCode,
            String authorizationHeader
    ) {
        Long verificationId = parseVerificationId(verificationIdValue);
        if (verificationCode == null || !verificationCode.matches("^[0-9]{6}$")) {
            throw PhoneVerificationException.invalidVerificationCodeFormat();
        }

        ConfirmationDecision decision = Objects.requireNonNull(transactionTemplate.execute(status ->
                confirmLocked(verificationId, verificationCode, authorizationHeader, clock.instant())
        ));
        if (decision.attemptLimitExceeded()) {
            throw PhoneVerificationException.attemptLimitExceeded();
        }
        if (decision.remainingAttempts() != null) {
            throw PhoneVerificationException.verificationCodeMismatch(decision.remainingAttempts());
        }

        PhoneVerificationChallenge challenge = decision.challenge();
        Instant tokenExpiresAt = challenge.verifiedAt().plus(verificationTokenPort.ttl());
        return new PhoneVerificationConfirmedResult(
                challenge.challengeId(),
                challenge.phoneE164(),
                challenge.purpose(),
                true,
                challenge.verifiedAt(),
                verificationTokenPort.issue(challenge.challengeId(), challenge.purpose(), challenge.verifiedAt()),
                secondsCeiling(Duration.between(clock.instant(), tokenExpiresAt))
        );
    }

    private ConfirmationDecision confirmLocked(
            Long verificationId,
            String verificationCode,
            String authorizationHeader,
            Instant now
    ) {
        PhoneVerificationChallenge challenge = challengePort.findByIdForUpdate(verificationId)
                .orElseThrow(PhoneVerificationException::verificationNotFound);
        if (challenge.consumedAt() != null) {
            throw PhoneVerificationException.alreadyConsumed();
        }
        if (challenge.verifiedAt() != null) {
            if (!now.isBefore(challenge.verifiedAt().plus(verificationTokenPort.ttl()))) {
                throw PhoneVerificationException.resultExpired();
            }
            validateConfirmAccountCondition(challenge, authorizationHeader);
            return ConfirmationDecision.success(challenge);
        }
        if (challenge.attemptCount() >= properties.maxAttempts()) {
            throw PhoneVerificationException.attemptLimitExceeded();
        }
        if (!now.isBefore(challenge.expiresAt())) {
            throw PhoneVerificationException.codeExpired();
        }
        validateConfirmAccountCondition(challenge, authorizationHeader);

        String candidateHash = codeHasher.hash(challenge.phoneE164(), challenge.purpose(), verificationCode);
        if (!MessageDigest.isEqual(
                challenge.codeHash().getBytes(StandardCharsets.UTF_8),
                candidateHash.getBytes(StandardCharsets.UTF_8)
        )) {
            int remainingAttempts = challengePort.registerMismatch(
                    challenge.challengeId(), now, properties.maxAttempts()
            );
            return remainingAttempts == 0
                    ? ConfirmationDecision.limitExceeded()
                    : ConfirmationDecision.mismatch(remainingAttempts);
        }
        return ConfirmationDecision.success(challengePort.markVerified(challenge.challengeId(), now));
    }

    private void validateConfirmAccountCondition(
            PhoneVerificationChallenge challenge,
            String authorizationHeader
    ) {
        Long authenticatedUserId = challenge.purpose().requiresAuthentication()
                ? accessTokenPort.requireUserId(authorizationHeader)
                : null;
        validateAccountCondition(challenge.phoneE164(), challenge.purpose(), authenticatedUserId);
    }

    private static Long parseVerificationId(String verificationIdValue) {
        try {
            long verificationId = Long.parseLong(verificationIdValue);
            if (verificationId <= 0) {
                throw PhoneVerificationException.invalidVerificationId();
            }
            return verificationId;
        } catch (NumberFormatException exception) {
            throw PhoneVerificationException.invalidVerificationId();
        }
    }

    private PhoneVerificationChallenge saveChallenge(
            String phoneE164,
            VerificationPurpose purpose,
            String codeHash,
            Instant now
    ) {
        ZonedDateTime zonedNow = now.atZone(properties.dailyLimitZone());
        Instant dayStart = zonedNow.toLocalDate().atStartOfDay(properties.dailyLimitZone()).toInstant();
        Instant nextDayStart = zonedNow.toLocalDate().plusDays(1).atStartOfDay(properties.dailyLimitZone()).toInstant();
        int dailySends = challengePort.sumSendCount(phoneE164, purpose, dayStart, nextDayStart);
        if (dailySends >= properties.maxDailySends()) {
            throw PhoneVerificationException.dailyLimitExceeded(secondsCeiling(Duration.between(now, nextDayStart)));
        }

        PhoneVerificationChallenge latest = challengePort.findLatestForUpdate(phoneE164, purpose).orElse(null);
        Instant expiresAt = now.plus(properties.codeTtl());
        if (latest != null && latest.canBeResent() && !latest.createdAt().isBefore(dayStart)) {
            Instant lastSentAt = latest.expiresAt().minus(properties.codeTtl());
            Instant resendAt = lastSentAt.plus(properties.resendCooldown());
            if (now.isBefore(resendAt)) {
                throw PhoneVerificationException.resendTooEarly(secondsCeiling(Duration.between(now, resendAt)));
            }
            return challengePort.resend(latest.challengeId(), codeHash, expiresAt);
        }
        return challengePort.create(phoneE164, purpose, codeHash, expiresAt, now);
    }

    private void validateAccountCondition(String phoneE164, VerificationPurpose purpose, Long authenticatedUserId) {
        boolean registered = userPhonePort.existsByPhoneNumber(phoneE164);
        if (purpose == VerificationPurpose.SIGN_UP && registered) {
            throw PhoneVerificationException.phoneNumberAlreadyRegistered();
        }
        if (purpose.requiresRegisteredPhone() && !registered) {
            throw PhoneVerificationException.phoneNumberNotRegistered();
        }
        if (purpose == VerificationPurpose.WITHDRAW_ACCOUNT) {
            String authenticatedPhone = userPhonePort.findPhoneNumberByUserId(authenticatedUserId)
                    .orElseThrow(PhoneVerificationException::unauthorized);
            if (!authenticatedPhone.equals(phoneE164)) {
                throw PhoneVerificationException.phoneNumberMismatch();
            }
        }
    }

    private void expireFailedChallenge(Long challengeId) {
        try {
            transactionTemplate.executeWithoutResult(status -> challengePort.expire(challengeId, clock.instant()));
        } catch (RuntimeException ignored) {
            // Preserve the SMS failure response; an already committed unusable code is still unknown to the caller.
        }
    }

    private static String validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() > 20 || !E164_PATTERN.matcher(phoneNumber).matches()) {
            throw PhoneVerificationException.invalidPhoneNumber();
        }
        try {
            PhoneNumber parsed = PHONE_NUMBER_UTIL.parse(phoneNumber, "ZZ");
            PhoneNumberUtil.PhoneNumberType type = PHONE_NUMBER_UTIL.getNumberType(parsed);
            boolean mobile = type == PhoneNumberUtil.PhoneNumberType.MOBILE
                    || type == PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE;
            String normalized = PHONE_NUMBER_UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            if (!mobile || !PHONE_NUMBER_UTIL.isValidNumber(parsed) || !normalized.equals(phoneNumber)) {
                throw PhoneVerificationException.invalidPhoneNumber();
            }
            return normalized;
        } catch (NumberParseException exception) {
            throw PhoneVerificationException.invalidPhoneNumber();
        }
    }

    private static int secondsCeiling(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return 0;
        }
        long seconds = duration.toSeconds();
        if (duration.minusSeconds(seconds).isZero()) {
            return Math.toIntExact(seconds);
        }
        return Math.toIntExact(seconds + 1);
    }

    private static String maskPhoneNumber(String phoneE164) {
        if (phoneE164.matches("^\\+82\\d{10}$")) {
            return "+82 " + phoneE164.substring(3, 5) + "-****-" + phoneE164.substring(9);
        }
        int visiblePrefix = Math.min(4, phoneE164.length() - 4);
        return phoneE164.substring(0, visiblePrefix) + "****" + phoneE164.substring(phoneE164.length() - 4);
    }

    private record ConfirmationDecision(
            PhoneVerificationChallenge challenge,
            Integer remainingAttempts,
            boolean attemptLimitExceeded
    ) {
        static ConfirmationDecision success(PhoneVerificationChallenge challenge) {
            return new ConfirmationDecision(challenge, null, false);
        }

        static ConfirmationDecision mismatch(int remainingAttempts) {
            return new ConfirmationDecision(null, remainingAttempts, false);
        }

        static ConfirmationDecision limitExceeded() {
            return new ConfirmationDecision(null, null, true);
        }
    }
}
