package com.allygo.allygo_api.auth.account;

import com.allygo.allygo_api.auth.account.infrastructure.persistence.JdbcAccountAuthStore;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcAccountRecoveryStoreTest {
    private static final Instant NOW = Instant.parse("2026-09-02T03:00:00Z");
    private static final Instant FUTURE = NOW.plusSeconds(600);
    private static final String PHONE = "+821012345678";

    private JdbcTemplate jdbc;
    private JdbcAccountAuthStore store;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:recovery-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcAccountAuthStore(new NamedParameterJdbcTemplate(dataSource));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        jdbc.execute("""
                CREATE TABLE users (
                    user_id BIGINT PRIMARY KEY,
                    phone_e164 VARCHAR(20) NOT NULL UNIQUE,
                    login_id VARCHAR(50) NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    account_status VARCHAR(20) NOT NULL,
                    version INTEGER NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE refresh_tokens (
                    refresh_token_id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    revoked_at TIMESTAMP,
                    revoke_reason VARCHAR(50)
                )
                """);
        jdbc.execute("""
                CREATE TABLE phone_verification_challenges (
                    challenge_id BIGINT PRIMARY KEY,
                    phone_e164 VARCHAR(20) NOT NULL,
                    purpose VARCHAR(30) NOT NULL,
                    verified_at TIMESTAMP,
                    consumed_at TIMESTAMP,
                    expires_at TIMESTAMP NOT NULL
                )
                """);

        jdbc.update("INSERT INTO users VALUES (1, ?, 'allygo', 'old-hash', 'ACTIVE', 0, ?)",
                PHONE, Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update("INSERT INTO users VALUES (2, '+821099999999', 'another', 'other-hash', 'ACTIVE', 0, ?)",
                Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update("INSERT INTO refresh_tokens VALUES (101, 1, NULL, NULL)");
        jdbc.update("INSERT INTO refresh_tokens VALUES (102, 1, ?, 'LOGOUT')", Timestamp.from(NOW.minusSeconds(10)));
        jdbc.update("INSERT INTO refresh_tokens VALUES (103, 2, NULL, NULL)");
        insertChallenge(201, PHONE, "RESET_PASSWORD", NOW.minusSeconds(30), null, FUTURE);
        insertChallenge(202, PHONE, "RESET_PASSWORD", NOW.minusSeconds(20), null, FUTURE);
        insertChallenge(203, PHONE, "RESET_PASSWORD", null, null, FUTURE);
        insertChallenge(204, PHONE, "FIND_LOGIN_ID", NOW.minusSeconds(20), null, FUTURE);
        insertChallenge(205, "+821099999999", "RESET_PASSWORD", NOW.minusSeconds(20), null, FUTURE);
    }

    @Test
    void updatesPasswordRevokesSessionsAndInvalidatesOnlyMatchingResetChallenges() {
        transactions.executeWithoutResult(status ->
                store.completePasswordReset(1L, PHONE, "new-hash", NOW)
        );

        Map<String, Object> user = jdbc.queryForMap(
                "SELECT password_hash, version, updated_at FROM users WHERE user_id = 1"
        );
        assertThat(user.get("PASSWORD_HASH")).isEqualTo("new-hash");
        assertThat(user.get("VERSION")).isEqualTo(1);
        assertThat(((Timestamp) user.get("UPDATED_AT")).toInstant()).isEqualTo(NOW);

        assertThat(refreshToken(101).get("REVOKE_REASON")).isEqualTo("PASSWORD_RESET");
        assertThat(((Timestamp) refreshToken(101).get("REVOKED_AT")).toInstant()).isEqualTo(NOW);
        assertThat(refreshToken(102).get("REVOKE_REASON")).isEqualTo("LOGOUT");
        assertThat(refreshToken(103).get("REVOKED_AT")).isNull();

        assertChallenge(201, NOW, FUTURE);
        assertChallenge(202, NOW, FUTURE);
        assertChallenge(203, null, NOW);
        assertChallenge(204, null, FUTURE);
        assertChallenge(205, null, FUTURE);
    }

    @Test
    void rollsBackPasswordUpdateWhenLaterSessionRevocationFails() {
        jdbc.execute("DROP TABLE refresh_tokens");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                store.completePasswordReset(1L, PHONE, "new-hash", NOW)
        )).isInstanceOf(RuntimeException.class);

        Map<String, Object> user = jdbc.queryForMap(
                "SELECT password_hash, version FROM users WHERE user_id = 1"
        );
        assertThat(user.get("PASSWORD_HASH")).isEqualTo("old-hash");
        assertThat(user.get("VERSION")).isEqualTo(0);
    }

    private void insertChallenge(
            long id,
            String phone,
            String purpose,
            Instant verifiedAt,
            Instant consumedAt,
            Instant expiresAt
    ) {
        jdbc.update("INSERT INTO phone_verification_challenges VALUES (?, ?, ?, ?, ?, ?)",
                id, phone, purpose, timestamp(verifiedAt), timestamp(consumedAt), Timestamp.from(expiresAt));
    }

    private Map<String, Object> refreshToken(long id) {
        return jdbc.queryForMap(
                "SELECT revoked_at, revoke_reason FROM refresh_tokens WHERE refresh_token_id = ?", id
        );
    }

    private void assertChallenge(long id, Instant consumedAt, Instant expiresAt) {
        Map<String, Object> challenge = jdbc.queryForMap(
                "SELECT consumed_at, expires_at FROM phone_verification_challenges WHERE challenge_id = ?", id
        );
        assertThat(timestampValue(challenge.get("CONSUMED_AT"))).isEqualTo(consumedAt);
        assertThat(timestampValue(challenge.get("EXPIRES_AT"))).isEqualTo(expiresAt);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant timestampValue(Object value) {
        return value == null ? null : ((Timestamp) value).toInstant();
    }
}
