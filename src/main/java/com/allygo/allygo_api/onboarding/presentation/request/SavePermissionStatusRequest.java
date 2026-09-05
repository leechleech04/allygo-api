package com.allygo.allygo_api.onboarding.presentation.request;

import com.allygo.allygo_api.onboarding.application.command.SavePermissionStatusCommand;
import com.allygo.allygo_api.onboarding.domain.OnboardingException;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

public final class SavePermissionStatusRequest {
    private final JsonNode locationStatus;
    private final JsonNode notificationStatus;
    private final JsonNode cameraStatus;
    private final JsonNode microphoneStatus;
    private final JsonNode checkedAt;
    private boolean hasUnknownField;

    @JsonCreator
    public SavePermissionStatusRequest(
            @JsonProperty("locationStatus") JsonNode locationStatus,
            @JsonProperty("notificationStatus") JsonNode notificationStatus,
            @JsonProperty("cameraStatus") JsonNode cameraStatus,
            @JsonProperty("microphoneStatus") JsonNode microphoneStatus,
            @JsonProperty("checkedAt") JsonNode checkedAt
    ) {
        this.locationStatus = locationStatus;
        this.notificationStatus = notificationStatus;
        this.cameraStatus = cameraStatus;
        this.microphoneStatus = microphoneStatus;
        this.checkedAt = checkedAt;
    }

    @JsonAnySetter
    public void captureUnknownField(String name, JsonNode value) {
        hasUnknownField = true;
    }

    public SavePermissionStatusCommand toCommand() {
        if (hasUnknownField || !textual(locationStatus) || !textual(notificationStatus)
                || !textual(cameraStatus) || !textual(microphoneStatus) || !textual(checkedAt)) {
            throw OnboardingException.of(
                    OnboardingException.Reason.INVALID_PERMISSION_STATUS_REQUEST,
                    "권한 상태 요청 형식이 올바르지 않습니다."
            );
        }
        return new SavePermissionStatusCommand(
                locationStatus.stringValue(), notificationStatus.stringValue(),
                cameraStatus.stringValue(), microphoneStatus.stringValue(), checkedAt.stringValue()
        );
    }

    private static boolean textual(JsonNode value) {
        return value != null && value.isString();
    }
}
