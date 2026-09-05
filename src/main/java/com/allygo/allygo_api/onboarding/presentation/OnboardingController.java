package com.allygo.allygo_api.onboarding.presentation;

import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.onboarding.application.OnboardingService;
import com.allygo.allygo_api.onboarding.domain.OnboardingException;
import com.allygo.allygo_api.onboarding.presentation.request.SaveInitialSettingsRequest;
import com.allygo.allygo_api.onboarding.presentation.request.SavePermissionStatusRequest;
import com.allygo.allygo_api.onboarding.presentation.response.InitialSettingsResponse;
import com.allygo.allygo_api.onboarding.presentation.response.OnboardingCompletionResponse;
import com.allygo.allygo_api.onboarding.presentation.response.PermissionStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class OnboardingController {
    private static final Logger log = LoggerFactory.getLogger(OnboardingController.class);

    private final OnboardingService service;

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    @GetMapping("/api/onboarding/initial-settings")
    public ResponseEntity<ApiResponse<InitialSettingsResponse>> getInitialSettings(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        InitialSettingsResponse response = InitialSettingsResponse.from(
                service.getInitialSettings(authorizationHeader)
        );
        return ResponseEntity.ok(ApiResponse.success(
                "INITIAL_SETTINGS_RETRIEVED", "사용자 초기 설정을 조회했습니다.", response
        ));
    }

    @PutMapping("/api/onboarding/initial-settings")
    public ResponseEntity<ApiResponse<InitialSettingsResponse>> saveInitialSettings(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestBody SaveInitialSettingsRequest request
    ) {
        InitialSettingsResponse response = InitialSettingsResponse.from(
                service.saveInitialSettings(
                        authorizationHeader,
                        request == null ? null : request.toCommand()
                )
        );
        return ResponseEntity.ok(ApiResponse.success(
                "INITIAL_SETTINGS_SAVED", "사용자 초기 설정이 저장되었습니다.", response
        ));
    }

    @PostMapping("/api/onboarding/complete")
    public ResponseEntity<ApiResponse<OnboardingCompletionResponse>> complete(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        OnboardingCompletionResponse response = OnboardingCompletionResponse.from(
                service.complete(authorizationHeader)
        );
        return ResponseEntity.ok(ApiResponse.success(
                "ONBOARDING_COMPLETED", "온보딩이 완료되었습니다.", response
        ));
    }

    @GetMapping("/api/onboarding/permissions")
    public ResponseEntity<ApiResponse<PermissionStatusResponse>> getPermissionStatus(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        PermissionStatusResponse response = PermissionStatusResponse.from(
                service.getPermissionStatus(authorizationHeader)
        );
        return ResponseEntity.ok(ApiResponse.success(
                "PERMISSION_STATUS_RETRIEVED", "사용자 권한 상태를 조회했습니다.", response
        ));
    }

    @PutMapping("/api/onboarding/permissions")
    public ResponseEntity<ApiResponse<PermissionStatusResponse>> savePermissionStatus(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestBody SavePermissionStatusRequest request
    ) {
        PermissionStatusResponse response = PermissionStatusResponse.from(
                service.savePermissionStatus(
                        authorizationHeader,
                        request == null ? null : request.toCommand()
                )
        );
        return ResponseEntity.ok(ApiResponse.success(
                "PERMISSION_STATUS_SAVED", "사용자 권한 상태가 저장되었습니다.", response
        ));
    }

    @ExceptionHandler(OnboardingException.class)
    ResponseEntity<ApiResponse<Object>> handleOnboardingException(OnboardingException exception) {
        HttpStatus status = switch (exception.reason()) {
            case INVALID_INITIAL_SETTINGS_REQUEST, INVALID_NATIONALITY_CODE, INVALID_LANGUAGE_CODE,
                    INVALID_TIMEZONE_NAME, INVALID_NOTIFICATION_PREFERENCES,
                    INVALID_PERMISSION_STATUS_REQUEST, INVALID_PERMISSION_STATUS,
                    INVALID_CHECKED_AT -> HttpStatus.BAD_REQUEST;
            case ACCOUNT_SUSPENDED, ACCOUNT_BANNED, ACCOUNT_WITHDRAWN,
                    LOGIN_RESTRICTED -> HttpStatus.FORBIDDEN;
            case USER_NOT_FOUND, LANGUAGE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case LANGUAGE_NOT_AVAILABLE, ONBOARDING_ALREADY_COMPLETED,
                    STALE_PERMISSION_SNAPSHOT,
                    PERMISSION_SNAPSHOT_TIMESTAMP_CONFLICT -> HttpStatus.CONFLICT;
        };
        Object data = null;
        if (exception.reason() == OnboardingException.Reason.LOGIN_RESTRICTED) {
            Map<String, Object> restriction = new LinkedHashMap<>();
            restriction.put("restrictionEndsAt", exception.restrictionEndsAt());
            data = restriction;
        }
        return ResponseEntity.status(status).body(ApiResponse.failure(
                exception.reason().name(), exception.getMessage(), data
        ));
    }

    @ExceptionHandler(AccountAuthException.class)
    ResponseEntity<ApiResponse<Object>> handleAuthenticationException(AccountAuthException exception) {
        if (exception.reason() != AccountAuthException.Reason.UNAUTHORIZED) {
            log.error("Unexpected authentication failure in onboarding", exception);
            return ResponseEntity.internalServerError().body(ApiResponse.failure(
                    "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.", null
            ));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failure(
                "UNAUTHORIZED", exception.getMessage(), null
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Object>> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        boolean permissionRequest = "/api/onboarding/permissions".equals(request.getRequestURI());
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                permissionRequest ? "INVALID_PERMISSION_STATUS_REQUEST" : "INVALID_INITIAL_SETTINGS_REQUEST",
                permissionRequest ? "권한 상태 요청 형식이 올바르지 않습니다." : "초기 설정 요청 형식이 올바르지 않습니다.",
                null
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Object>> handleUnexpectedException(Exception exception) {
        log.error("Unexpected onboarding failure", exception);
        return ResponseEntity.internalServerError().body(ApiResponse.failure(
                "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.", null
        ));
    }

    public record ApiResponse<T>(boolean success, String code, String message, T data) {
        static <T> ApiResponse<T> success(String code, String message, T data) {
            return new ApiResponse<>(true, code, message, data);
        }

        static <T> ApiResponse<T> failure(String code, String message, T data) {
            return new ApiResponse<>(false, code, message, data);
        }
    }
}
