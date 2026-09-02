package com.allygo.allygo_api.auth.account.application;

import com.allygo.allygo_api.auth.account.application.command.LoginCommand;
import com.allygo.allygo_api.auth.account.application.command.FindLoginIdCommand;
import com.allygo.allygo_api.auth.account.application.command.PasswordResetCommand;
import com.allygo.allygo_api.auth.account.application.command.PolicyAgreementCommand;
import com.allygo.allygo_api.auth.account.application.command.SignUpCommand;
import com.allygo.allygo_api.auth.account.application.port.AccountAuthStore;
import com.allygo.allygo_api.auth.account.application.port.AccountTokenPort;
import com.allygo.allygo_api.auth.account.application.port.LoginAttemptPort;
import com.allygo.allygo_api.auth.account.application.port.ProfileImageUrlPort;
import com.allygo.allygo_api.auth.account.application.result.AuthTokenResult;
import com.allygo.allygo_api.auth.account.application.result.LoginResult;
import com.allygo.allygo_api.auth.account.application.result.LoginIdLookupResult;
import com.allygo.allygo_api.auth.account.application.result.NotificationPreferenceResult;
import com.allygo.allygo_api.auth.account.application.result.SignUpResult;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException.Reason;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AccountAuthCommandService {
    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9_]{4,50}$");
    private static final Pattern SIGN_UP_PASSWORD = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9])\\S{8,64}$"
    );
    private static final Set<String> COUNTRY_CODES = Set.copyOf(Arrays.asList(Locale.getISOCountries()));
    private static final Map<String, String> COUNTRY_LANGUAGE = Map.ofEntries(
            Map.entry("KR", "ko"), Map.entry("JP", "ja"), Map.entry("CN", "zh-CN"),
            Map.entry("TW", "zh-TW"), Map.entry("HK", "zh-HK"), Map.entry("TH", "th"),
            Map.entry("VN", "vi"), Map.entry("ID", "id"), Map.entry("MY", "ms"),
            Map.entry("PH", "en"), Map.entry("SG", "en"), Map.entry("US", "en"),
            Map.entry("GB", "en"), Map.entry("CA", "en"), Map.entry("AU", "en"),
            Map.entry("NZ", "en"), Map.entry("IN", "en"), Map.entry("DE", "de"),
            Map.entry("FR", "fr"), Map.entry("ES", "es"), Map.entry("IT", "it"),
            Map.entry("PT", "pt"), Map.entry("BR", "pt-BR"), Map.entry("RU", "ru")
    );
    private static final List<String> NOTIFICATION_CATEGORIES = List.of(
            "MATCHING", "CHAT", "CALL", "REVIEW", "REPORT", "VERIFICATION", "SANCTION"
    );

    private final AccountAuthStore store;
    private final AccountTokenPort tokenPort;
    private final LoginAttemptPort loginAttemptPort;
    private final ProfileImageUrlPort profileImageUrlPort;
    private final PasswordEncoder passwordEncoder;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AccountAuthCommandService(
            AccountAuthStore store,
            AccountTokenPort tokenPort,
            LoginAttemptPort loginAttemptPort,
            ProfileImageUrlPort profileImageUrlPort,
            PasswordEncoder passwordEncoder,
            TransactionOperations transactions,
            Clock clock
    ) {
        this.store = store;
        this.tokenPort = tokenPort;
        this.loginAttemptPort = loginAttemptPort;
        this.profileImageUrlPort = profileImageUrlPort;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("AllyGo!dummy-password-2026");
    }

    public SignUpResult signUp(SignUpCommand rawCommand) {
        ValidatedSignUp command = validateSignUp(rawCommand);
        AccountTokenPort.VerificationTokenClaims tokenClaims = tokenPort.parseVerificationToken(
                command.verificationToken()
        );
        Instant now = clock.instant();
        if (!now.isBefore(tokenClaims.expiresAt())) {
            throw error(Reason.VERIFICATION_TOKEN_EXPIRED, "휴대폰 인증 토큰이 만료되었습니다.");
        }
        if (tokenClaims.purpose() != VerificationPurpose.SIGN_UP) {
            throw error(Reason.VERIFICATION_TOKEN_PURPOSE_MISMATCH, "회원가입용 휴대폰 인증 토큰이 아닙니다.");
        }

        return Objects.requireNonNull(transactions.execute(status -> signUpLocked(command, tokenClaims, now)));
    }

    public LoginResult login(LoginCommand rawCommand) {
        ValidatedLogin command = validateLogin(rawCommand);
        int blockedFor = loginAttemptPort.blockedForSeconds(command.loginId(), command.ipAddress());
        if (blockedFor > 0) {
            throw AccountAuthException.tooManyAttempts(blockedFor);
        }

        AccountAuthStore.LoginAccount account = store.findLoginAccount(command.loginId()).orElse(null);
        String storedHash = account == null ? dummyPasswordHash : account.passwordHash();
        if (!passwordEncoder.matches(command.password(), storedHash) || account == null) {
            int retryAfter = loginAttemptPort.recordFailure(command.loginId(), command.ipAddress());
            if (retryAfter > 0) {
                throw AccountAuthException.tooManyAttempts(retryAfter);
            }
            throw error(Reason.INVALID_LOGIN_CREDENTIALS, "아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        validateAccountStatus(account.accountStatus());
        Instant now = clock.instant();
        LoginResult result = Objects.requireNonNull(transactions.execute(status -> loginLocked(account, command, now)));
        loginAttemptPort.clear(command.loginId(), command.ipAddress());
        return result;
    }

    public LoginIdLookupResult findLoginId(FindLoginIdCommand rawCommand) {
        String verificationToken = validateLoginIdLookup(rawCommand);
        AccountTokenPort.VerificationTokenClaims tokenClaims = parseRecoveryToken(
                verificationToken, VerificationPurpose.FIND_LOGIN_ID,
                "아이디 찾기용 휴대폰 인증 토큰이 아닙니다."
        );
        Instant now = clock.instant();
        return Objects.requireNonNull(transactions.execute(status -> findLoginIdLocked(tokenClaims, now)));
    }

    public void resetPassword(PasswordResetCommand rawCommand) {
        ValidatedPasswordReset command = validatePasswordReset(rawCommand);
        AccountTokenPort.VerificationTokenClaims tokenClaims = parseRecoveryToken(
                command.verificationToken(), VerificationPurpose.RESET_PASSWORD,
                "비밀번호 재설정용 휴대폰 인증 토큰이 아닙니다."
        );
        Instant now = clock.instant();
        Objects.requireNonNull(transactions.execute(status -> {
            resetPasswordLocked(command, tokenClaims, now);
            return Boolean.TRUE;
        }));
    }

    private LoginIdLookupResult findLoginIdLocked(
            AccountTokenPort.VerificationTokenClaims tokenClaims,
            Instant now
    ) {
        AccountAuthStore.PhoneChallenge preliminary = store.findPhoneChallenge(tokenClaims.challengeId())
                .orElseThrow(() -> error(Reason.PHONE_VERIFICATION_NOT_FOUND, "휴대폰 인증 요청을 찾을 수 없습니다."));
        validateRecoveryChallenge(preliminary, tokenClaims, VerificationPurpose.FIND_LOGIN_ID);
        AccountAuthStore.RecoveryAccount initialAccount = store.findRecoveryAccountByPhone(preliminary.phoneE164())
                .orElseThrow(() -> error(Reason.PHONE_NUMBER_NOT_REGISTERED, "가입된 휴대폰 번호를 찾을 수 없습니다."));
        store.lockAccountLifecycle(initialAccount.userId());
        AccountAuthStore.RecoveryAccount account = store.lockRecoveryAccountByPhone(preliminary.phoneE164())
                .orElseThrow(() -> error(Reason.PHONE_NUMBER_NOT_REGISTERED, "가입된 휴대폰 번호를 찾을 수 없습니다."));
        validateRecoveryAccountStatus(account.accountStatus());
        AccountAuthStore.PhoneChallenge challenge = store.lockPhoneChallenge(tokenClaims.challengeId())
                .orElseThrow(() -> error(Reason.PHONE_VERIFICATION_NOT_FOUND, "휴대폰 인증 요청을 찾을 수 없습니다."));
        validateRecoveryChallenge(challenge, tokenClaims, VerificationPurpose.FIND_LOGIN_ID);
        String maskedLoginId = maskLoginId(account.loginId());
        store.consumePhoneChallenge(challenge.challengeId(), now);
        return new LoginIdLookupResult(maskedLoginId);
    }

    private void resetPasswordLocked(
            ValidatedPasswordReset command,
            AccountTokenPort.VerificationTokenClaims tokenClaims,
            Instant now
    ) {
        AccountAuthStore.PhoneChallenge preliminary = store.findPhoneChallenge(tokenClaims.challengeId())
                .orElseThrow(() -> error(Reason.PHONE_VERIFICATION_NOT_FOUND, "휴대폰 인증 요청을 찾을 수 없습니다."));
        if (preliminary.purpose() != VerificationPurpose.RESET_PASSWORD) {
            throw error(Reason.VERIFICATION_TOKEN_PURPOSE_MISMATCH, "비밀번호 재설정용 휴대폰 인증 결과가 아닙니다.");
        }
        validateRecoveryChallenge(preliminary, tokenClaims, VerificationPurpose.RESET_PASSWORD);

        AccountAuthStore.RecoveryAccount initialAccount = store.findRecoveryAccountByPhone(preliminary.phoneE164())
                .orElseThrow(() -> error(Reason.PHONE_NUMBER_NOT_REGISTERED, "가입된 휴대폰 번호를 찾을 수 없습니다."));
        store.lockAccountLifecycle(initialAccount.userId());
        AccountAuthStore.RecoveryAccount account = store.lockRecoveryAccountByPhone(preliminary.phoneE164())
                .orElseThrow(() -> error(Reason.PHONE_NUMBER_NOT_REGISTERED, "가입된 휴대폰 번호를 찾을 수 없습니다."));
        validateRecoveryAccountStatus(account.accountStatus());

        AccountAuthStore.PhoneChallenge challenge = store.lockPhoneChallenges(
                        preliminary.phoneE164(), VerificationPurpose.RESET_PASSWORD
                ).stream()
                .filter(candidate -> candidate.challengeId() == tokenClaims.challengeId())
                .findFirst()
                .orElseThrow(() -> error(Reason.PHONE_VERIFICATION_NOT_FOUND, "휴대폰 인증 요청을 찾을 수 없습니다."));
        validateRecoveryChallenge(challenge, tokenClaims, VerificationPurpose.RESET_PASSWORD);
        store.completePasswordReset(
                account.userId(), challenge.phoneE164(), passwordEncoder.encode(command.newPassword()), now
        );
    }

    private AccountTokenPort.VerificationTokenClaims parseRecoveryToken(
            String verificationToken,
            VerificationPurpose expectedPurpose,
            String mismatchMessage
    ) {
        AccountTokenPort.VerificationTokenClaims tokenClaims = tokenPort.parseVerificationToken(verificationToken);
        if (!clock.instant().isBefore(tokenClaims.expiresAt())) {
            throw error(Reason.VERIFICATION_TOKEN_EXPIRED, "휴대폰 인증 토큰이 만료되었습니다.");
        }
        if (tokenClaims.purpose() != expectedPurpose) {
            throw error(Reason.VERIFICATION_TOKEN_PURPOSE_MISMATCH, mismatchMessage);
        }
        return tokenClaims;
    }

    private static void validateRecoveryChallenge(
            AccountAuthStore.PhoneChallenge challenge,
            AccountTokenPort.VerificationTokenClaims tokenClaims,
            VerificationPurpose expectedPurpose
    ) {
        if (challenge.purpose() != expectedPurpose) {
            throw error(Reason.VERIFICATION_TOKEN_PURPOSE_MISMATCH, "휴대폰 인증 목적이 일치하지 않습니다.");
        }
        if (challenge.verifiedAt() == null || !challenge.verifiedAt().truncatedTo(ChronoUnit.SECONDS)
                .equals(tokenClaims.issuedAt().truncatedTo(ChronoUnit.SECONDS))) {
            throw error(Reason.INVALID_VERIFICATION_TOKEN, "유효한 휴대폰 인증 결과와 연결되지 않은 토큰입니다.");
        }
        if (challenge.consumedAt() != null) {
            throw error(Reason.PHONE_VERIFICATION_ALREADY_CONSUMED, "이미 사용된 휴대폰 인증 결과입니다.");
        }
    }

    private static void validateRecoveryAccountStatus(String accountStatus) {
        switch (accountStatus) {
            case "ACTIVE", "SUSPENDED", "BANNED" -> { }
            case "WITHDRAWN" -> throw error(Reason.ACCOUNT_WITHDRAWN, "탈퇴한 계정입니다.");
            default -> throw new IllegalStateException("Unknown account status: " + accountStatus);
        }
    }

    private static String maskLoginId(String loginId) {
        if (loginId == null || loginId.length() < 4) {
            throw new IllegalStateException("Stored login ID does not satisfy the account schema");
        }
        int visiblePrefix = loginId.length() == 4 ? 1 : 2;
        int visibleSuffix = loginId.length() == 4 ? 1 : 2;
        return loginId.substring(0, visiblePrefix)
                + "*".repeat(loginId.length() - visiblePrefix - visibleSuffix)
                + loginId.substring(loginId.length() - visibleSuffix);
    }

    private SignUpResult signUpLocked(
            ValidatedSignUp command,
            AccountTokenPort.VerificationTokenClaims tokenClaims,
            Instant now
    ) {
        AccountAuthStore.PhoneChallenge challenge = store.lockPhoneChallenge(tokenClaims.challengeId())
                .orElseThrow(() -> error(Reason.PHONE_VERIFICATION_NOT_FOUND, "휴대폰 인증 요청을 찾을 수 없습니다."));
        if (challenge.purpose() != VerificationPurpose.SIGN_UP) {
            throw error(Reason.VERIFICATION_TOKEN_PURPOSE_MISMATCH, "회원가입용 휴대폰 인증 결과가 아닙니다.");
        }
        if (challenge.verifiedAt() == null || !challenge.verifiedAt().truncatedTo(ChronoUnit.SECONDS)
                .equals(tokenClaims.issuedAt().truncatedTo(ChronoUnit.SECONDS))) {
            throw error(Reason.INVALID_VERIFICATION_TOKEN, "유효한 휴대폰 인증 결과와 연결되지 않은 토큰입니다.");
        }
        if (challenge.consumedAt() != null) {
            throw error(Reason.PHONE_VERIFICATION_ALREADY_CONSUMED, "이미 사용된 휴대폰 인증 결과입니다.");
        }
        if (store.existsByPhone(challenge.phoneE164())) {
            throw error(Reason.PHONE_NUMBER_ALREADY_REGISTERED, "이미 가입된 휴대폰 번호입니다.");
        }
        if (store.existsByLoginId(command.loginId())) {
            throw error(Reason.LOGIN_ID_ALREADY_EXISTS, "이미 사용 중인 로그인 아이디입니다.");
        }
        if (store.existsByNickname(command.nickname())) {
            throw error(Reason.NICKNAME_ALREADY_EXISTS, "이미 사용 중인 닉네임입니다.");
        }

        String preferredLanguage = COUNTRY_LANGUAGE.getOrDefault(command.nationalityCode(), "en");
        String defaultLanguage = store.resolveActiveLanguage(preferredLanguage);
        validatePolicies(command.policyAgreements(), defaultLanguage, now);

        List<AccountAuthStore.PolicyAgreement> storedAgreements = command.policyAgreements().stream()
                .map(item -> new AccountAuthStore.PolicyAgreement(item.policyDocumentId(), item.agreed()))
                .toList();
        AccountAuthStore.CreatedAccount created = store.createAccount(new AccountAuthStore.NewAccount(
                command.loginId(), challenge.phoneE164(), passwordEncoder.encode(command.password()),
                command.name(), command.nickname(), command.nationalityCode(), defaultLanguage,
                challenge.verifiedAt(), challenge.challengeId(), storedAgreements,
                command.ipAddress(), command.userAgent(), now
        ));
        AccountTokenPort.IssuedTokens tokens = tokenPort.issue(created.userId(), now);
        store.saveRefreshToken(created.userId(), storedRefreshToken(tokens, command.deviceId(), now));
        store.consumePhoneChallenge(challenge.challengeId(), now);

        return new SignUpResult(
                created.userId(), created.travelerProfileId(), created.phoneVerificationId(),
                command.loginId(), challenge.phoneE164(), command.name(), command.nickname(),
                command.nationalityCode(), defaultLanguage, "ACTIVE", "TRAVELER", false,
                new SignUpResult.SettingsResult("TRAVELER", false, "UTC"),
                defaultNotificationPreferences(), created.createdAt(), tokenResult(tokens)
        );
    }

    private LoginResult loginLocked(
            AccountAuthStore.LoginAccount account,
            ValidatedLogin command,
            Instant now
    ) {
        account = store.lockLoginAccount(account.userId())
                .orElseThrow(() -> error(Reason.INVALID_LOGIN_CREDENTIALS, "아이디 또는 비밀번호가 일치하지 않습니다."));
        if (!passwordEncoder.matches(command.password(), account.passwordHash())) {
            throw error(Reason.INVALID_LOGIN_CREDENTIALS, "아이디 또는 비밀번호가 일치하지 않습니다.");
        }
        validateAccountStatus(account.accountStatus());
        AccountAuthStore.LoginRestriction restriction = store.findLoginRestriction(account.userId(), now);
        if (restriction.restricted()) {
            throw AccountAuthException.loginRestricted(restriction.endsAt());
        }

        boolean helperApproved = "APPROVED".equals(account.helperApprovalStatus());
        String currentUiMode = account.currentUiMode();
        if ("HELPER".equals(currentUiMode)) {
            boolean helperAllowed = helperApproved
                    && store.hasRequiredHelperVerification(account.userId(), now)
                    && !store.hasHelperActivityRestriction(account.userId(), now);
            if (!helperAllowed) {
                currentUiMode = "TRAVELER";
                store.changeCurrentUiMode(account.userId(), currentUiMode, now);
            }
        }

        AccountTokenPort.IssuedTokens tokens = tokenPort.issue(account.userId(), now);
        store.completeLogin(account.userId(), storedRefreshToken(tokens, command.deviceId(), now), now);
        ProfileImageUrlPort.SignedImageUrl image = account.profileImageStorageKey() == null
                ? null
                : profileImageUrlPort.sign(account.profileImageStorageKey(), now);

        return new LoginResult(
                account.userId(), account.travelerProfileId(), account.helperProfileId(), account.loginId(),
                account.name(), account.nickname(), account.phoneNumber(), account.nationalityCode(),
                account.defaultLanguageCode(), account.accountStatus(), helperApproved ? "BOTH" : "TRAVELER",
                account.helperApprovalStatus(), account.helperAvailabilityStatus(), currentUiMode,
                account.onboardingCompleted(), image == null ? null : image.url(),
                image == null ? null : image.expiresAt(), now, tokenResult(tokens)
        );
    }

    private void validatePolicies(List<ValidatedPolicyAgreement> agreements, String languageCode, Instant now) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ValidatedPolicyAgreement agreement : agreements) {
            ids.add(agreement.policyDocumentId());
        }
        List<AccountAuthStore.PolicyDocument> documents = store.lockPolicyDocuments(ids);
        if (documents.size() != ids.size()) {
            throw error(Reason.POLICY_DOCUMENT_NOT_FOUND, "정책 문서를 찾을 수 없습니다.");
        }
        for (AccountAuthStore.PolicyDocument document : documents) {
            if (!document.isEffectiveAt(now)) {
                throw error(Reason.POLICY_DOCUMENT_NOT_EFFECTIVE, "현재 유효하지 않은 정책 문서입니다.");
            }
        }
        Set<Long> required = store.findEffectiveRequiredPolicyIds(languageCode, now);
        Set<Long> agreed = new HashSet<>();
        for (ValidatedPolicyAgreement agreement : agreements) {
            if (agreement.agreed()) {
                agreed.add(agreement.policyDocumentId());
            }
        }
        if (required.isEmpty() || !agreed.containsAll(required)) {
            throw error(Reason.REQUIRED_POLICY_NOT_AGREED, "현재 유효한 필수 정책에 모두 동의해야 합니다.");
        }
    }

    private static ValidatedSignUp validateSignUp(SignUpCommand command) {
        if (command == null || isBlank(command.verificationToken())) {
            throw error(Reason.INVALID_SIGN_UP_REQUEST, "필수 회원가입 정보를 입력해 주세요.");
        }
        String loginId = normalizeLoginId(command.loginId());
        validateLoginId(loginId);
        if (command.password() == null || !SIGN_UP_PASSWORD.matcher(command.password()).matches()) {
            throw error(Reason.INVALID_PASSWORD_FORMAT, "비밀번호 형식이 올바르지 않습니다.");
        }
        if (!Objects.equals(command.password(), command.passwordConfirm())) {
            throw error(Reason.PASSWORD_CONFIRM_MISMATCH, "비밀번호 확인이 일치하지 않습니다.");
        }
        String name = trim(command.name());
        if (name == null || name.isEmpty() || name.length() > 100 || containsControl(name)) {
            throw error(Reason.INVALID_NAME_FORMAT, "이름 형식이 올바르지 않습니다.");
        }
        String nickname = trim(command.nickname());
        if (nickname == null || nickname.length() < 2 || nickname.length() > 30
                || containsControl(nickname) || nickname.matches(".*\\s{2,}.*")) {
            throw error(Reason.INVALID_NICKNAME_FORMAT, "닉네임 형식이 올바르지 않습니다.");
        }
        String nationality = trim(command.nationalityCode());
        nationality = nationality == null ? null : nationality.toUpperCase(Locale.ROOT);
        if (nationality == null || !COUNTRY_CODES.contains(nationality)) {
            throw error(Reason.INVALID_NATIONALITY_CODE, "유효한 ISO 국가 코드를 입력해 주세요.");
        }
        List<ValidatedPolicyAgreement> policies = validatePolicyAgreements(command.policyAgreements());
        String deviceId = validateDeviceId(command.deviceId());
        return new ValidatedSignUp(
                command.verificationToken(), loginId, command.password(), name, nickname, nationality,
                policies, deviceId, sanitizeIp(command.ipAddress()), truncate(command.userAgent(), 500)
        );
    }

    private static ValidatedLogin validateLogin(LoginCommand command) {
        if (command == null) {
            throw error(Reason.INVALID_LOGIN_REQUEST, "로그인 정보를 입력해 주세요.");
        }
        String loginId = normalizeLoginId(command.loginId());
        validateLoginId(loginId);
        if (command.password() == null || command.password().length() < 8 || command.password().length() > 64
                || command.password().isBlank()) {
            throw error(Reason.INVALID_PASSWORD_FORMAT, "비밀번호 형식이 올바르지 않습니다.");
        }
        return new ValidatedLogin(
                loginId, command.password(), validateDeviceId(command.deviceId()), sanitizeIp(command.ipAddress())
        );
    }

    private static String validateLoginIdLookup(FindLoginIdCommand command) {
        if (command == null || invalidVerificationTokenRequest(command.verificationToken())) {
            throw error(Reason.INVALID_LOGIN_ID_LOOKUP_REQUEST, "아이디 찾기 요청 형식이 올바르지 않습니다.");
        }
        return command.verificationToken();
    }

    private static ValidatedPasswordReset validatePasswordReset(PasswordResetCommand command) {
        if (command == null || invalidVerificationTokenRequest(command.verificationToken())
                || command.newPassword() == null || command.newPassword().isEmpty()
                || command.newPasswordConfirm() == null || command.newPasswordConfirm().isEmpty()) {
            throw error(Reason.INVALID_PASSWORD_RESET_REQUEST, "비밀번호 재설정 요청 형식이 올바르지 않습니다.");
        }
        if (!SIGN_UP_PASSWORD.matcher(command.newPassword()).matches()) {
            throw error(Reason.INVALID_PASSWORD_FORMAT, "비밀번호 형식이 올바르지 않습니다.");
        }
        if (!Objects.equals(command.newPassword(), command.newPasswordConfirm())) {
            throw error(Reason.PASSWORD_CONFIRM_MISMATCH, "비밀번호 확인이 일치하지 않습니다.");
        }
        return new ValidatedPasswordReset(
                command.verificationToken(), command.newPassword()
        );
    }

    private static boolean invalidVerificationTokenRequest(String verificationToken) {
        return verificationToken == null || verificationToken.isBlank() || verificationToken.length() > 2048;
    }

    private static List<ValidatedPolicyAgreement> validatePolicyAgreements(List<PolicyAgreementCommand> input) {
        if (input == null || input.isEmpty()) {
            throw error(Reason.INVALID_POLICY_AGREEMENTS, "정책 동의 목록은 비어 있을 수 없습니다.");
        }
        List<ValidatedPolicyAgreement> result = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (PolicyAgreementCommand item : input) {
            if (item == null || item.policyDocumentId() == null || item.policyDocumentId() <= 0
                    || item.agreed() == null || !ids.add(item.policyDocumentId())) {
                throw error(Reason.INVALID_POLICY_AGREEMENTS, "정책 동의 목록이 올바르지 않습니다.");
            }
            result.add(new ValidatedPolicyAgreement(item.policyDocumentId(), item.agreed()));
        }
        return List.copyOf(result);
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID.matcher(loginId).matches()) {
            throw error(Reason.INVALID_LOGIN_ID_FORMAT, "로그인 아이디 형식이 올바르지 않습니다.");
        }
    }

    private static String validateDeviceId(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        if (deviceId.isBlank() || deviceId.length() > 255) {
            throw error(Reason.INVALID_DEVICE_ID, "기기 식별자 형식이 올바르지 않습니다.");
        }
        return deviceId;
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

    private static AccountAuthStore.StoredRefreshToken storedRefreshToken(
            AccountTokenPort.IssuedTokens tokens,
            String deviceId,
            Instant now
    ) {
        return new AccountAuthStore.StoredRefreshToken(
                tokens.tokenFamilyId(), tokens.refreshTokenHash(), deviceId,
                now.plus(tokens.refreshTokenTtl()), now
        );
    }

    private static AuthTokenResult tokenResult(AccountTokenPort.IssuedTokens tokens) {
        return new AuthTokenResult(
                tokens.accessToken(), tokens.refreshToken(),
                Math.toIntExact(tokens.accessTokenTtl().toSeconds()),
                Math.toIntExact(tokens.refreshTokenTtl().toSeconds())
        );
    }

    private static List<NotificationPreferenceResult> defaultNotificationPreferences() {
        return NOTIFICATION_CATEGORIES.stream()
                .map(category -> new NotificationPreferenceResult(category, true, true))
                .toList();
    }

    private static String normalizeLoginId(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static String sanitizeIp(String ipAddress) {
        return isBlank(ipAddress) ? null : truncate(ipAddress.trim(), 45);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static AccountAuthException error(Reason reason, String message) {
        return AccountAuthException.of(reason, message);
    }

    private record ValidatedSignUp(
            String verificationToken,
            String loginId,
            String password,
            String name,
            String nickname,
            String nationalityCode,
            List<ValidatedPolicyAgreement> policyAgreements,
            String deviceId,
            String ipAddress,
            String userAgent
    ) {
    }

    private record ValidatedPolicyAgreement(long policyDocumentId, boolean agreed) {
    }

    private record ValidatedLogin(String loginId, String password, String deviceId, String ipAddress) {
    }

    private record ValidatedPasswordReset(String verificationToken, String newPassword) {
    }

}
