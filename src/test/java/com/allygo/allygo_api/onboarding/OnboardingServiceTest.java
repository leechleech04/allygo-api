package com.allygo.allygo_api.onboarding;

import com.allygo.allygo_api.auth.account.application.port.AccountTokenPort;
import com.allygo.allygo_api.onboarding.application.OnboardingService;
import com.allygo.allygo_api.onboarding.application.command.SaveInitialSettingsCommand;
import com.allygo.allygo_api.onboarding.application.command.SavePermissionStatusCommand;
import com.allygo.allygo_api.onboarding.application.port.OnboardingStore;
import com.allygo.allygo_api.onboarding.domain.OnboardingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-04T03:00:00Z");

    @Mock OnboardingStore store;
    @Mock AccountTokenPort tokenPort;
    @Mock TransactionOperations transactions;

    private OnboardingService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().when(tokenPort.requireAccessUserId("Bearer access")).thenReturn(1L);
        service = new OnboardingService(
                store, tokenPort, transactions, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void retrievesCompleteInitialSettingsInDocumentedOrder() {
        when(store.find(1L, NOW)).thenReturn(Optional.of(state(null, "TRAVELER")));

        var result = service.getInitialSettings("Bearer access");

        assertThat(result.nationalityCode()).isEqualTo("KR");
        assertThat(result.onboardingCompleted()).isFalse();
        assertThat(result.notificationPreferences())
                .extracting(preference -> preference.notificationCategory())
                .containsExactlyElementsOf(OnboardingService.NOTIFICATION_CATEGORIES);
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesNormalizedFullSettingsWithoutCompletingOnboarding() {
        when(store.lock(1L, NOW)).thenReturn(Optional.of(state(null, "TRAVELER")));
        when(store.findLanguage("ko-KR"))
                .thenReturn(Optional.of(new OnboardingStore.Language("ko-KR", true)));
        SaveInitialSettingsCommand command = command("kr", "KO-kr", "Asia/Seoul");

        var result = service.saveInitialSettings("Bearer access", command);

        ArgumentCaptor<List<OnboardingStore.NotificationPreference>> preferences = ArgumentCaptor.forClass(List.class);
        verify(store).saveInitialSettings(
                eq(1L), eq("KR"), eq("ko-KR"), eq(true), eq("Asia/Seoul"),
                preferences.capture(), eq(NOW)
        );
        assertThat(preferences.getValue())
                .extracting(OnboardingStore.NotificationPreference::notificationCategory)
                .containsExactlyElementsOf(OnboardingService.NOTIFICATION_CATEGORIES);
        assertThat(result.onboardingCompleted()).isFalse();
        assertThat(result.onboardingCompletedAt()).isNull();
        assertThat(result.settings().currentUiMode()).isEqualTo("TRAVELER");
    }

    @Test
    void rejectsDuplicateNotificationCategory() {
        List<SaveInitialSettingsCommand.NotificationPreference> preferences = preferences();
        preferences.set(6, new SaveInitialSettingsCommand.NotificationPreference("MATCHING", true, true));
        SaveInitialSettingsCommand command = new SaveInitialSettingsCommand(
                "KR", "ko", false, "UTC", preferences
        );

        assertThatThrownBy(() -> service.saveInitialSettings("Bearer access", command))
                .isInstanceOfSatisfying(OnboardingException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                OnboardingException.Reason.INVALID_NOTIFICATION_PREFERENCES
                        ));
        verify(store, never()).lock(any(Long.class), any(Instant.class));
    }

    @Test
    void rejectsSettingsSaveAfterOnboardingCompletion() {
        when(store.lock(1L, NOW)).thenReturn(Optional.of(state(NOW.minusSeconds(60), "TRAVELER")));

        assertThatThrownBy(() -> service.saveInitialSettings(
                "Bearer access", command("KR", "ko", "UTC")
        )).isInstanceOfSatisfying(OnboardingException.class, exception ->
                assertThat(exception.reason()).isEqualTo(
                        OnboardingException.Reason.ONBOARDING_ALREADY_COMPLETED
                ));
        verify(store, never()).saveInitialSettings(
                any(Long.class), any(), any(), any(Boolean.class), any(), any(), any()
        );
    }

    @Test
    void completesOnceAndReturnsTheOriginalTimestampOnRetry() {
        when(store.lock(1L, NOW))
                .thenReturn(Optional.of(state(null, "TRAVELER")))
                .thenReturn(Optional.of(state(NOW, "TRAVELER")));
        when(store.findLanguage("ko"))
                .thenReturn(Optional.of(new OnboardingStore.Language("ko", true)));

        var first = service.complete("Bearer access");
        var second = service.complete("Bearer access");

        verify(store).complete(1L, NOW);
        assertThat(first.onboardingCompletedAt()).isEqualTo(NOW);
        assertThat(second.onboardingCompletedAt()).isEqualTo(NOW);
        assertThat(second.roleType()).isEqualTo("TRAVELER");
    }

    @Test
    void blocksCompletionWhenStoredLanguageIsInactive() {
        when(store.lock(1L, NOW)).thenReturn(Optional.of(state(null, "TRAVELER")));
        when(store.findLanguage("ko"))
                .thenReturn(Optional.of(new OnboardingStore.Language("ko", false)));

        assertThatThrownBy(() -> service.complete("Bearer access"))
                .isInstanceOfSatisfying(OnboardingException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                OnboardingException.Reason.LANGUAGE_NOT_AVAILABLE
                        ));
        verify(store, never()).complete(any(Long.class), any());
    }

    @Test
    void treatsMissingNotificationRowsAsIntegrityFailure() {
        OnboardingStore.OnboardingState incomplete = new OnboardingStore.OnboardingState(
                1L, "KR", "ko", "ACTIVE", 2L, null,
                "TRAVELER", false, "UTC", preferencesInState().subList(0, 6),
                false, null
        );
        when(store.find(1L, NOW)).thenReturn(Optional.of(incomplete));

        assertThatThrownBy(() -> service.getInitialSettings("Bearer access"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notification preferences");
    }

    @Test
    void retrievesAnUnstoredPermissionSnapshotAsNull() {
        when(store.findPermissionState(1L, NOW)).thenReturn(Optional.of(permissionState(null)));

        var result = service.getPermissionStatus("Bearer access");

        assertThat(result.permissionSnapshot()).isNull();
    }

    @Test
    void savesANewerFullPermissionSnapshot() {
        Instant checkedAt = NOW.minusSeconds(10);
        when(store.lockPermissionState(1L, NOW)).thenReturn(Optional.of(permissionState(null)));

        var result = service.savePermissionStatus("Bearer access", permissionCommand(checkedAt));

        ArgumentCaptor<OnboardingStore.PermissionSnapshot> snapshot =
                ArgumentCaptor.forClass(OnboardingStore.PermissionSnapshot.class);
        verify(store).savePermissionSnapshot(eq(1L), snapshot.capture(), eq(NOW));
        assertThat(snapshot.getValue().checkedAt()).isEqualTo(checkedAt);
        assertThat(snapshot.getValue().updatedAt()).isEqualTo(NOW);
        assertThat(result.permissionSnapshot().locationStatus()).isEqualTo("GRANTED");
    }

    @Test
    void returnsTheExistingSnapshotWithoutWritingForAnIdenticalRetry() {
        Instant checkedAt = NOW.minusSeconds(10);
        OnboardingStore.PermissionSnapshot current = permissionSnapshot(checkedAt, NOW.minusSeconds(5));
        when(store.lockPermissionState(1L, NOW)).thenReturn(Optional.of(permissionState(current)));

        var result = service.savePermissionStatus("Bearer access", permissionCommand(checkedAt));

        verify(store, never()).savePermissionSnapshot(any(Long.class), any(), any());
        assertThat(result.permissionSnapshot().updatedAt()).isEqualTo(NOW.minusSeconds(5));
    }

    @Test
    void rejectsAnOlderPermissionSnapshotWithoutWriting() {
        when(store.lockPermissionState(1L, NOW)).thenReturn(Optional.of(
                permissionState(permissionSnapshot(NOW.minusSeconds(5), NOW.minusSeconds(4)))
        ));

        assertThatThrownBy(() -> service.savePermissionStatus(
                "Bearer access", permissionCommand(NOW.minusSeconds(10))
        )).isInstanceOfSatisfying(OnboardingException.class, exception ->
                assertThat(exception.reason()).isEqualTo(
                        OnboardingException.Reason.STALE_PERMISSION_SNAPSHOT
                ));
        verify(store, never()).savePermissionSnapshot(any(Long.class), any(), any());
    }

    @Test
    void rejectsDifferentStatusesAtTheSameCheckedAt() {
        Instant checkedAt = NOW.minusSeconds(10);
        OnboardingStore.PermissionSnapshot current = permissionSnapshot(checkedAt, NOW.minusSeconds(5));
        when(store.lockPermissionState(1L, NOW)).thenReturn(Optional.of(permissionState(current)));
        SavePermissionStatusCommand conflicting = new SavePermissionStatusCommand(
                "DENIED", "DENIED", "NOT_DETERMINED", "RESTRICTED", checkedAt.toString()
        );

        assertThatThrownBy(() -> service.savePermissionStatus("Bearer access", conflicting))
                .isInstanceOfSatisfying(OnboardingException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                OnboardingException.Reason.PERMISSION_SNAPSHOT_TIMESTAMP_CONFLICT
                        ));
        verify(store, never()).savePermissionSnapshot(any(Long.class), any(), any());
    }

    @Test
    void rejectsPermissionStatusOutsideTheDocumentedEnum() {
        SavePermissionStatusCommand invalid = new SavePermissionStatusCommand(
                "UNKNOWN", "DENIED", "NOT_DETERMINED", "RESTRICTED", NOW.toString()
        );

        assertThatThrownBy(() -> service.savePermissionStatus("Bearer access", invalid))
                .isInstanceOfSatisfying(OnboardingException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                OnboardingException.Reason.INVALID_PERMISSION_STATUS
                        ));
        verify(store, never()).lockPermissionState(any(Long.class), any(Instant.class));
    }

    @Test
    void rejectsCheckedAtWithoutAnOffset() {
        SavePermissionStatusCommand invalid = new SavePermissionStatusCommand(
                "GRANTED", "DENIED", "NOT_DETERMINED", "RESTRICTED", "2026-09-04T12:00:00"
        );

        assertThatThrownBy(() -> service.savePermissionStatus("Bearer access", invalid))
                .isInstanceOfSatisfying(OnboardingException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                OnboardingException.Reason.INVALID_CHECKED_AT
                        ));
    }

    private static OnboardingStore.OnboardingState state(Instant completedAt, String currentUiMode) {
        return new OnboardingStore.OnboardingState(
                1L, "KR", "ko", "ACTIVE", 2L, completedAt,
                currentUiMode, false, "UTC", preferencesInState(), false, null
        );
    }

    private static SaveInitialSettingsCommand command(
            String nationalityCode,
            String languageCode,
            String timezoneName
    ) {
        return new SaveInitialSettingsCommand(
                nationalityCode, languageCode, true, timezoneName, preferences()
        );
    }

    private static List<SaveInitialSettingsCommand.NotificationPreference> preferences() {
        return OnboardingService.NOTIFICATION_CATEGORIES.stream()
                .map(category -> new SaveInitialSettingsCommand.NotificationPreference(category, true, true))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    private static List<OnboardingStore.NotificationPreference> preferencesInState() {
        return OnboardingService.NOTIFICATION_CATEGORIES.stream()
                .map(category -> new OnboardingStore.NotificationPreference(category, true, true))
                .toList();
    }

    private static OnboardingStore.PermissionState permissionState(
            OnboardingStore.PermissionSnapshot snapshot
    ) {
        return new OnboardingStore.PermissionState(1L, "ACTIVE", false, null, snapshot);
    }

    private static OnboardingStore.PermissionSnapshot permissionSnapshot(
            Instant checkedAt,
            Instant updatedAt
    ) {
        return new OnboardingStore.PermissionSnapshot(
                "GRANTED", "DENIED", "NOT_DETERMINED", "RESTRICTED", checkedAt, updatedAt
        );
    }

    private static SavePermissionStatusCommand permissionCommand(Instant checkedAt) {
        return new SavePermissionStatusCommand(
                "GRANTED", "DENIED", "NOT_DETERMINED", "RESTRICTED", checkedAt.toString()
        );
    }
}
