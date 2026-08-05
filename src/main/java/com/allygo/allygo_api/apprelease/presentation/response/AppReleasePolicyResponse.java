package com.allygo.allygo_api.apprelease.presentation.response;

import com.allygo.allygo_api.apprelease.application.result.AppReleasePolicyResult;
import com.allygo.allygo_api.apprelease.domain.AppPlatform;

import java.time.OffsetDateTime;

public record AppReleasePolicyResponse(
        Long releasePolicyId,
        AppPlatform platform,
        String minimumSupportedVersion,
        String latestVersion,
        boolean maintenanceEnabled,
        String maintenanceMessage,
        OffsetDateTime updatedAt
) {

    public static AppReleasePolicyResponse from(AppReleasePolicyResult result) {
        return new AppReleasePolicyResponse(
                result.releasePolicyId(),
                result.platform(),
                result.minimumSupportedVersion(),
                result.latestVersion(),
                result.maintenanceEnabled(),
                result.maintenanceMessage(),
                result.updatedAt()
        );
    }
}
