package com.allygo.allygo_api.onboarding;

import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.onboarding.application.OnboardingService;
import com.allygo.allygo_api.onboarding.application.command.SaveInitialSettingsCommand;
import com.allygo.allygo_api.onboarding.application.command.SavePermissionStatusCommand;
import com.allygo.allygo_api.onboarding.application.result.InitialSettingsResult;
import com.allygo.allygo_api.onboarding.application.result.OnboardingCompletionResult;
import com.allygo.allygo_api.onboarding.application.result.PermissionStatusResult;
import com.allygo.allygo_api.onboarding.domain.OnboardingException;
import com.allygo.allygo_api.onboarding.presentation.OnboardingController;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-04T03:00:00Z");

    @Mock OnboardingService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingController(service)).build();
    }

    @Test
    void returnsDocumentedInitialSettingsResponse() throws Exception {
        when(service.getInitialSettings("Bearer access")).thenReturn(initialSettings());

        mockMvc.perform(get("/api/onboarding/initial-settings")
                        .header("Authorization", "Bearer access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("INITIAL_SETTINGS_RETRIEVED"))
                .andExpect(jsonPath("$.data.nationalityCode").value("KR"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.onboardingCompletedAt").doesNotExist())
                .andExpect(jsonPath("$.data.settings.currentUiMode").value("TRAVELER"))
                .andExpect(jsonPath("$.data.notificationPreferences.length()").value(7))
                .andExpect(jsonPath("$.data.notificationPreferences[6].notificationCategory")
                        .value("SANCTION"));
    }

    @Test
    void returnsDocumentedSavedSettingsResponse() throws Exception {
        when(service.saveInitialSettings(
                org.mockito.ArgumentMatchers.eq("Bearer access"),
                any(SaveInitialSettingsCommand.class)
        )).thenReturn(initialSettings());

        mockMvc.perform(put("/api/onboarding/initial-settings")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INITIAL_SETTINGS_SAVED"))
                .andExpect(jsonPath("$.message").value("사용자 초기 설정이 저장되었습니다."))
                .andExpect(jsonPath("$.data.settings.timezoneName").value("Asia/Seoul"));
    }

    @Test
    void returnsDocumentedCompletionResponse() throws Exception {
        when(service.complete("Bearer access")).thenReturn(new OnboardingCompletionResult(
                1L, 2L, "TRAVELER", "TRAVELER", true, COMPLETED_AT
        ));

        mockMvc.perform(post("/api/onboarding/complete")
                        .header("Authorization", "Bearer access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ONBOARDING_COMPLETED"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.travelerProfileId").value(2))
                .andExpect(jsonPath("$.data.roleType").value("TRAVELER"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.onboardingCompletedAt").value("2026-09-04T03:00:00Z"));
    }

    @Test
    void mapsMalformedJsonToInitialSettingsRequestError() throws Exception {
        mockMvc.perform(put("/api/onboarding/initial-settings")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INITIAL_SETTINGS_REQUEST"));
    }

    @Test
    void mapsNonBooleanNotificationFieldsToNotificationPreferencesError() throws Exception {
        when(service.saveInitialSettings(
                org.mockito.ArgumentMatchers.eq("Bearer access"),
                any(SaveInitialSettingsCommand.class)
        )).thenAnswer(invocation -> {
            SaveInitialSettingsCommand command = invocation.getArgument(1);
            if (command.notificationPreferences().getFirst().pushEnabled() == null) {
                throw OnboardingException.of(
                        OnboardingException.Reason.INVALID_NOTIFICATION_PREFERENCES,
                        "알림 설정 형식이 올바르지 않습니다."
                );
            }
            return initialSettings();
        });

        mockMvc.perform(put("/api/onboarding/initial-settings")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace(
                                "\"pushEnabled\":true", "\"pushEnabled\":\"true\""
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NOTIFICATION_PREFERENCES"));
    }

    @Test
    void mapsLoginRestrictionWithNullableEndTime() throws Exception {
        when(service.getInitialSettings("Bearer access"))
                .thenThrow(OnboardingException.loginRestricted(null));

        mockMvc.perform(get("/api/onboarding/initial-settings")
                        .header("Authorization", "Bearer access"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOGIN_RESTRICTED"))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.restrictionEndsAt").doesNotExist());
    }

    @Test
    void mapsMissingOrInvalidAccessTokenToUnauthorized() throws Exception {
        when(service.complete(null)).thenThrow(AccountAuthException.unauthorized());

        mockMvc.perform(post("/api/onboarding/complete"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void returnsAnUnstoredPermissionSnapshotAsNull() throws Exception {
        when(service.getPermissionStatus("Bearer access"))
                .thenReturn(new PermissionStatusResult(null));

        mockMvc.perform(get("/api/onboarding/permissions")
                        .header("Authorization", "Bearer access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERMISSION_STATUS_RETRIEVED"))
                .andExpect(jsonPath("$.data.permissionSnapshot").doesNotExist());
    }

    @Test
    void returnsTheDocumentedSavedPermissionSnapshot() throws Exception {
        when(service.savePermissionStatus(
                org.mockito.ArgumentMatchers.eq("Bearer access"),
                any(SavePermissionStatusCommand.class)
        )).thenReturn(permissionStatus());

        mockMvc.perform(put("/api/onboarding/permissions")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPermissionRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERMISSION_STATUS_SAVED"))
                .andExpect(jsonPath("$.data.permissionSnapshot.locationStatus").value("GRANTED"))
                .andExpect(jsonPath("$.data.permissionSnapshot.checkedAt")
                        .value("2026-09-04T02:59:50Z"))
                .andExpect(jsonPath("$.data.permissionSnapshot.updatedAt")
                        .value("2026-09-04T03:00:00Z"));
    }

    @Test
    void mapsMalformedPermissionJsonToPermissionRequestError() throws Exception {
        mockMvc.perform(put("/api/onboarding/permissions")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERMISSION_STATUS_REQUEST"));
    }

    @Test
    void rejectsUnknownPermissionRequestFields() throws Exception {
        mockMvc.perform(put("/api/onboarding/permissions")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPermissionRequest().replace(
                                "\"checkedAt\":", "\"userId\":1,\"checkedAt\":"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERMISSION_STATUS_REQUEST"));
    }

    @Test
    void mapsStalePermissionSnapshotToConflict() throws Exception {
        when(service.savePermissionStatus(
                org.mockito.ArgumentMatchers.eq("Bearer access"),
                any(SavePermissionStatusCommand.class)
        )).thenThrow(OnboardingException.of(
                OnboardingException.Reason.STALE_PERMISSION_SNAPSHOT,
                "더 최신인 권한 상태가 이미 저장되어 있습니다."
        ));

        mockMvc.perform(put("/api/onboarding/permissions")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPermissionRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_PERMISSION_SNAPSHOT"));
    }

    private static InitialSettingsResult initialSettings() {
        return new InitialSettingsResult(
                "KR", "ko", false, null,
                new InitialSettingsResult.Settings("TRAVELER", false, "Asia/Seoul"),
                OnboardingService.NOTIFICATION_CATEGORIES.stream()
                        .map(category -> new InitialSettingsResult.NotificationPreference(
                                category, true, true
                        ))
                        .toList()
        );
    }

    private static String validRequest() {
        return """
                {
                  "nationalityCode":"KR",
                  "defaultLanguageCode":"ko",
                  "locationSharingDefault":false,
                  "timezoneName":"Asia/Seoul",
                  "notificationPreferences":[
                    {"notificationCategory":"MATCHING","pushEnabled":true,"inAppEnabled":true},
                    {"notificationCategory":"CHAT","pushEnabled":true,"inAppEnabled":true},
                    {"notificationCategory":"CALL","pushEnabled":true,"inAppEnabled":true},
                    {"notificationCategory":"REVIEW","pushEnabled":true,"inAppEnabled":true},
                    {"notificationCategory":"REPORT","pushEnabled":true,"inAppEnabled":true},
                    {"notificationCategory":"VERIFICATION","pushEnabled":true,"inAppEnabled":true},
                    {"notificationCategory":"SANCTION","pushEnabled":true,"inAppEnabled":true}
                  ]
                }
                """;
    }

    private static PermissionStatusResult permissionStatus() {
        return new PermissionStatusResult(new PermissionStatusResult.PermissionSnapshot(
                "GRANTED", "DENIED", "NOT_DETERMINED", "RESTRICTED",
                Instant.parse("2026-09-04T02:59:50Z"), COMPLETED_AT
        ));
    }

    private static String validPermissionRequest() {
        return """
                {
                  "locationStatus":"GRANTED",
                  "notificationStatus":"DENIED",
                  "cameraStatus":"NOT_DETERMINED",
                  "microphoneStatus":"RESTRICTED",
                  "checkedAt":"2026-09-04T11:59:50+09:00"
                }
                """;
    }
}
