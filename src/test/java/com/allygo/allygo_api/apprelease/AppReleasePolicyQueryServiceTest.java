package com.allygo.allygo_api.apprelease;

import com.allygo.allygo_api.apprelease.application.AppReleasePolicyQueryService;
import com.allygo.allygo_api.apprelease.application.port.AppReleasePolicyQueryPort;
import com.allygo.allygo_api.apprelease.application.result.AppReleasePolicyResult;
import com.allygo.allygo_api.apprelease.domain.AppPlatform;
import com.allygo.allygo_api.apprelease.domain.AppReleasePolicy;
import com.allygo.allygo_api.apprelease.domain.exception.AppReleasePolicyNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppReleasePolicyQueryServiceTest {

    private static final OffsetDateTime UPDATED_AT =
            OffsetDateTime.parse("2026-07-27T09:00:00+09:00");

    @Mock
    private AppReleasePolicyQueryPort queryPort;

    @InjectMocks
    private AppReleasePolicyQueryService queryService;

    @Test
    void returnsPolicyAndHidesStaleMaintenanceMessageWhenMaintenanceIsDisabled() {
        AppReleasePolicy policy = policy(false, "이전 점검 안내문");
        when(queryPort.findAllByPlatform(AppPlatform.ANDROID)).thenReturn(List.of(policy));

        AppReleasePolicyResult result = queryService.getPolicy(AppPlatform.ANDROID);

        assertThat(result.releasePolicyId()).isEqualTo(2L);
        assertThat(result.platform()).isEqualTo(AppPlatform.ANDROID);
        assertThat(result.minimumSupportedVersion()).isEqualTo("1.4.0");
        assertThat(result.latestVersion()).isEqualTo("1.8.2");
        assertThat(result.maintenanceEnabled()).isFalse();
        assertThat(result.maintenanceMessage()).isNull();
        assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void returnsMaintenanceMessageWhenMaintenanceIsEnabled() {
        AppReleasePolicy policy = policy(true, "서비스 점검 중입니다.");
        when(queryPort.findAllByPlatform(AppPlatform.ANDROID)).thenReturn(List.of(policy));

        AppReleasePolicyResult result = queryService.getPolicy(AppPlatform.ANDROID);

        assertThat(result.maintenanceEnabled()).isTrue();
        assertThat(result.maintenanceMessage()).isEqualTo("서비스 점검 중입니다.");
    }

    @Test
    void throwsNotFoundWhenPlatformPolicyDoesNotExist() {
        when(queryPort.findAllByPlatform(AppPlatform.IOS)).thenReturn(List.of());

        assertThatThrownBy(() -> queryService.getPolicy(AppPlatform.IOS))
                .isInstanceOf(AppReleasePolicyNotFoundException.class);
    }

    @Test
    void treatsDuplicatePlatformPoliciesAsInternalError() {
        AppReleasePolicy policy = policy(false, null);
        when(queryPort.findAllByPlatform(AppPlatform.ANDROID)).thenReturn(List.of(policy, policy));

        assertThatThrownBy(() -> queryService.getPolicy(AppPlatform.ANDROID))
                .isInstanceOf(IllegalStateException.class);
    }

    private AppReleasePolicy policy(boolean maintenanceEnabled, String maintenanceMessage) {
        return new AppReleasePolicy(
                2L,
                AppPlatform.ANDROID,
                "1.4.0",
                "1.8.2",
                maintenanceEnabled,
                maintenanceMessage,
                UPDATED_AT
        );
    }
}
