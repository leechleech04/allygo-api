package com.allygo.allygo_api.apprelease.presentation;

import com.allygo.allygo_api.apprelease.application.AppReleasePolicyQueryService;
import com.allygo.allygo_api.apprelease.domain.AppPlatform;
import com.allygo.allygo_api.apprelease.domain.exception.AppReleasePolicyNotFoundException;
import com.allygo.allygo_api.apprelease.presentation.response.AppReleasePolicyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AppReleasePolicyController {

    private static final String RETRIEVED_CODE = "APP_RELEASE_POLICY_RETRIEVED";
    private static final String RETRIEVED_MESSAGE = "앱 버전 및 지원 정책을 조회했습니다.";

    private final AppReleasePolicyQueryService queryService;

    public AppReleasePolicyController(AppReleasePolicyQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/api/app-release-policy")
    public ResponseEntity<ApiResponse<AppReleasePolicyResponse>> getPolicy(
            @RequestParam MultiValueMap<String, String> queryParameters,
            @RequestBody(required = false) String requestBody
    ) {
        List<String> platformValues = queryParameters.get("platform");
        if (queryParameters.size() != 1
                || platformValues == null
                || platformValues.size() != 1
                || requestBody != null) {
            throw new InvalidAppReleasePolicyQueryException();
        }

        AppPlatform platform;
        try {
            platform = AppPlatform.from(platformValues.getFirst());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAppPlatformException();
        }

        AppReleasePolicyResponse response =
                AppReleasePolicyResponse.from(queryService.getPolicy(platform));
        return ResponseEntity.ok(ApiResponse.success(RETRIEVED_CODE, RETRIEVED_MESSAGE, response));
    }

    @GetMapping("/api/app-release-policy/{path}")
    public void rejectPathParameter() {
        throw new InvalidAppReleasePolicyQueryException();
    }

    @ExceptionHandler(InvalidAppReleasePolicyQueryException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidQuery() {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_APP_RELEASE_POLICY_QUERY",
                "앱 버전 및 지원 정책 조회 요청이 올바르지 않습니다."
        );
    }

    @ExceptionHandler(InvalidAppPlatformException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidPlatform() {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_APP_PLATFORM",
                "지원하지 않는 앱 플랫폼입니다."
        );
    }

    @ExceptionHandler(AppReleasePolicyNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNotFound() {
        return error(
                HttpStatus.NOT_FOUND,
                "APP_RELEASE_POLICY_NOT_FOUND",
                "앱 버전 및 지원 정책을 찾을 수 없습니다."
        );
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    ResponseEntity<ApiResponse<Void>> handleDataIntegrityError() {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다."
        );
    }

    private ResponseEntity<ApiResponse<Void>> error(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message));
    }

    public record ApiResponse<T>(
            boolean success,
            String code,
            String message,
            T data
    ) {

        static <T> ApiResponse<T> success(String code, String message, T data) {
            return new ApiResponse<>(true, code, message, data);
        }

        static <T> ApiResponse<T> failure(String code, String message) {
            return new ApiResponse<>(false, code, message, null);
        }
    }

    private static final class InvalidAppReleasePolicyQueryException extends RuntimeException {
    }

    private static final class InvalidAppPlatformException extends RuntimeException {
    }
}
