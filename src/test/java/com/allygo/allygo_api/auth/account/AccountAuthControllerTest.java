package com.allygo.allygo_api.auth.account;

import com.allygo.allygo_api.auth.account.application.AccountAuthCommandService;
import com.allygo.allygo_api.auth.account.application.AccountSessionService;
import com.allygo.allygo_api.auth.account.application.command.LoginCommand;
import com.allygo.allygo_api.auth.account.application.command.FindLoginIdCommand;
import com.allygo.allygo_api.auth.account.application.command.PasswordResetCommand;
import com.allygo.allygo_api.auth.account.application.command.AccountWithdrawalCommand;
import com.allygo.allygo_api.auth.account.application.command.SignUpCommand;
import com.allygo.allygo_api.auth.account.application.result.AuthTokenResult;
import com.allygo.allygo_api.auth.account.application.result.AccountWithdrawalResult;
import com.allygo.allygo_api.auth.account.application.result.CurrentUserResult;
import com.allygo.allygo_api.auth.account.application.result.LoginResult;
import com.allygo.allygo_api.auth.account.application.result.LoginIdLookupResult;
import com.allygo.allygo_api.auth.account.application.result.NotificationPreferenceResult;
import com.allygo.allygo_api.auth.account.application.result.SignUpResult;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.auth.account.presentation.AccountAuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountAuthControllerTest {
    @Mock AccountAuthCommandService commandService;
    @Mock AccountSessionService sessionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AccountAuthController(commandService, sessionService)
        ).build();
    }

    @Test
    void returnsDocumentedSignupResponse() throws Exception {
        when(commandService.signUp(any(SignUpCommand.class))).thenReturn(signUpResult());

        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "allygo-test")
                        .content("""
                                {
                                  "verificationToken":"pvt_token",
                                  "loginId":"jiwon_2026",
                                  "password":"AllyGo!2026",
                                  "passwordConfirm":"AllyGo!2026",
                                  "name":"김지원",
                                  "nickname":"Jiwon",
                                  "nationalityCode":"KR",
                                  "policyAgreements":[{"policyDocumentId":101,"agreed":true}],
                                  "deviceId":"device-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SIGN_UP_COMPLETED"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.roleType").value("TRAVELER"))
                .andExpect(jsonPath("$.data.notificationPreferences.length()").value(7))
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(3600))
                .andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(1209600));
    }

    @Test
    void returnsDocumentedLoginResponse() throws Exception {
        when(commandService.login(any(LoginCommand.class))).thenReturn(loginResult());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"jiwon_2026","password":"AllyGo!2026","deviceId":"device-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.roleType").value("TRAVELER"))
                .andExpect(jsonPath("$.data.helperProfileId").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    void returnsRetryAfterForLoginRateLimit() throws Exception {
        when(commandService.login(any(LoginCommand.class)))
                .thenThrow(AccountAuthException.tooManyAttempts(90));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"jiwon_2026","password":"AllyGo!2026"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"))
                .andExpect(jsonPath("$.data.retryAfter").value(90));
    }

    @Test
    void returnsDocumentedLoginIdLookupResponse() throws Exception {
        when(commandService.findLoginId(any(FindLoginIdCommand.class)))
                .thenReturn(new LoginIdLookupResult("al**go"));

        mockMvc.perform(post("/api/auth/login-id/find")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationToken\":\"pvt_find\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("LOGIN_ID_FOUND"))
                .andExpect(jsonPath("$.message").value("아이디를 찾았습니다."))
                .andExpect(jsonPath("$.data.maskedLoginId").value("al**go"));
    }

    @Test
    void returnsDocumentedPasswordResetResponseWithEmptyData() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verificationToken":"pvt_reset",
                                  "newPassword":"NewAllyGo!2026",
                                  "newPasswordConfirm":"NewAllyGo!2026"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_COMPLETED"))
                .andExpect(jsonPath("$.message").value("비밀번호가 재설정되었습니다."))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void mapsRecoveryJsonErrorsToEndpointSpecificCodes() throws Exception {
        mockMvc.perform(post("/api/auth/login-id/find")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN_ID_LOOKUP_REQUEST"));

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_REQUEST"));
    }

    @Test
    void mapsMalformedJsonToEndpointSpecificCode() throws Exception {
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGN_UP_REQUEST"));
    }

    @Test
    void returnsDocumentedCurrentUserResponse() throws Exception {
        when(sessionService.currentUser("Bearer access")).thenReturn(currentUserResult());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CURRENT_USER_RETRIEVED"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.profileImageStorageKey").value("profiles/1/avatar.jpg"))
                .andExpect(jsonPath("$.data.roleType").value("BOTH"))
                .andExpect(jsonPath("$.data.activeRestrictionScopes[0]").value("CREATE_REQUEST"));
    }

    @Test
    void returnsDocumentedTokenRefreshResponse() throws Exception {
        when(sessionService.refresh("rt_old", "127.0.0.1"))
                .thenReturn(new AuthTokenResult("new-access", "rt_new", 3600, 10_000));

        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"rt_old\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TOKEN_REFRESH_COMPLETED"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt_new"))
                .andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(10_000));
    }

    @Test
    void mapsMalformedTokenRefreshJsonToDocumentedRequestError() throws Exception {
        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN_REFRESH_REQUEST"));
    }

    @Test
    void returnsDocumentedLogoutResponseWithNullData() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"rt_current\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("LOGOUT_COMPLETED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void mapsMalformedLogoutJsonToDocumentedRequestError() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOGOUT_REQUEST"));
    }

    @Test
    void returnsDocumentedAccountWithdrawalResponse() throws Exception {
        when(sessionService.withdraw(
                org.mockito.ArgumentMatchers.eq("Bearer access"),
                any(AccountWithdrawalCommand.class)
        )).thenReturn(new AccountWithdrawalResult(
                501L, "WITHDRAWN", Instant.parse("2026-09-02T03:00:00Z"),
                Instant.parse("2026-10-02T03:00:00Z")
        ));

        mockMvc.perform(post("/api/auth/account/withdrawal")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verificationToken":"pvt_withdraw",
                                  "reasonCode":"NO_LONGER_NEEDED",
                                  "reasonDetail":"더 이상 사용하지 않음",
                                  "confirmed":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWAL_COMPLETED"))
                .andExpect(jsonPath("$.data.withdrawalId").value(501))
                .andExpect(jsonPath("$.data.accountStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.withdrawnAt").value("2026-09-02T03:00:00Z"))
                .andExpect(jsonPath("$.data.retentionUntil").value("2026-10-02T03:00:00Z"));
    }

    @Test
    void returnsActiveSessionDetailsForBlockedWithdrawal() throws Exception {
        when(sessionService.withdraw(
                org.mockito.ArgumentMatchers.eq("Bearer access"),
                any(AccountWithdrawalCommand.class)
        )).thenThrow(AccountAuthException.activeHelpSession(88L, "ACTIVE"));

        mockMvc.perform(post("/api/auth/account/withdrawal")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationToken\":\"pvt_withdraw\",\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_HELP_SESSION_EXISTS"))
                .andExpect(jsonPath("$.data.activeResourceType").value("HELP_SESSION"))
                .andExpect(jsonPath("$.data.activeResourceId").value(88))
                .andExpect(jsonPath("$.data.activeResourceStatus").value("ACTIVE"));
    }

    @Test
    void mapsMalformedWithdrawalJsonToDocumentedRequestError() throws Exception {
        mockMvc.perform(post("/api/auth/account/withdrawal")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_WITHDRAWAL_REQUEST"));
    }

    private static SignUpResult signUpResult() {
        List<NotificationPreferenceResult> preferences = List.of(
                "MATCHING", "CHAT", "CALL", "REVIEW", "REPORT", "VERIFICATION", "SANCTION"
        ).stream().map(category -> new NotificationPreferenceResult(category, true, true)).toList();
        return new SignUpResult(
                1L, 2L, 3L, "jiwon_2026", "+821012345678", "김지원", "Jiwon",
                "KR", "ko", "ACTIVE", "TRAVELER", false,
                new SignUpResult.SettingsResult("TRAVELER", false, "UTC"), preferences,
                Instant.parse("2026-09-01T03:00:00Z"),
                new AuthTokenResult("access", "refresh", 3600, 1209600)
        );
    }

    private static LoginResult loginResult() {
        return new LoginResult(
                1L, 2L, null, "jiwon_2026", "김지원", "Jiwon", "+821012345678",
                "KR", "ko", "ACTIVE", "TRAVELER", null, null, "TRAVELER", true,
                null, null, Instant.parse("2026-09-01T03:00:00Z"),
                new AuthTokenResult("access", "refresh", 3600, 1209600)
        );
    }

    private static CurrentUserResult currentUserResult() {
        return new CurrentUserResult(
                1L, 2L, 21L, "jiwon_2026", "김지원", "Jiwon", "+821012345678",
                "KR", "ko", "ACTIVE", "BOTH", "APPROVED", "UNAVAILABLE", "TRAVELER",
                true, Instant.parse("2026-09-01T02:00:00Z"), "profiles/1/avatar.jpg",
                "https://signed.example/avatar", Instant.parse("2026-09-01T03:05:00Z"),
                Instant.parse("2026-09-01T03:00:00Z"), Instant.parse("2026-08-01T03:00:00Z"),
                List.of("CREATE_REQUEST")
        );
    }
}
