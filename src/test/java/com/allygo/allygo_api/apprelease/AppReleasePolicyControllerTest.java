package com.allygo.allygo_api.apprelease;

import com.allygo.allygo_api.apprelease.application.AppReleasePolicyQueryService;
import com.allygo.allygo_api.apprelease.application.result.AppReleasePolicyResult;
import com.allygo.allygo_api.apprelease.domain.AppPlatform;
import com.allygo.allygo_api.apprelease.domain.exception.AppReleasePolicyNotFoundException;
import com.allygo.allygo_api.apprelease.presentation.AppReleasePolicyController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppReleasePolicyControllerTest {

    @Mock
    private AppReleasePolicyQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AppReleasePolicyController(queryService))
                .build();
    }

    @Test
    void returnsPublicReleasePolicy() throws Exception {
        AppReleasePolicyResult result = new AppReleasePolicyResult(
                2L,
                AppPlatform.ANDROID,
                "1.4.0",
                "1.8.2",
                false,
                null,
                OffsetDateTime.parse("2026-07-27T09:00:00+09:00")
        );
        when(queryService.getPolicy(AppPlatform.ANDROID)).thenReturn(result);

        mockMvc.perform(get("/api/app-release-policy").queryParam("platform", "ANDROID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("APP_RELEASE_POLICY_RETRIEVED"))
                .andExpect(jsonPath("$.message").value("앱 버전 및 지원 정책을 조회했습니다."))
                .andExpect(jsonPath("$.data.releasePolicyId").value(2))
                .andExpect(jsonPath("$.data.platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.minimumSupportedVersion").value("1.4.0"))
                .andExpect(jsonPath("$.data.latestVersion").value("1.8.2"))
                .andExpect(jsonPath("$.data.maintenanceEnabled").value(false))
                .andExpect(jsonPath("$.data.maintenanceMessage").value(nullValue()))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-07-27T09:00:00+09:00"));
    }

    @Test
    void rejectsMissingPlatform() throws Exception {
        mockMvc.perform(get("/api/app-release-policy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_APP_RELEASE_POLICY_QUERY"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(queryService);
    }

    @Test
    void rejectsDuplicatePlatformParameter() throws Exception {
        mockMvc.perform(get("/api/app-release-policy")
                        .queryParam("platform", "IOS", "ANDROID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_APP_RELEASE_POLICY_QUERY"));

        verifyNoInteractions(queryService);
    }

    @Test
    void rejectsAdditionalQueryParameter() throws Exception {
        mockMvc.perform(get("/api/app-release-policy")
                        .queryParam("platform", "IOS")
                        .queryParam("userId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_APP_RELEASE_POLICY_QUERY"));

        verifyNoInteractions(queryService);
    }

    @Test
    void rejectsUnknownPlatform() throws Exception {
        mockMvc.perform(get("/api/app-release-policy").queryParam("platform", "WEB"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_APP_PLATFORM"));

        verifyNoInteractions(queryService);
    }

    @Test
    void rejectsRequestBody() throws Exception {
        mockMvc.perform(get("/api/app-release-policy")
                        .queryParam("platform", "IOS")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_APP_RELEASE_POLICY_QUERY"));

        verifyNoInteractions(queryService);
    }

    @Test
    void returnsNotFoundWhenPlatformPolicyDoesNotExist() throws Exception {
        when(queryService.getPolicy(AppPlatform.IOS))
                .thenThrow(new AppReleasePolicyNotFoundException(AppPlatform.IOS));

        mockMvc.perform(get("/api/app-release-policy").queryParam("platform", "IOS"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APP_RELEASE_POLICY_NOT_FOUND"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void returnsInternalErrorForDuplicateOrInvalidPolicyData() throws Exception {
        when(queryService.getPolicy(AppPlatform.ANDROID))
                .thenThrow(new IllegalStateException("duplicate policy"));

        mockMvc.perform(get("/api/app-release-policy").queryParam("platform", "ANDROID"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
