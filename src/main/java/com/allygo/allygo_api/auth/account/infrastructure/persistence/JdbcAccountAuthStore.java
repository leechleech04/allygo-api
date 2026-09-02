package com.allygo.allygo_api.auth.account.infrastructure.persistence;

import com.allygo.allygo_api.auth.account.application.port.AccountAuthStore;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException.Reason;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class JdbcAccountAuthStore implements AccountAuthStore {
    private static final List<String> NOTIFICATION_CATEGORIES = List.of(
            "MATCHING", "CHAT", "CALL", "REVIEW", "REPORT", "VERIFICATION", "SANCTION"
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAccountAuthStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PhoneChallenge> findPhoneChallenge(long challengeId) {
        return queryPhoneChallenges("""
                SELECT challenge_id, phone_e164, purpose, verified_at, consumed_at, created_at
                FROM phone_verification_challenges
                WHERE challenge_id = :challengeId
                """, Map.of("challengeId", challengeId)).stream().findFirst();
    }

    @Override
    public Optional<PhoneChallenge> lockPhoneChallenge(long challengeId) {
        return queryPhoneChallenges("""
                SELECT challenge_id, phone_e164, purpose, verified_at, consumed_at, created_at
                FROM phone_verification_challenges
                WHERE challenge_id = :challengeId
                FOR UPDATE
                """, Map.of("challengeId", challengeId)).stream().findFirst();
    }

    @Override
    public List<PhoneChallenge> lockPhoneChallenges(String phoneE164, VerificationPurpose purpose) {
        return queryPhoneChallenges("""
                SELECT challenge_id, phone_e164, purpose, verified_at, consumed_at, created_at
                FROM phone_verification_challenges
                WHERE phone_e164 = :phoneE164 AND purpose = :purpose
                ORDER BY challenge_id
                FOR UPDATE
                """, Map.of("phoneE164", phoneE164, "purpose", purpose.name()));
    }

    private List<PhoneChallenge> queryPhoneChallenges(String sql, Map<String, ?> parameters) {
        return jdbc.query(sql, parameters, (rs, rowNum) -> new PhoneChallenge(
                rs.getLong("challenge_id"),
                rs.getString("phone_e164"),
                VerificationPurpose.valueOf(rs.getString("purpose")),
                instant(rs.getTimestamp("verified_at")),
                instant(rs.getTimestamp("consumed_at")),
                instant(rs.getTimestamp("created_at"))
        ));
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return exists("SELECT EXISTS (SELECT 1 FROM users WHERE lower(login_id) = lower(:value))", loginId);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return exists("SELECT EXISTS (SELECT 1 FROM users WHERE lower(nickname) = lower(:value))", nickname);
    }

    @Override
    public boolean existsByPhone(String phoneE164) {
        return exists("SELECT EXISTS (SELECT 1 FROM users WHERE phone_e164 = :value)", phoneE164);
    }

    @Override
    public String resolveActiveLanguage(String preferredLanguageCode) {
        List<String> preferred = jdbc.queryForList("""
                SELECT language_code FROM languages
                WHERE language_code = :languageCode AND is_active = TRUE
                """, Map.of("languageCode", preferredLanguageCode), String.class);
        if (!preferred.isEmpty()) {
            return preferred.getFirst();
        }
        List<String> fallback = jdbc.queryForList("""
                SELECT language_code FROM languages
                WHERE language_code = 'en' AND is_active = TRUE
                """, Map.of(), String.class);
        if (fallback.isEmpty()) {
            throw new IllegalStateException("Active fallback language 'en' is not configured");
        }
        return fallback.getFirst();
    }

    @Override
    public List<PolicyDocument> lockPolicyDocuments(Set<Long> policyDocumentIds) {
        return jdbc.query("""
                SELECT policy_document_id, language_code, is_required, effective_at, retired_at
                FROM policy_documents
                WHERE policy_document_id IN (:ids)
                ORDER BY policy_document_id
                FOR UPDATE
                """, Map.of("ids", policyDocumentIds), (rs, rowNum) -> new PolicyDocument(
                rs.getLong("policy_document_id"),
                rs.getString("language_code"),
                rs.getBoolean("is_required"),
                instant(rs.getTimestamp("effective_at")),
                instant(rs.getTimestamp("retired_at"))
        ));
    }

    @Override
    public Set<Long> findEffectiveRequiredPolicyIds(String languageCode, Instant now) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT policy_document_id
                FROM policy_documents
                WHERE language_code = :languageCode
                  AND is_required = TRUE
                  AND effective_at <= :now
                  AND (retired_at IS NULL OR retired_at > :now)
                """, new MapSqlParameterSource()
                .addValue("languageCode", languageCode)
                .addValue("now", Timestamp.from(now)), Long.class));
    }

    @Override
    public CreatedAccount createAccount(NewAccount account) {
        try {
            MapSqlParameterSource userParams = new MapSqlParameterSource()
                    .addValue("loginId", account.loginId())
                    .addValue("phone", account.phoneE164())
                    .addValue("passwordHash", account.passwordHash())
                    .addValue("name", account.name())
                    .addValue("nickname", account.nickname())
                    .addValue("nationality", account.nationalityCode())
                    .addValue("language", account.defaultLanguageCode())
                    .addValue("verifiedAt", Timestamp.from(account.phoneVerifiedAt()))
                    .addValue("now", Timestamp.from(account.now()));
            Long userId = jdbc.queryForObject("""
                    INSERT INTO users (
                        login_id, phone_e164, password_hash, name, nickname, nationality_code,
                        default_language_code, account_status, phone_verified_at, created_at, updated_at
                    ) VALUES (
                        :loginId, :phone, :passwordHash, :name, :nickname, :nationality,
                        :language, 'ACTIVE', :verifiedAt, :now, :now
                    ) RETURNING user_id
                    """, userParams, Long.class);

            Long travelerProfileId = jdbc.queryForObject("""
                    INSERT INTO traveler_profiles (user_id, onboarding_completed_at, created_at, updated_at)
                    VALUES (:userId, NULL, :now, :now)
                    RETURNING traveler_profile_id
                    """, params(userId, account.now()), Long.class);

            jdbc.update("""
                    INSERT INTO user_settings (
                        user_id, current_ui_mode, location_sharing_default, timezone_name, created_at, updated_at
                    ) VALUES (:userId, 'TRAVELER', FALSE, 'UTC', :now, :now)
                    """, params(userId, account.now()));

            MapSqlParameterSource[] notificationRows = NOTIFICATION_CATEGORIES.stream()
                    .map(category -> params(userId, account.now()).addValue("category", category))
                    .toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    INSERT INTO notification_preferences (
                        user_id, notification_category, push_enabled, in_app_enabled, updated_at
                    ) VALUES (:userId, :category, TRUE, TRUE, :now)
                    """, notificationRows);

            MapSqlParameterSource verificationParams = params(userId, account.now())
                    .addValue("consentedAt", Timestamp.from(account.phoneVerifiedAt()))
                    .addValue("challengeId", account.challengeId());
            Long verificationId = jdbc.queryForObject("""
                    INSERT INTO verifications (
                        user_id, verification_type, status, evidence_metadata, consented_at,
                        submitted_at, reviewed_at, approved_at, created_at, updated_at
                    ) VALUES (
                        :userId, 'PHONE', 'APPROVED',
                        CAST('{"challengeId":' || CAST(:challengeId AS varchar) || '}' AS jsonb),
                        :consentedAt, :now, :now, :now, :now, :now
                    ) RETURNING verification_id
                    """, verificationParams, Long.class);

            MapSqlParameterSource[] agreementRows = account.policyAgreements().stream()
                    .map(agreement -> new MapSqlParameterSource()
                            .addValue("userId", userId)
                            .addValue("policyId", agreement.policyDocumentId())
                            .addValue("agreed", agreement.agreed())
                            .addValue("now", Timestamp.from(account.now()))
                            .addValue("ip", account.ipAddress())
                            .addValue("userAgent", account.userAgent()))
                    .toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    INSERT INTO user_policy_agreements (
                        user_id, policy_document_id, agreed, agreed_at, ip_address, user_agent
                    ) VALUES (:userId, :policyId, :agreed, :now, CAST(:ip AS inet), :userAgent)
                    """, agreementRows);

            return new CreatedAccount(userId, travelerProfileId, verificationId, account.now());
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraintViolation(exception);
        }
    }

    @Override
    public void saveRefreshToken(long userId, StoredRefreshToken token) {
        MapSqlParameterSource values = params(userId, token.createdAt())
                .addValue("familyId", token.tokenFamilyId())
                .addValue("tokenHash", token.tokenHash())
                .addValue("deviceId", token.deviceId())
                .addValue("expiresAt", Timestamp.from(token.expiresAt()));
        jdbc.update("""
                INSERT INTO refresh_tokens (
                    user_id, token_family_id, token_hash, device_id, expires_at, created_at
                ) VALUES (:userId, :familyId, :tokenHash, :deviceId, :expiresAt, :now)
                """, values);
    }

    @Override
    public void consumePhoneChallenge(long challengeId, Instant consumedAt) {
        int updated = jdbc.update("""
                UPDATE phone_verification_challenges
                SET consumed_at = :consumedAt
                WHERE challenge_id = :challengeId AND consumed_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("challengeId", challengeId)
                .addValue("consumedAt", Timestamp.from(consumedAt)));
        if (updated != 1) {
            throw AccountAuthException.of(
                    Reason.PHONE_VERIFICATION_ALREADY_CONSUMED,
                    "이미 사용된 휴대폰 인증 결과입니다."
            );
        }
    }

    @Override
    public Optional<RecoveryAccount> findRecoveryAccountByPhone(String phoneE164) {
        return queryRecoveryAccount("""
                SELECT user_id, login_id, account_status
                FROM users
                WHERE phone_e164 = :phoneE164
                """, phoneE164);
    }

    @Override
    public Optional<RecoveryAccount> lockRecoveryAccountByPhone(String phoneE164) {
        return queryRecoveryAccount("""
                SELECT user_id, login_id, account_status
                FROM users
                WHERE phone_e164 = :phoneE164
                FOR UPDATE
                """, phoneE164);
    }

    private Optional<RecoveryAccount> queryRecoveryAccount(String sql, String phoneE164) {
        List<RecoveryAccount> rows = jdbc.query(sql, Map.of("phoneE164", phoneE164), (rs, rowNum) ->
                new RecoveryAccount(
                        rs.getLong("user_id"),
                        rs.getString("login_id"),
                        rs.getString("account_status")
                ));
        return rows.stream().findFirst();
    }

    @Override
    public void completePasswordReset(long userId, String phoneE164, String passwordHash, Instant resetAt) {
        MapSqlParameterSource values = params(userId, resetAt)
                .addValue("phoneE164", phoneE164)
                .addValue("passwordHash", passwordHash);
        int userUpdated = jdbc.update("""
                UPDATE users
                SET password_hash = :passwordHash, version = version + 1, updated_at = :now
                WHERE user_id = :userId AND phone_e164 = :phoneE164 AND account_status <> 'WITHDRAWN'
                """, values);
        if (userUpdated != 1) {
            throw new IllegalStateException("Account changed while password reset was locked");
        }

        jdbc.update("""
                UPDATE refresh_tokens
                SET revoked_at = :now, revoke_reason = 'PASSWORD_RESET'
                WHERE user_id = :userId AND revoked_at IS NULL
                """, values);
        jdbc.update("""
                UPDATE phone_verification_challenges
                SET consumed_at = CASE
                        WHEN verified_at IS NOT NULL AND consumed_at IS NULL THEN :now
                        ELSE consumed_at
                    END,
                    expires_at = CASE
                        WHEN verified_at IS NULL THEN :now
                        ELSE expires_at
                    END
                WHERE phone_e164 = :phoneE164 AND purpose = 'RESET_PASSWORD'
                """, values);
    }

    @Override
    public Optional<LoginAccount> findLoginAccount(String normalizedLoginId) {
        return queryLoginAccount("""
                SELECT u.user_id, tp.traveler_profile_id, hp.helper_profile_id,
                       u.login_id, u.password_hash, u.name, u.nickname, u.phone_e164,
                       u.nationality_code, u.default_language_code, u.account_status,
                       hp.approval_status, hp.availability_status, us.current_ui_mode,
                       (tp.onboarding_completed_at IS NOT NULL) AS onboarding_completed,
                       u.profile_image_storage_key
                FROM users u
                JOIN traveler_profiles tp ON tp.user_id = u.user_id
                JOIN user_settings us ON us.user_id = u.user_id
                LEFT JOIN helper_profiles hp ON hp.user_id = u.user_id
                WHERE lower(u.login_id) = lower(:loginId)
                """, Map.of("loginId", normalizedLoginId));
    }

    @Override
    public Optional<LoginAccount> lockLoginAccount(long userId) {
        return queryLoginAccount("""
                SELECT u.user_id, tp.traveler_profile_id, hp.helper_profile_id,
                       u.login_id, u.password_hash, u.name, u.nickname, u.phone_e164,
                       u.nationality_code, u.default_language_code, u.account_status,
                       hp.approval_status, hp.availability_status, us.current_ui_mode,
                       (tp.onboarding_completed_at IS NOT NULL) AS onboarding_completed,
                       u.profile_image_storage_key
                FROM users u
                JOIN traveler_profiles tp ON tp.user_id = u.user_id
                JOIN user_settings us ON us.user_id = u.user_id
                LEFT JOIN helper_profiles hp ON hp.user_id = u.user_id
                WHERE u.user_id = :userId
                FOR UPDATE OF u, tp, us
                """, Map.of("userId", userId));
    }

    private Optional<LoginAccount> queryLoginAccount(String sql, Map<String, ?> parameters) {
        List<LoginAccount> rows = jdbc.query(sql, parameters, (rs, rowNum) -> new LoginAccount(
                rs.getLong("user_id"), rs.getLong("traveler_profile_id"),
                nullableLong(rs.getObject("helper_profile_id")), rs.getString("login_id"),
                rs.getString("password_hash"), rs.getString("name"), rs.getString("nickname"),
                rs.getString("phone_e164"), rs.getString("nationality_code"),
                rs.getString("default_language_code"), rs.getString("account_status"),
                rs.getString("approval_status"), rs.getString("availability_status"),
                rs.getString("current_ui_mode"), rs.getBoolean("onboarding_completed"),
                rs.getString("profile_image_storage_key")
        ));
        return rows.stream().findFirst();
    }

    @Override
    public LoginRestriction findLoginRestriction(long userId, Instant now) {
        List<LoginRestriction> rows = jdbc.query("""
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
                """, params(userId, now), (rs, rowNum) -> new LoginRestriction(
                true, instant(rs.getTimestamp("ends_at"))
        ));
        return rows.isEmpty() ? LoginRestriction.none() : rows.getFirst();
    }

    @Override
    public boolean hasHelperActivityRestriction(long userId, Instant now) {
        Boolean value = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM sanctions s
                    JOIN sanction_restrictions sr ON sr.sanction_id = s.sanction_id
                    WHERE s.target_user_id = :userId
                      AND s.status = 'ACTIVE'
                      AND s.starts_at <= :now
                      AND (s.ends_at IS NULL OR s.ends_at > :now)
                      AND sr.restriction_scope = 'HELPER_ACTIVITY'
                )
                """, params(userId, now), Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public boolean hasRequiredHelperVerification(long userId, Instant now) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT verification_type)
                FROM verifications
                WHERE user_id = :userId
                  AND verification_type IN ('PHONE', 'ID_DOCUMENT', 'FACE', 'LOCAL_PRESENCE')
                  AND status = 'APPROVED'
                  AND (expires_at IS NULL OR expires_at > :now)
                """, params(userId, now), Integer.class);
        return count != null && count == 4;
    }

    @Override
    public void changeCurrentUiMode(long userId, String currentUiMode, Instant now) {
        jdbc.update("""
                UPDATE user_settings
                SET current_ui_mode = :mode, updated_at = :now
                WHERE user_id = :userId
                """, params(userId, now).addValue("mode", currentUiMode));
    }

    @Override
    public void completeLogin(long userId, StoredRefreshToken refreshToken, Instant loginAt) {
        jdbc.update("""
                UPDATE users SET last_login_at = :now, updated_at = :now WHERE user_id = :userId
                """, params(userId, loginAt));
        saveRefreshToken(userId, refreshToken);
    }

    @Override
    public Optional<CurrentUserAccount> findCurrentUser(long userId, Instant now) {
        List<CurrentUserAccount> rows = jdbc.query("""
                SELECT u.user_id, tp.traveler_profile_id, hp.helper_profile_id,
                       u.login_id, u.name, u.nickname, u.phone_e164, u.nationality_code,
                       u.default_language_code, u.account_status, hp.approval_status,
                       hp.availability_status, us.current_ui_mode,
                       (tp.onboarding_completed_at IS NOT NULL) AS onboarding_completed,
                       u.phone_verified_at, u.profile_image_storage_key, u.last_login_at, u.created_at,
                       (SELECT COUNT(DISTINCT v.verification_type) = 4
                          FROM verifications v
                         WHERE v.user_id = u.user_id
                           AND v.verification_type IN ('PHONE', 'ID_DOCUMENT', 'FACE', 'LOCAL_PRESENCE')
                           AND v.status = 'APPROVED'
                           AND (v.expires_at IS NULL OR v.expires_at > :now)
                       ) AS required_helper_verifications_valid,
                       COALESCE((
                           SELECT string_agg(DISTINCT sr.restriction_scope, ',' ORDER BY sr.restriction_scope)
                           FROM sanctions s
                           JOIN sanction_restrictions sr ON sr.sanction_id = s.sanction_id
                           WHERE s.target_user_id = u.user_id
                             AND s.status = 'ACTIVE'
                             AND s.starts_at <= :now
                             AND (s.ends_at IS NULL OR s.ends_at > :now)
                       ), '') AS active_restriction_scopes,
                       (SELECT s.ends_at
                          FROM sanctions s
                          JOIN sanction_restrictions sr ON sr.sanction_id = s.sanction_id
                         WHERE s.target_user_id = u.user_id
                           AND s.status = 'ACTIVE'
                           AND s.starts_at <= :now
                           AND (s.ends_at IS NULL OR s.ends_at > :now)
                           AND sr.restriction_scope = 'LOGIN'
                         ORDER BY s.ends_at NULLS FIRST
                         LIMIT 1
                       ) AS login_restriction_ends_at
                FROM users u
                LEFT JOIN traveler_profiles tp ON tp.user_id = u.user_id
                LEFT JOIN user_settings us ON us.user_id = u.user_id
                LEFT JOIN helper_profiles hp ON hp.user_id = u.user_id
                WHERE u.user_id = :userId
                """, params(userId, now), (rs, rowNum) -> new CurrentUserAccount(
                rs.getLong("user_id"),
                nullableLong(rs.getObject("traveler_profile_id")),
                nullableLong(rs.getObject("helper_profile_id")),
                rs.getString("login_id"),
                rs.getString("name"),
                rs.getString("nickname"),
                rs.getString("phone_e164"),
                rs.getString("nationality_code"),
                rs.getString("default_language_code"),
                rs.getString("account_status"),
                rs.getString("approval_status"),
                rs.getString("availability_status"),
                rs.getString("current_ui_mode"),
                rs.getBoolean("onboarding_completed"),
                instant(rs.getTimestamp("phone_verified_at")),
                rs.getString("profile_image_storage_key"),
                instant(rs.getTimestamp("last_login_at")),
                instant(rs.getTimestamp("created_at")),
                rs.getBoolean("required_helper_verifications_valid"),
                restrictionScopes(rs.getString("active_restriction_scopes")),
                instant(rs.getTimestamp("login_restriction_ends_at"))
        ));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<RefreshToken> findRefreshToken(String tokenHash) {
        return queryRefreshToken("""
                SELECT refresh_token_id, user_id, token_family_id, token_hash, device_id,
                       expires_at, last_used_at, revoked_at, revoke_reason, created_at
                FROM refresh_tokens
                WHERE token_hash = :tokenHash
                """, tokenHash);
    }

    @Override
    public void lockRefreshTokenFamily(UUID tokenFamilyId) {
        lockAdvisory("refresh-token-family:" + tokenFamilyId);
        jdbc.queryForList("""
                SELECT refresh_token_id
                FROM refresh_tokens
                WHERE token_family_id = :familyId
                ORDER BY refresh_token_id
                FOR UPDATE
                """, Map.of("familyId", tokenFamilyId), Long.class);
    }

    @Override
    public Optional<RefreshToken> lockRefreshToken(String tokenHash) {
        return queryRefreshToken("""
                SELECT refresh_token_id, user_id, token_family_id, token_hash, device_id,
                       expires_at, last_used_at, revoked_at, revoke_reason, created_at
                FROM refresh_tokens
                WHERE token_hash = :tokenHash
                FOR UPDATE
                """, tokenHash);
    }

    private Optional<RefreshToken> queryRefreshToken(String sql, String tokenHash) {
        List<RefreshToken> rows = jdbc.query(sql, Map.of("tokenHash", tokenHash), (rs, rowNum) -> new RefreshToken(
                rs.getLong("refresh_token_id"),
                rs.getLong("user_id"),
                rs.getObject("token_family_id", UUID.class),
                rs.getString("token_hash"),
                rs.getString("device_id"),
                instant(rs.getTimestamp("expires_at")),
                instant(rs.getTimestamp("last_used_at")),
                instant(rs.getTimestamp("revoked_at")),
                rs.getString("revoke_reason"),
                instant(rs.getTimestamp("created_at"))
        ));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<String> lockAccountStatus(long userId) {
        List<String> rows = jdbc.queryForList("""
                SELECT account_status
                FROM users
                WHERE user_id = :userId
                FOR UPDATE
                """, Map.of("userId", userId), String.class);
        return rows.stream().findFirst();
    }

    @Override
    public void rotateRefreshToken(
            long userId,
            long refreshTokenId,
            StoredRefreshToken token,
            Instant rotatedAt
    ) {
        int updated = jdbc.update("""
                UPDATE refresh_tokens
                SET last_used_at = :now, revoked_at = :now, revoke_reason = 'ROTATED'
                WHERE refresh_token_id = :refreshTokenId AND revoked_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("refreshTokenId", refreshTokenId)
                .addValue("now", Timestamp.from(rotatedAt)));
        if (updated != 1) {
            throw new IllegalStateException("Refresh token changed while its family was locked");
        }
        saveRefreshToken(userId, token);
    }

    @Override
    public void revokeActiveRefreshTokenFamily(UUID tokenFamilyId, Instant revokedAt, String reason) {
        jdbc.update("""
                UPDATE refresh_tokens
                SET revoked_at = :now, revoke_reason = :reason
                WHERE token_family_id = :familyId AND revoked_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("familyId", tokenFamilyId)
                .addValue("now", Timestamp.from(revokedAt))
                .addValue("reason", reason));
    }

    @Override
    public void lockAccountLifecycle(long userId) {
        lockAdvisory("account-lifecycle:" + userId);
    }

    @Override
    public Optional<WithdrawalAccount> lockWithdrawalAccount(long userId) {
        List<WithdrawalAccount> rows = jdbc.query("""
                SELECT user_id, phone_e164, account_status
                FROM users
                WHERE user_id = :userId
                FOR UPDATE
                """, Map.of("userId", userId), (rs, rowNum) -> new WithdrawalAccount(
                rs.getLong("user_id"), rs.getString("phone_e164"), rs.getString("account_status")
        ));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<ActiveResource> lockActiveHelpRequest(long userId) {
        if (!tableExists("help_requests")) {
            return Optional.empty();
        }
        List<ActiveResource> rows = jdbc.query("""
                SELECT request_id, status
                FROM help_requests
                WHERE traveler_user_id = :userId
                  AND status IN ('DRAFT', 'ANALYZING', 'READY', 'MATCHING', 'MATCHED', 'IN_PROGRESS')
                ORDER BY request_id
                LIMIT 1
                FOR UPDATE
                """, Map.of("userId", userId), (rs, rowNum) -> new ActiveResource(
                rs.getLong("request_id"), rs.getString("status")
        ));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<ActiveResource> lockActiveHelpSession(long userId) {
        if (!tableExists("help_sessions")) {
            return Optional.empty();
        }
        List<ActiveResource> rows = jdbc.query("""
                SELECT session_id, status
                FROM help_sessions
                WHERE (traveler_user_id = :userId OR helper_user_id = :userId)
                  AND status IN ('WAITING', 'ACTIVE')
                ORDER BY session_id
                LIMIT 1
                FOR UPDATE
                """, Map.of("userId", userId), (rs, rowNum) -> new ActiveResource(
                rs.getLong("session_id"), rs.getString("status")
        ));
        return rows.stream().findFirst();
    }

    @Override
    public Instant findWithdrawalRetentionUntil(long userId, Instant withdrawnAt) {
        List<Instant> candidates = new ArrayList<>();
        Timestamp verificationRetention = jdbc.queryForObject("""
                SELECT MAX(
                    CASE
                        WHEN status = 'APPROVED' THEN :now + INTERVAL '30 days'
                        WHEN status IN ('REJECTED', 'CANCELED')
                            THEN COALESCE(reviewed_at, updated_at) + INTERVAL '30 days'
                        ELSE NULL
                    END
                )
                FROM verifications
                WHERE user_id = :userId
                """, params(userId, withdrawnAt), Timestamp.class);
        addCandidate(candidates, verificationRetention);

        if (tableExists("help_sessions")) {
            Timestamp chatRetention = jdbc.queryForObject("""
                    SELECT MAX(ended_at + INTERVAL '1 year')
                    FROM help_sessions
                    WHERE (traveler_user_id = :userId OR helper_user_id = :userId)
                      AND status IN ('COMPLETED', 'CANCELED', 'DISPUTED')
                    """, Map.of("userId", userId), Timestamp.class);
            addCandidate(candidates, chatRetention);
        }

        if (tableExists("reports")) {
            Boolean unresolvedReport = jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM reports
                        WHERE (reporter_user_id = :userId OR reported_user_id = :userId)
                          AND status NOT IN ('RESOLVED', 'REJECTED')
                    )
                    """, Map.of("userId", userId), Boolean.class);
            if (Boolean.TRUE.equals(unresolvedReport)) {
                return null;
            }
            Timestamp reportRetention = jdbc.queryForObject("""
                    SELECT MAX(resolved_at + INTERVAL '3 years')
                    FROM reports
                    WHERE (reporter_user_id = :userId OR reported_user_id = :userId)
                      AND status IN ('RESOLVED', 'REJECTED')
                    """, Map.of("userId", userId), Timestamp.class);
            addCandidate(candidates, reportRetention);
        }

        return candidates.stream().max(Instant::compareTo).orElse(null);
    }

    @Override
    public CreatedWithdrawal completeWithdrawal(NewWithdrawal withdrawal) {
        MapSqlParameterSource values = params(withdrawal.userId(), withdrawal.requestedAt())
                .addValue("challengeId", withdrawal.challengeId())
                .addValue("reasonCode", withdrawal.reasonCode())
                .addValue("reasonDetail", withdrawal.reasonDetail())
                .addValue("retentionUntil", timestamp(withdrawal.retentionUntil()));
        Long withdrawalId = jdbc.queryForObject("""
                INSERT INTO account_withdrawals (
                    user_id, reason_code, reason_detail, requested_at, completed_at, retention_until
                ) VALUES (
                    :userId, :reasonCode, :reasonDetail, :now, :now, :retentionUntil
                )
                RETURNING withdrawal_id
                """, values, Long.class);

        int accountUpdated = jdbc.update("""
                UPDATE users
                SET account_status = 'WITHDRAWN', withdrawn_at = :now,
                    version = version + 1, updated_at = :now
                WHERE user_id = :userId AND account_status = 'ACTIVE'
                """, values);
        if (accountUpdated != 1) {
            throw new IllegalStateException("Account status changed while withdrawal was locked");
        }

        jdbc.update("""
                UPDATE helper_profiles
                SET availability_status = 'UNAVAILABLE', availability_changed_at = :now,
                    version = version + 1, updated_at = :now
                WHERE user_id = :userId
                """, values);
        jdbc.update("""
                UPDATE refresh_tokens
                SET revoked_at = :now, revoke_reason = 'ACCOUNT_WITHDRAWAL'
                WHERE user_id = :userId AND revoked_at IS NULL
                """, values);
        consumePhoneChallenge(withdrawal.challengeId(), withdrawal.requestedAt());

        return new CreatedWithdrawal(
                Objects.requireNonNull(withdrawalId), withdrawal.requestedAt(), withdrawal.retentionUntil()
        );
    }

    private void lockAdvisory(String lockKey) {
        jdbc.query("""
                SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))
                """, Map.of("lockKey", lockKey), rs -> null);
    }

    private boolean tableExists(String tableName) {
        Boolean value = jdbc.queryForObject(
                "SELECT to_regclass(:tableName) IS NOT NULL",
                Map.of("tableName", tableName),
                Boolean.class
        );
        return Boolean.TRUE.equals(value);
    }

    private static void addCandidate(List<Instant> candidates, Timestamp timestamp) {
        if (timestamp != null) {
            candidates.add(timestamp.toInstant());
        }
    }

    private boolean exists(String sql, String value) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Map.of("value", value), Boolean.class));
    }

    private static MapSqlParameterSource params(long userId, Instant now) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("now", Timestamp.from(now));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Set<String> restrictionScopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Set.of(value.split(","));
    }

    private static RuntimeException translateConstraintViolation(DataIntegrityViolationException exception) {
        String message = Optional.ofNullable(exception.getMostSpecificCause().getMessage())
                .orElse("").toLowerCase(Locale.ROOT);
        if (message.contains("login_id") || message.contains("ux_users_login_id_lower")) {
            return AccountAuthException.of(Reason.LOGIN_ID_ALREADY_EXISTS, "이미 사용 중인 로그인 아이디입니다.");
        }
        if (message.contains("nickname") || message.contains("ux_users_nickname_lower")) {
            return AccountAuthException.of(Reason.NICKNAME_ALREADY_EXISTS, "이미 사용 중인 닉네임입니다.");
        }
        if (message.contains("phone_e164") || message.contains("users_phone_e164")) {
            return AccountAuthException.of(
                    Reason.PHONE_NUMBER_ALREADY_REGISTERED,
                    "이미 가입된 휴대폰 번호입니다."
            );
        }
        return exception;
    }
}
