package com.allygo.allygo_api.onboarding;

import com.allygo.allygo_api.onboarding.application.OnboardingService;
import com.allygo.allygo_api.onboarding.application.port.OnboardingStore;
import com.allygo.allygo_api.onboarding.infrastructure.persistence.JdbcOnboardingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcOnboardingStoreTest {
    private static final Instant NOW = Instant.parse("2026-09-04T03:00:00Z");

    private JdbcTemplate jdbc;
    private JdbcOnboardingStore store;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:onboarding-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcOnboardingStore(new NamedParameterJdbcTemplate(dataSource));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        createSchema();
        insertAggregate();
    }

    @Test
    void readsTheAggregateInTheDocumentedNotificationOrder() {
        OnboardingStore.OnboardingState state = store.find(1L, NOW).orElseThrow();

        assertThat(state.nationalityCode()).isEqualTo("KR");
        assertThat(state.travelerProfileId()).isEqualTo(11L);
        assertThat(state.notificationPreferences())
                .extracting(OnboardingStore.NotificationPreference::notificationCategory)
                .containsExactlyElementsOf(OnboardingService.NOTIFICATION_CATEGORIES);
        assertThat(state.loginRestricted()).isFalse();
    }

    @Test
    void exposesAnIndefiniteActiveLoginRestriction() {
        jdbc.update("INSERT INTO sanctions VALUES (31, 1, 'ACTIVE', ?, NULL)", Timestamp.from(NOW.minusSeconds(10)));
        jdbc.update("INSERT INTO sanction_restrictions VALUES (31, 'LOGIN')");

        OnboardingStore.OnboardingState state = store.find(1L, NOW).orElseThrow();

        assertThat(state.loginRestricted()).isTrue();
        assertThat(state.restrictionEndsAt()).isNull();
    }

    @Test
    void savesAllSettingsWhilePreservingUiModeAndCompletionState() {
        List<OnboardingStore.NotificationPreference> preferences =
                OnboardingService.NOTIFICATION_CATEGORIES.stream()
                        .map(category -> new OnboardingStore.NotificationPreference(
                                category, !"CHAT".equals(category), "CHAT".equals(category)
                        ))
                        .toList();

        transactions.executeWithoutResult(status -> {
            store.lock(1L, NOW).orElseThrow();
            store.saveInitialSettings(1L, "JP", "ja", true, "Asia/Tokyo", preferences, NOW);
        });

        Map<String, Object> user = jdbc.queryForMap("""
                SELECT nationality_code, default_language_code, version FROM users WHERE user_id = 1
                """);
        assertThat(user.get("NATIONALITY_CODE")).isEqualTo("JP");
        assertThat(user.get("DEFAULT_LANGUAGE_CODE")).isEqualTo("ja");
        assertThat(user.get("VERSION")).isEqualTo(1);
        Map<String, Object> settings = jdbc.queryForMap("""
                SELECT current_ui_mode, location_sharing_default, timezone_name
                FROM user_settings WHERE user_id = 1
                """);
        assertThat(settings.get("CURRENT_UI_MODE")).isEqualTo("TRAVELER");
        assertThat(settings.get("LOCATION_SHARING_DEFAULT")).isEqualTo(true);
        assertThat(settings.get("TIMEZONE_NAME")).isEqualTo("Asia/Tokyo");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification_preferences
                WHERE user_id = 1 AND notification_category = 'CHAT'
                  AND push_enabled = FALSE AND in_app_enabled = TRUE
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT onboarding_completed_at FROM traveler_profiles WHERE user_id = 1
                """, Timestamp.class)).isNull();
    }

    @Test
    void completesOnboardingWithoutChangingTheFirstCompletionTime() {
        transactions.executeWithoutResult(status -> {
            OnboardingStore.OnboardingState state = store.lock(1L, NOW).orElseThrow();
            if (state.onboardingCompletedAt() == null) {
                store.complete(1L, NOW);
            }
        });
        Instant later = NOW.plusSeconds(60);
        transactions.executeWithoutResult(status -> {
            OnboardingStore.OnboardingState state = store.lock(1L, later).orElseThrow();
            if (state.onboardingCompletedAt() == null) {
                store.complete(1L, later);
            }
        });

        Timestamp completedAt = jdbc.queryForObject("""
                SELECT onboarding_completed_at FROM traveler_profiles WHERE user_id = 1
                """, Timestamp.class);
        assertThat(completedAt.toInstant()).isEqualTo(NOW);
    }

    @Test
    void rollsBackTheWholeSettingsUpdateWhenOneNotificationRowIsMissing() {
        jdbc.update("""
                DELETE FROM notification_preferences
                WHERE user_id = 1 AND notification_category = 'SANCTION'
                """);
        List<OnboardingStore.NotificationPreference> preferences =
                OnboardingService.NOTIFICATION_CATEGORIES.stream()
                        .map(category -> new OnboardingStore.NotificationPreference(category, false, false))
                        .toList();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                store.saveInitialSettings(1L, "JP", "ja", true, "Asia/Tokyo", preferences, NOW)
        )).isInstanceOf(IllegalStateException.class);

        Map<String, Object> user = jdbc.queryForMap("""
                SELECT nationality_code, default_language_code, version FROM users WHERE user_id = 1
                """);
        assertThat(user.get("NATIONALITY_CODE")).isEqualTo("KR");
        assertThat(user.get("DEFAULT_LANGUAGE_CODE")).isEqualTo("ko");
        assertThat(user.get("VERSION")).isEqualTo(0);
        assertThat(jdbc.queryForObject("""
                SELECT location_sharing_default FROM user_settings WHERE user_id = 1
                """, Boolean.class)).isFalse();
    }

    @Test
    void readsAMissingPermissionSnapshotAsANormalNullState() {
        OnboardingStore.PermissionState state = store.findPermissionState(1L, NOW).orElseThrow();

        assertThat(state.accountStatus()).isEqualTo("ACTIVE");
        assertThat(state.loginRestricted()).isFalse();
        assertThat(state.permissionSnapshot()).isNull();
    }

    @Test
    void insertsAndConditionallyUpdatesThePermissionSnapshot() {
        OnboardingStore.PermissionSnapshot initial = new OnboardingStore.PermissionSnapshot(
                "GRANTED", "DENIED", "NOT_DETERMINED", "RESTRICTED",
                NOW.minusSeconds(20), NOW.minusSeconds(10)
        );
        OnboardingStore.PermissionSnapshot newer = new OnboardingStore.PermissionSnapshot(
                "DENIED", "GRANTED", "GRANTED", "NOT_DETERMINED",
                NOW.minusSeconds(5), NOW
        );

        transactions.executeWithoutResult(status -> {
            store.lockPermissionState(1L, NOW).orElseThrow();
            store.savePermissionSnapshot(1L, initial, initial.updatedAt());
        });
        transactions.executeWithoutResult(status -> {
            store.lockPermissionState(1L, NOW).orElseThrow();
            store.savePermissionSnapshot(1L, newer, newer.updatedAt());
        });

        OnboardingStore.PermissionSnapshot saved =
                store.findPermissionState(1L, NOW).orElseThrow().permissionSnapshot();
        assertThat(saved.locationStatus()).isEqualTo("DENIED");
        assertThat(saved.notificationStatus()).isEqualTo("GRANTED");
        assertThat(saved.checkedAt()).isEqualTo(NOW.minusSeconds(5));
        assertThat(saved.updatedAt()).isEqualTo(NOW);
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE languages (
                    language_code VARCHAR(35) PRIMARY KEY,
                    is_active BOOLEAN NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE users (
                    user_id BIGINT PRIMARY KEY,
                    nationality_code CHAR(2) NOT NULL,
                    default_language_code VARCHAR(35) NOT NULL,
                    account_status VARCHAR(20) NOT NULL,
                    version INTEGER NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE traveler_profiles (
                    traveler_profile_id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL UNIQUE,
                    onboarding_completed_at TIMESTAMP WITH TIME ZONE,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE user_permission_snapshots (
                    user_id BIGINT PRIMARY KEY REFERENCES users(user_id),
                    location_status VARCHAR(20) NOT NULL,
                    notification_status VARCHAR(20) NOT NULL,
                    camera_status VARCHAR(20) NOT NULL,
                    microphone_status VARCHAR(20) NOT NULL,
                    checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE user_settings (
                    user_id BIGINT PRIMARY KEY,
                    current_ui_mode VARCHAR(10) NOT NULL,
                    location_sharing_default BOOLEAN NOT NULL,
                    timezone_name VARCHAR(50) NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE notification_preferences (
                    user_id BIGINT NOT NULL,
                    notification_category VARCHAR(30) NOT NULL,
                    push_enabled BOOLEAN NOT NULL,
                    in_app_enabled BOOLEAN NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    PRIMARY KEY (user_id, notification_category)
                )
                """);
        jdbc.execute("""
                CREATE TABLE sanctions (
                    sanction_id BIGINT PRIMARY KEY,
                    target_user_id BIGINT NOT NULL,
                    status VARCHAR(15) NOT NULL,
                    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    ends_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbc.execute("""
                CREATE TABLE sanction_restrictions (
                    sanction_id BIGINT NOT NULL,
                    restriction_scope VARCHAR(30) NOT NULL
                )
                """);
    }

    private void insertAggregate() {
        jdbc.update("INSERT INTO languages VALUES ('ko', TRUE), ('ja', TRUE)");
        jdbc.update("INSERT INTO users VALUES (1, 'KR', 'ko', 'ACTIVE', 0, ?)", Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update("INSERT INTO traveler_profiles VALUES (11, 1, NULL, ?)", Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update("INSERT INTO user_settings VALUES (1, 'TRAVELER', FALSE, 'UTC', ?)", Timestamp.from(NOW.minusSeconds(60)));
        for (int index = OnboardingService.NOTIFICATION_CATEGORIES.size() - 1; index >= 0; index--) {
            jdbc.update("INSERT INTO notification_preferences VALUES (1, ?, TRUE, TRUE, ?)",
                    OnboardingService.NOTIFICATION_CATEGORIES.get(index), Timestamp.from(NOW.minusSeconds(60)));
        }
    }
}
