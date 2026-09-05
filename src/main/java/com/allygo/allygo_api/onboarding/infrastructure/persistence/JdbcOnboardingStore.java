package com.allygo.allygo_api.onboarding.infrastructure.persistence;

import com.allygo.allygo_api.onboarding.application.OnboardingService;
import com.allygo.allygo_api.onboarding.application.port.OnboardingStore;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JdbcOnboardingStore implements OnboardingStore {
    private static final String NOTIFICATION_ORDER = """
            CASE np.notification_category
                WHEN 'MATCHING' THEN 1
                WHEN 'CHAT' THEN 2
                WHEN 'CALL' THEN 3
                WHEN 'REVIEW' THEN 4
                WHEN 'REPORT' THEN 5
                WHEN 'VERIFICATION' THEN 6
                WHEN 'SANCTION' THEN 7
                ELSE 8
            END
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOnboardingStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OnboardingState> find(long userId, Instant now) {
        String sql = """
                SELECT u.user_id, u.nationality_code, u.default_language_code, u.account_status,
                       tp.traveler_profile_id, tp.onboarding_completed_at,
                       us.current_ui_mode, us.location_sharing_default, us.timezone_name,
                       np.notification_category, np.push_enabled, np.in_app_enabled,
                       EXISTS (
                           SELECT 1
                           FROM sanctions s
                           JOIN sanction_restrictions sr ON sr.sanction_id = s.sanction_id
                           WHERE s.target_user_id = u.user_id
                             AND s.status = 'ACTIVE'
                             AND s.starts_at <= :now
                             AND (s.ends_at IS NULL OR s.ends_at > :now)
                             AND sr.restriction_scope = 'LOGIN'
                       ) AS login_restricted,
                       (
                           SELECT s.ends_at
                           FROM sanctions s
                           JOIN sanction_restrictions sr ON sr.sanction_id = s.sanction_id
                           WHERE s.target_user_id = u.user_id
                             AND s.status = 'ACTIVE'
                             AND s.starts_at <= :now
                             AND (s.ends_at IS NULL OR s.ends_at > :now)
                             AND sr.restriction_scope = 'LOGIN'
                           ORDER BY s.ends_at NULLS FIRST
                           LIMIT 1
                       ) AS restriction_ends_at
                FROM users u
                LEFT JOIN traveler_profiles tp ON tp.user_id = u.user_id
                LEFT JOIN user_settings us ON us.user_id = u.user_id
                LEFT JOIN notification_preferences np ON np.user_id = u.user_id
                WHERE u.user_id = :userId
                ORDER BY %s
                """.formatted(NOTIFICATION_ORDER);
        return jdbc.query(sql, params(userId, now), stateExtractor());
    }

    @Override
    public Optional<OnboardingState> lock(long userId, Instant now) {
        List<UserRow> users = jdbc.query("""
                SELECT user_id, nationality_code, default_language_code, account_status
                FROM users
                WHERE user_id = :userId
                FOR UPDATE
                """, Map.of("userId", userId), (rs, rowNum) -> new UserRow(
                rs.getLong("user_id"),
                rs.getString("nationality_code"),
                rs.getString("default_language_code"),
                rs.getString("account_status")
        ));
        if (users.isEmpty()) {
            return Optional.empty();
        }
        UserRow user = users.getFirst();

        List<TravelerRow> travelers = jdbc.query("""
                SELECT traveler_profile_id, onboarding_completed_at
                FROM traveler_profiles
                WHERE user_id = :userId
                FOR UPDATE
                """, Map.of("userId", userId), (rs, rowNum) -> new TravelerRow(
                rs.getLong("traveler_profile_id"), instant(rs.getTimestamp("onboarding_completed_at"))
        ));
        TravelerRow traveler = travelers.isEmpty() ? null : travelers.getFirst();

        List<SettingsRow> settingsRows = jdbc.query("""
                SELECT current_ui_mode, location_sharing_default, timezone_name
                FROM user_settings
                WHERE user_id = :userId
                FOR UPDATE
                """, Map.of("userId", userId), (rs, rowNum) -> new SettingsRow(
                rs.getString("current_ui_mode"),
                rs.getBoolean("location_sharing_default"),
                rs.getString("timezone_name")
        ));
        SettingsRow settings = settingsRows.isEmpty() ? null : settingsRows.getFirst();

        List<NotificationPreference> preferences = jdbc.query("""
                SELECT notification_category, push_enabled, in_app_enabled
                FROM notification_preferences np
                WHERE user_id = :userId
                ORDER BY %s
                FOR UPDATE
                """.formatted(NOTIFICATION_ORDER), Map.of("userId", userId), (rs, rowNum) ->
                new NotificationPreference(
                        rs.getString("notification_category"),
                        rs.getBoolean("push_enabled"),
                        rs.getBoolean("in_app_enabled")
                ));

        Restriction restriction = findLoginRestriction(userId, now);
        return Optional.of(new OnboardingState(
                user.userId(), user.nationalityCode(), user.defaultLanguageCode(), user.accountStatus(),
                traveler == null ? null : traveler.travelerProfileId(),
                traveler == null ? null : traveler.onboardingCompletedAt(),
                settings == null ? null : settings.currentUiMode(),
                settings == null ? null : settings.locationSharingDefault(),
                settings == null ? null : settings.timezoneName(),
                preferences, restriction.restricted(), restriction.endsAt()
        ));
    }

    @Override
    public Optional<Language> findLanguage(String languageCode) {
        List<Language> rows = jdbc.query("""
                SELECT language_code, is_active
                FROM languages
                WHERE language_code = :languageCode
                """, Map.of("languageCode", languageCode), (rs, rowNum) -> new Language(
                rs.getString("language_code"), rs.getBoolean("is_active")
        ));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<PermissionState> findPermissionState(long userId, Instant now) {
        List<PermissionState> rows = jdbc.query("""
                SELECT u.user_id, u.account_status,
                       ups.location_status, ups.notification_status,
                       ups.camera_status, ups.microphone_status,
                       ups.checked_at, ups.updated_at,
                       EXISTS (
                           SELECT 1
                           FROM sanctions s
                           JOIN sanction_restrictions sr ON sr.sanction_id = s.sanction_id
                           WHERE s.target_user_id = u.user_id
                             AND s.status = 'ACTIVE'
                             AND s.starts_at <= :now
                             AND (s.ends_at IS NULL OR s.ends_at > :now)
                             AND sr.restriction_scope = 'LOGIN'
                       ) AS login_restricted,
                       (
                           SELECT s.ends_at
                           FROM sanctions s
                           JOIN sanction_restrictions sr ON sr.sanction_id = s.sanction_id
                           WHERE s.target_user_id = u.user_id
                             AND s.status = 'ACTIVE'
                             AND s.starts_at <= :now
                             AND (s.ends_at IS NULL OR s.ends_at > :now)
                             AND sr.restriction_scope = 'LOGIN'
                           ORDER BY s.ends_at NULLS FIRST
                           LIMIT 1
                       ) AS restriction_ends_at
                FROM users u
                LEFT JOIN user_permission_snapshots ups ON ups.user_id = u.user_id
                WHERE u.user_id = :userId
                """, params(userId, now), (rs, rowNum) -> new PermissionState(
                rs.getLong("user_id"),
                rs.getString("account_status"),
                rs.getBoolean("login_restricted"),
                instant(rs.getTimestamp("restriction_ends_at")),
                permissionSnapshot(rs)
        ));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<PermissionState> lockPermissionState(long userId, Instant now) {
        List<UserPermissionRow> users = jdbc.query("""
                SELECT user_id, account_status
                FROM users
                WHERE user_id = :userId
                FOR UPDATE
                """, Map.of("userId", userId), (rs, rowNum) -> new UserPermissionRow(
                rs.getLong("user_id"), rs.getString("account_status")
        ));
        if (users.isEmpty()) {
            return Optional.empty();
        }

        List<PermissionSnapshot> snapshots = jdbc.query("""
                SELECT location_status, notification_status, camera_status,
                       microphone_status, checked_at, updated_at
                FROM user_permission_snapshots
                WHERE user_id = :userId
                FOR UPDATE
                """, Map.of("userId", userId), (rs, rowNum) -> permissionSnapshot(rs));
        Restriction restriction = findLoginRestriction(userId, now);
        UserPermissionRow user = users.getFirst();
        return Optional.of(new PermissionState(
                user.userId(), user.accountStatus(), restriction.restricted(), restriction.endsAt(),
                snapshots.stream().findFirst().orElse(null)
        ));
    }

    @Override
    public void saveInitialSettings(
            long userId,
            String nationalityCode,
            String defaultLanguageCode,
            boolean locationSharingDefault,
            String timezoneName,
            List<NotificationPreference> notificationPreferences,
            Instant now
    ) {
        MapSqlParameterSource userParameters = params(userId, now)
                .addValue("nationalityCode", nationalityCode)
                .addValue("defaultLanguageCode", defaultLanguageCode);
        requireOne(jdbc.update("""
                UPDATE users
                SET nationality_code = :nationalityCode,
                    default_language_code = :defaultLanguageCode,
                    version = version + 1,
                    updated_at = :now
                WHERE user_id = :userId
                """, userParameters), "user");

        MapSqlParameterSource settingsParameters = params(userId, now)
                .addValue("locationSharingDefault", locationSharingDefault)
                .addValue("timezoneName", timezoneName);
        requireOne(jdbc.update("""
                UPDATE user_settings
                SET location_sharing_default = :locationSharingDefault,
                    timezone_name = :timezoneName,
                    updated_at = :now
                WHERE user_id = :userId
                """, settingsParameters), "user settings");

        MapSqlParameterSource[] rows = notificationPreferences.stream()
                .map(preference -> params(userId, now)
                        .addValue("category", preference.notificationCategory())
                        .addValue("pushEnabled", preference.pushEnabled())
                        .addValue("inAppEnabled", preference.inAppEnabled()))
                .toArray(MapSqlParameterSource[]::new);
        int[] updates = jdbc.batchUpdate("""
                UPDATE notification_preferences
                SET push_enabled = :pushEnabled,
                    in_app_enabled = :inAppEnabled,
                    updated_at = :now
                WHERE user_id = :userId AND notification_category = :category
                """, rows);
        if (updates.length != OnboardingService.NOTIFICATION_CATEGORIES.size()) {
            throw new IllegalStateException("Unexpected notification preference update count");
        }
        for (int update : updates) {
            requireOne(update, "notification preference");
        }
    }

    @Override
    public void complete(long userId, Instant completedAt) {
        int updated = jdbc.update("""
                UPDATE traveler_profiles
                SET onboarding_completed_at = :now, updated_at = :now
                WHERE user_id = :userId AND onboarding_completed_at IS NULL
                """, params(userId, completedAt));
        requireOne(updated, "traveler profile");
    }

    @Override
    public void savePermissionSnapshot(long userId, PermissionSnapshot snapshot, Instant updatedAt) {
        MapSqlParameterSource parameters = params(userId, updatedAt)
                .addValue("locationStatus", snapshot.locationStatus())
                .addValue("notificationStatus", snapshot.notificationStatus())
                .addValue("cameraStatus", snapshot.cameraStatus())
                .addValue("microphoneStatus", snapshot.microphoneStatus())
                .addValue("checkedAt", Timestamp.from(snapshot.checkedAt()));
        int updated = jdbc.update("""
                UPDATE user_permission_snapshots
                SET location_status = :locationStatus,
                    notification_status = :notificationStatus,
                    camera_status = :cameraStatus,
                    microphone_status = :microphoneStatus,
                    checked_at = :checkedAt,
                    updated_at = :now
                WHERE user_id = :userId AND checked_at < :checkedAt
                """, parameters);
        if (updated == 1) {
            return;
        }
        int inserted = jdbc.update("""
                INSERT INTO user_permission_snapshots (
                    user_id, location_status, notification_status,
                    camera_status, microphone_status, checked_at, updated_at
                )
                SELECT :userId, :locationStatus, :notificationStatus,
                       :cameraStatus, :microphoneStatus, :checkedAt, :now
                WHERE NOT EXISTS (
                    SELECT 1 FROM user_permission_snapshots WHERE user_id = :userId
                )
                """, parameters);
        requireOne(inserted, "permission snapshot");
    }

    private Restriction findLoginRestriction(long userId, Instant now) {
        List<Instant> restrictions = jdbc.query("""
                SELECT s.ends_at
                FROM sanctions s
                JOIN sanction_restrictions sr ON sr.sanction_id = s.sanction_id
                WHERE s.target_user_id = :userId
                  AND s.status = 'ACTIVE'
                  AND s.starts_at <= :now
                  AND (s.ends_at IS NULL OR s.ends_at > :now)
                  AND sr.restriction_scope = 'LOGIN'
                ORDER BY s.ends_at NULLS FIRST
                LIMIT 1
                """, params(userId, now), (rs, rowNum) -> instant(rs.getTimestamp("ends_at")));
        return restrictions.isEmpty() ? new Restriction(false, null) : new Restriction(true, restrictions.getFirst());
    }

    private static ResultSetExtractor<Optional<OnboardingState>> stateExtractor() {
        return rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            long userId = rs.getLong("user_id");
            String nationalityCode = rs.getString("nationality_code");
            String defaultLanguageCode = rs.getString("default_language_code");
            String accountStatus = rs.getString("account_status");
            Long travelerProfileId = nullableLong(rs, "traveler_profile_id");
            Instant onboardingCompletedAt = instant(rs.getTimestamp("onboarding_completed_at"));
            String currentUiMode = rs.getString("current_ui_mode");
            Boolean locationSharingDefault = nullableBoolean(rs, "location_sharing_default");
            String timezoneName = rs.getString("timezone_name");
            boolean loginRestricted = rs.getBoolean("login_restricted");
            Instant restrictionEndsAt = instant(rs.getTimestamp("restriction_ends_at"));
            List<NotificationPreference> preferences = new ArrayList<>();
            do {
                String category = rs.getString("notification_category");
                if (category != null) {
                    preferences.add(new NotificationPreference(
                            category, rs.getBoolean("push_enabled"), rs.getBoolean("in_app_enabled")
                    ));
                }
            } while (rs.next());
            return Optional.of(new OnboardingState(
                    userId, nationalityCode, defaultLanguageCode, accountStatus,
                    travelerProfileId, onboardingCompletedAt, currentUiMode,
                    locationSharingDefault, timezoneName, preferences,
                    loginRestricted, restrictionEndsAt
            ));
        };
    }

    private static MapSqlParameterSource params(long userId, Instant now) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("now", Timestamp.from(now));
    }

    private static void requireOne(int updated, String resource) {
        if (updated != 1) {
            throw new IllegalStateException("Expected exactly one " + resource + " row to be updated");
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static PermissionSnapshot permissionSnapshot(ResultSet rs) throws SQLException {
        String locationStatus = rs.getString("location_status");
        if (locationStatus == null) {
            return null;
        }
        return new PermissionSnapshot(
                locationStatus,
                rs.getString("notification_status"),
                rs.getString("camera_status"),
                rs.getString("microphone_status"),
                instant(rs.getTimestamp("checked_at")),
                instant(rs.getTimestamp("updated_at"))
        );
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record UserRow(
            long userId,
            String nationalityCode,
            String defaultLanguageCode,
            String accountStatus
    ) {
    }

    private record UserPermissionRow(long userId, String accountStatus) {
    }

    private record TravelerRow(long travelerProfileId, Instant onboardingCompletedAt) {
    }

    private record SettingsRow(
            String currentUiMode,
            boolean locationSharingDefault,
            String timezoneName
    ) {
    }

    private record Restriction(boolean restricted, Instant endsAt) {
    }
}
