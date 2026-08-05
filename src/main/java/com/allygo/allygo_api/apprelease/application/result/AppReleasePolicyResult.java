package com.allygo.allygo_api.apprelease.application.result;

import com.allygo.allygo_api.apprelease.domain.AppPlatform;
import com.allygo.allygo_api.apprelease.domain.AppReleasePolicy;

import java.time.OffsetDateTime;

public record AppReleasePolicyResult(
        Long releasePolicyId,
        AppPlatform platform,
        String minimumSupportedVersion,
        String latestVersion,
        boolean maintenanceEnabled,
        String maintenanceMessage,
        OffsetDateTime updatedAt
) {

    public static AppReleasePolicyResult from(AppReleasePolicy policy) {
        return new AppReleasePolicyResult(
                policy.releasePolicyId(),
                policy.platform(),
                policy.minimumSupportedVersion(),
                policy.latestVersion(),
                policy.maintenanceEnabled(),
                policy.effectiveMaintenanceMessage(),
                policy.updatedAt()
        );
    }
}
