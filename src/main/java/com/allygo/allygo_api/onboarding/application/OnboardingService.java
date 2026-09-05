package com.allygo.allygo_api.onboarding.application;

import com.allygo.allygo_api.auth.account.application.port.AccountTokenPort;
import com.allygo.allygo_api.onboarding.application.command.SaveInitialSettingsCommand;
import com.allygo.allygo_api.onboarding.application.command.SavePermissionStatusCommand;
import com.allygo.allygo_api.onboarding.application.port.OnboardingStore;
import com.allygo.allygo_api.onboarding.application.result.InitialSettingsResult;
import com.allygo.allygo_api.onboarding.application.result.OnboardingCompletionResult;
import com.allygo.allygo_api.onboarding.application.result.PermissionStatusResult;
import com.allygo.allygo_api.onboarding.domain.OnboardingException;
import com.allygo.allygo_api.onboarding.domain.OnboardingException.Reason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class OnboardingService {
    public static final Set<String> PERMISSION_STATUSES = Set.of(
            "NOT_DETERMINED", "GRANTED", "DENIED", "RESTRICTED"
    );
    public static final List<String> NOTIFICATION_CATEGORIES = List.of(
            "MATCHING", "CHAT", "CALL", "REVIEW", "REPORT", "VERIFICATION", "SANCTION"
    );
    private static final Set<String> NATIONALITY_CODES = Set.of(Locale.getISOCountries());

    private final OnboardingStore store;
    private final AccountTokenPort tokenPort;
    private final TransactionOperations transactions;
    private final Clock clock;

    public OnboardingService(
            OnboardingStore store,
            AccountTokenPort tokenPort,
            TransactionOperations transactions,
            Clock clock
    ) {
        this.store = store;
        this.tokenPort = tokenPort;
        this.transactions = transactions;
        this.clock = clock;
    }

    public InitialSettingsResult getInitialSettings(String authorizationHeader) {
        long userId = tokenPort.requireAccessUserId(authorizationHeader);
        Instant now = clock.instant();
        OnboardingStore.OnboardingState state = store.find(userId, now)
                .orElseThrow(() -> error(Reason.USER_NOT_FOUND, "사용자 계정을 찾을 수 없습니다."));
        validateAccessible(state);
        validateIntegrity(state);
        return toInitialSettings(state);
    }

    public InitialSettingsResult saveInitialSettings(
            String authorizationHeader,
            SaveInitialSettingsCommand rawCommand
    ) {
        ValidatedSettings command = validate(rawCommand);
        long userId = tokenPort.requireAccessUserId(authorizationHeader);
        Instant now = clock.instant();
        return Objects.requireNonNull(transactions.execute(status -> {
            OnboardingStore.OnboardingState state = store.lock(userId, now)
                    .orElseThrow(() -> error(Reason.USER_NOT_FOUND, "사용자 계정을 찾을 수 없습니다."));
            validateAccessible(state);
            validateIntegrity(state);
            if (state.onboardingCompletedAt() != null) {
                throw error(Reason.ONBOARDING_ALREADY_COMPLETED, "이미 온보딩을 완료했습니다.");
            }

            OnboardingStore.Language language = requireLanguage(command.defaultLanguageCode());
            store.saveInitialSettings(
                    userId,
                    command.nationalityCode(),
                    language.languageCode(),
                    command.locationSharingDefault(),
                    command.timezoneName(),
                    command.notificationPreferences(),
                    now
            );
            return new InitialSettingsResult(
                    command.nationalityCode(), language.languageCode(), false, null,
                    new InitialSettingsResult.Settings(
                            state.currentUiMode(), command.locationSharingDefault(), command.timezoneName()
                    ),
                    command.notificationPreferences().stream()
                            .map(preference -> new InitialSettingsResult.NotificationPreference(
                                    preference.notificationCategory(),
                                    preference.pushEnabled(),
                                    preference.inAppEnabled()
                            ))
                            .toList()
            );
        }));
    }

    public OnboardingCompletionResult complete(String authorizationHeader) {
        long userId = tokenPort.requireAccessUserId(authorizationHeader);
        Instant now = clock.instant();
        return Objects.requireNonNull(transactions.execute(status -> {
            OnboardingStore.OnboardingState state = store.lock(userId, now)
                    .orElseThrow(() -> error(Reason.USER_NOT_FOUND, "사용자 계정을 찾을 수 없습니다."));
            validateAccessible(state);
            validateIntegrity(state);
            requireLanguage(state.defaultLanguageCode());
            validateStoredSettingsForCompletion(state);

            Instant completedAt = state.onboardingCompletedAt();
            if (completedAt == null) {
                store.complete(userId, now);
                completedAt = now;
            }
            return new OnboardingCompletionResult(
                    state.userId(), state.travelerProfileId(), "TRAVELER", "TRAVELER",
                    true, completedAt
            );
        }));
    }

    public PermissionStatusResult getPermissionStatus(String authorizationHeader) {
        long userId = tokenPort.requireAccessUserId(authorizationHeader);
        OnboardingStore.PermissionState state = store.findPermissionState(userId, clock.instant())
                .orElseThrow(() -> error(Reason.USER_NOT_FOUND, "사용자 계정을 찾을 수 없습니다."));
        validatePermissionAccessible(state);
        validatePermissionIntegrity(state.permissionSnapshot());
        return toPermissionStatus(state.permissionSnapshot());
    }

    public PermissionStatusResult savePermissionStatus(
            String authorizationHeader,
            SavePermissionStatusCommand rawCommand
    ) {
        OnboardingStore.PermissionSnapshot requested = validatePermissionStatus(rawCommand);
        long userId = tokenPort.requireAccessUserId(authorizationHeader);
        Instant now = clock.instant();
        return Objects.requireNonNull(transactions.execute(status -> {
            OnboardingStore.PermissionState state = store.lockPermissionState(userId, now)
                    .orElseThrow(() -> error(Reason.USER_NOT_FOUND, "사용자 계정을 찾을 수 없습니다."));
            validatePermissionAccessible(state);
            validatePermissionIntegrity(state.permissionSnapshot());

            OnboardingStore.PermissionSnapshot current = state.permissionSnapshot();
            if (current != null) {
                int timestampOrder = requested.checkedAt().compareTo(current.checkedAt());
                if (timestampOrder < 0) {
                    throw error(Reason.STALE_PERMISSION_SNAPSHOT, "더 최신인 권한 상태가 이미 저장되어 있습니다.");
                }
                if (timestampOrder == 0) {
                    if (!samePermissionStatuses(current, requested)) {
                        throw error(
                                Reason.PERMISSION_SNAPSHOT_TIMESTAMP_CONFLICT,
                                "같은 확인 시각에 서로 다른 권한 상태가 이미 저장되어 있습니다."
                        );
                    }
                    return toPermissionStatus(current);
                }
            }

            OnboardingStore.PermissionSnapshot saved = new OnboardingStore.PermissionSnapshot(
                    requested.locationStatus(), requested.notificationStatus(),
                    requested.cameraStatus(), requested.microphoneStatus(),
                    requested.checkedAt(), now
            );
            store.savePermissionSnapshot(userId, saved, now);
            return toPermissionStatus(saved);
        }));
    }

    private OnboardingStore.Language requireLanguage(String languageCode) {
        OnboardingStore.Language language = store.findLanguage(languageCode)
                .orElseThrow(() -> error(Reason.LANGUAGE_NOT_FOUND, "언어를 찾을 수 없습니다."));
        if (!language.active()) {
            throw error(Reason.LANGUAGE_NOT_AVAILABLE, "현재 사용할 수 없는 언어입니다.");
        }
        return language;
    }

    private static ValidatedSettings validate(SaveInitialSettingsCommand command) {
        if (command == null || command.nationalityCode() == null
                || command.defaultLanguageCode() == null
                || command.locationSharingDefault() == null
                || command.timezoneName() == null
                || command.notificationPreferences() == null) {
            throw error(Reason.INVALID_INITIAL_SETTINGS_REQUEST, "초기 설정 요청 형식이 올바르지 않습니다.");
        }

        String nationalityCode = command.nationalityCode().trim().toUpperCase(Locale.ROOT);
        if (!NATIONALITY_CODES.contains(nationalityCode)) {
            throw error(Reason.INVALID_NATIONALITY_CODE, "유효한 ISO 3166-1 alpha-2 국적 코드가 아닙니다.");
        }

        String languageCode = canonicalLanguageCode(command.defaultLanguageCode());
        String timezoneName = command.timezoneName().trim();
        if (timezoneName.length() > 50 || !ZoneId.getAvailableZoneIds().contains(timezoneName)) {
            throw error(Reason.INVALID_TIMEZONE_NAME, "유효한 IANA 시간대를 입력해 주세요.");
        }

        List<OnboardingStore.NotificationPreference> preferences = validatePreferences(
                command.notificationPreferences()
        );
        return new ValidatedSettings(
                nationalityCode, languageCode, command.locationSharingDefault(), timezoneName, preferences
        );
    }

    private static String canonicalLanguageCode(String value) {
        String languageCode = value.trim();
        if (languageCode.isEmpty() || languageCode.length() > 35 || languageCode.contains("_")) {
            throw error(Reason.INVALID_LANGUAGE_CODE, "기본 언어 코드 형식이 올바르지 않습니다.");
        }
        try {
            String canonical = new Locale.Builder().setLanguageTag(languageCode).build().toLanguageTag();
            if ("und".equals(canonical)) {
                throw new IllegalArgumentException("Undefined language");
            }
            return canonical;
        } catch (RuntimeException exception) {
            throw error(Reason.INVALID_LANGUAGE_CODE, "기본 언어 코드 형식이 올바르지 않습니다.");
        }
    }

    private static List<OnboardingStore.NotificationPreference> validatePreferences(
            List<SaveInitialSettingsCommand.NotificationPreference> preferences
    ) {
        if (preferences.size() != NOTIFICATION_CATEGORIES.size()) {
            throw error(Reason.INVALID_NOTIFICATION_PREFERENCES, "7개 알림 범주를 모두 입력해 주세요.");
        }
        Set<String> categories = new HashSet<>();
        for (SaveInitialSettingsCommand.NotificationPreference preference : preferences) {
            if (preference == null || preference.notificationCategory() == null
                    || preference.pushEnabled() == null || preference.inAppEnabled() == null
                    || !NOTIFICATION_CATEGORIES.contains(preference.notificationCategory())
                    || !categories.add(preference.notificationCategory())) {
                throw error(Reason.INVALID_NOTIFICATION_PREFERENCES, "알림 설정 형식이 올바르지 않습니다.");
            }
        }
        return NOTIFICATION_CATEGORIES.stream()
                .map(category -> preferences.stream()
                        .filter(preference -> category.equals(preference.notificationCategory()))
                        .findFirst()
                        .map(preference -> new OnboardingStore.NotificationPreference(
                                category, preference.pushEnabled(), preference.inAppEnabled()
                        ))
                        .orElseThrow())
                .toList();
    }

    private static void validateAccessible(OnboardingStore.OnboardingState state) {
        switch (state.accountStatus()) {
            case "ACTIVE" -> { }
            case "SUSPENDED" -> throw error(Reason.ACCOUNT_SUSPENDED, "정지된 계정입니다.");
            case "BANNED" -> throw error(Reason.ACCOUNT_BANNED, "차단된 계정입니다.");
            case "WITHDRAWN" -> throw error(Reason.ACCOUNT_WITHDRAWN, "탈퇴한 계정입니다.");
            default -> throw new IllegalStateException("Unknown account status: " + state.accountStatus());
        }
        if (state.loginRestricted()) {
            throw OnboardingException.loginRestricted(state.restrictionEndsAt());
        }
    }

    private static void validatePermissionAccessible(OnboardingStore.PermissionState state) {
        switch (state.accountStatus()) {
            case "ACTIVE" -> { }
            case "SUSPENDED" -> throw error(Reason.ACCOUNT_SUSPENDED, "정지된 계정입니다.");
            case "BANNED" -> throw error(Reason.ACCOUNT_BANNED, "차단된 계정입니다.");
            case "WITHDRAWN" -> throw error(Reason.ACCOUNT_WITHDRAWN, "탈퇴한 계정입니다.");
            default -> throw new IllegalStateException("Unknown account status: " + state.accountStatus());
        }
        if (state.loginRestricted()) {
            throw OnboardingException.loginRestricted(state.restrictionEndsAt());
        }
    }

    private static OnboardingStore.PermissionSnapshot validatePermissionStatus(
            SavePermissionStatusCommand command
    ) {
        if (command == null || command.locationStatus() == null
                || command.notificationStatus() == null || command.cameraStatus() == null
                || command.microphoneStatus() == null || command.checkedAt() == null) {
            throw error(Reason.INVALID_PERMISSION_STATUS_REQUEST, "권한 상태 요청 형식이 올바르지 않습니다.");
        }
        if (!PERMISSION_STATUSES.contains(command.locationStatus())
                || !PERMISSION_STATUSES.contains(command.notificationStatus())
                || !PERMISSION_STATUSES.contains(command.cameraStatus())
                || !PERMISSION_STATUSES.contains(command.microphoneStatus())) {
            throw error(Reason.INVALID_PERMISSION_STATUS, "허용되지 않은 권한 상태가 포함되어 있습니다.");
        }
        try {
            Instant checkedAt = OffsetDateTime.parse(command.checkedAt()).toInstant();
            return new OnboardingStore.PermissionSnapshot(
                    command.locationStatus(), command.notificationStatus(),
                    command.cameraStatus(), command.microphoneStatus(), checkedAt, null
            );
        } catch (DateTimeParseException exception) {
            throw error(Reason.INVALID_CHECKED_AT, "checkedAt은 오프셋을 포함한 ISO 8601 형식이어야 합니다.");
        }
    }

    private static void validatePermissionIntegrity(OnboardingStore.PermissionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (!PERMISSION_STATUSES.contains(snapshot.locationStatus())
                || !PERMISSION_STATUSES.contains(snapshot.notificationStatus())
                || !PERMISSION_STATUSES.contains(snapshot.cameraStatus())
                || !PERMISSION_STATUSES.contains(snapshot.microphoneStatus())
                || snapshot.checkedAt() == null || snapshot.updatedAt() == null) {
            throw new IllegalStateException("Permission snapshot data is incomplete or inconsistent");
        }
    }

    private static boolean samePermissionStatuses(
            OnboardingStore.PermissionSnapshot current,
            OnboardingStore.PermissionSnapshot requested
    ) {
        return current.locationStatus().equals(requested.locationStatus())
                && current.notificationStatus().equals(requested.notificationStatus())
                && current.cameraStatus().equals(requested.cameraStatus())
                && current.microphoneStatus().equals(requested.microphoneStatus());
    }

    private static PermissionStatusResult toPermissionStatus(OnboardingStore.PermissionSnapshot snapshot) {
        if (snapshot == null) {
            return new PermissionStatusResult(null);
        }
        return new PermissionStatusResult(new PermissionStatusResult.PermissionSnapshot(
                snapshot.locationStatus(), snapshot.notificationStatus(),
                snapshot.cameraStatus(), snapshot.microphoneStatus(),
                snapshot.checkedAt(), snapshot.updatedAt()
        ));
    }

    private static void validateIntegrity(OnboardingStore.OnboardingState state) {
        if (state.travelerProfileId() == null || state.currentUiMode() == null
                || state.locationSharingDefault() == null || state.timezoneName() == null
                || state.nationalityCode() == null || state.defaultLanguageCode() == null) {
            throw new IllegalStateException("Onboarding is missing required profile or settings data");
        }
        List<String> categories = state.notificationPreferences().stream()
                .map(OnboardingStore.NotificationPreference::notificationCategory)
                .toList();
        if (!NOTIFICATION_CATEGORIES.equals(categories)) {
            throw new IllegalStateException("Onboarding notification preferences are incomplete or inconsistent");
        }
    }

    private static void validateStoredSettingsForCompletion(OnboardingStore.OnboardingState state) {
        if (!NATIONALITY_CODES.contains(state.nationalityCode())
                || !ZoneId.getAvailableZoneIds().contains(state.timezoneName())
                || !"TRAVELER".equals(state.currentUiMode())) {
            throw new IllegalStateException("Stored onboarding settings are invalid");
        }
    }

    private static InitialSettingsResult toInitialSettings(OnboardingStore.OnboardingState state) {
        return new InitialSettingsResult(
                state.nationalityCode(), state.defaultLanguageCode(),
                state.onboardingCompletedAt() != null, state.onboardingCompletedAt(),
                new InitialSettingsResult.Settings(
                        state.currentUiMode(), state.locationSharingDefault(), state.timezoneName()
                ),
                state.notificationPreferences().stream()
                        .map(preference -> new InitialSettingsResult.NotificationPreference(
                                preference.notificationCategory(),
                                preference.pushEnabled(),
                                preference.inAppEnabled()
                        ))
                        .toList()
        );
    }

    private static OnboardingException error(Reason reason, String message) {
        return OnboardingException.of(reason, message);
    }

    private record ValidatedSettings(
            String nationalityCode,
            String defaultLanguageCode,
            boolean locationSharingDefault,
            String timezoneName,
            List<OnboardingStore.NotificationPreference> notificationPreferences
    ) {
    }
}
