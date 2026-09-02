package com.allygo.allygo_api.auth.account.presentation;

import com.allygo.allygo_api.auth.account.application.AccountAuthCommandService;
import com.allygo.allygo_api.auth.account.application.AccountSessionService;
import com.allygo.allygo_api.auth.account.domain.AccountAuthException;
import com.allygo.allygo_api.auth.account.presentation.request.LoginRequest;
import com.allygo.allygo_api.auth.account.presentation.request.FindLoginIdRequest;
import com.allygo.allygo_api.auth.account.presentation.request.PasswordResetRequest;
import com.allygo.allygo_api.auth.account.presentation.request.LogoutRequest;
import com.allygo.allygo_api.auth.account.presentation.request.AccountWithdrawalRequest;
import com.allygo.allygo_api.auth.account.presentation.request.SignUpRequest;
import com.allygo.allygo_api.auth.account.presentation.request.TokenRefreshRequest;
import com.allygo.allygo_api.auth.account.presentation.response.CurrentUserResponse;
import com.allygo.allygo_api.auth.account.presentation.response.LoginResponse;
import com.allygo.allygo_api.auth.account.presentation.response.LoginIdLookupResponse;
import com.allygo.allygo_api.auth.account.presentation.response.SignUpResponse;
import com.allygo.allygo_api.auth.account.presentation.response.TokenRefreshResponse;
import com.allygo.allygo_api.auth.account.presentation.response.AccountWithdrawalResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class AccountAuthController {
    private static final Logger log = LoggerFactory.getLogger(AccountAuthController.class);
    private final AccountAuthCommandService commandService;
    private final AccountSessionService sessionService;

    public AccountAuthController(AccountAuthCommandService commandService, AccountSessionService sessionService) {
        this.commandService = commandService;
        this.sessionService = sessionService;
    }

    @PostMapping("/api/auth/sign-up")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(
            @RequestBody SignUpRequest request,
            @RequestHeader(name = "User-Agent", required = false) String userAgent,
            HttpServletRequest httpRequest
    ) {
        SignUpResponse response = SignUpResponse.from(
                commandService.signUp(request.toCommand(httpRequest.getRemoteAddr(), userAgent))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "SIGN_UP_COMPLETED", "회원가입이 완료되었습니다.", response
        ));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginResponse response = LoginResponse.from(
                commandService.login(request.toCommand(httpRequest.getRemoteAddr()))
        );
        return ResponseEntity.ok(ApiResponse.success("LOGIN_SUCCESS", "로그인에 성공했습니다.", response));
    }

    @PostMapping("/api/auth/login-id/find")
    public ResponseEntity<ApiResponse<LoginIdLookupResponse>> findLoginId(
            @RequestBody FindLoginIdRequest request
    ) {
        LoginIdLookupResponse response = LoginIdLookupResponse.from(
                commandService.findLoginId(request == null ? null : request.toCommand())
        );
        return ResponseEntity.ok(ApiResponse.success(
                "LOGIN_ID_FOUND", "아이디를 찾았습니다.", response
        ));
    }

    @PostMapping("/api/auth/password/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetPassword(
            @RequestBody PasswordResetRequest request
    ) {
        commandService.resetPassword(request == null ? null : request.toCommand());
        return ResponseEntity.ok(ApiResponse.success(
                "PASSWORD_RESET_COMPLETED", "비밀번호가 재설정되었습니다.", Map.of()
        ));
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> currentUser(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        CurrentUserResponse response = CurrentUserResponse.from(
                sessionService.currentUser(authorizationHeader)
        );
        return ResponseEntity.ok(ApiResponse.success(
                "CURRENT_USER_RETRIEVED", "로그인 사용자 정보를 조회했습니다.", response
        ));
    }

    @PostMapping("/api/auth/token/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @RequestBody TokenRefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        TokenRefreshResponse response = TokenRefreshResponse.from(
                sessionService.refresh(request.refreshToken(), httpRequest.getRemoteAddr())
        );
        return ResponseEntity.ok(ApiResponse.success(
                "TOKEN_REFRESH_COMPLETED", "인증 토큰이 재발급되었습니다.", response
        ));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<ApiResponse<Object>> logout(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestBody LogoutRequest request
    ) {
        sessionService.logout(authorizationHeader, request == null ? null : request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(
                "LOGOUT_COMPLETED", "로그아웃되었습니다.", null
        ));
    }

    @PostMapping("/api/auth/account/withdrawal")
    public ResponseEntity<ApiResponse<AccountWithdrawalResponse>> withdraw(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestBody AccountWithdrawalRequest request
    ) {
        AccountWithdrawalResponse response = AccountWithdrawalResponse.from(
                sessionService.withdraw(authorizationHeader, request == null ? null : request.toCommand())
        );
        return ResponseEntity.ok(ApiResponse.success(
                "ACCOUNT_WITHDRAWAL_COMPLETED", "회원 탈퇴가 완료되었습니다.", response
        ));
    }

    @ExceptionHandler(AccountAuthException.class)
    ResponseEntity<ApiResponse<Object>> handleAccountAuthException(AccountAuthException exception) {
        HttpStatus status = switch (exception.reason()) {
            case INVALID_SIGN_UP_REQUEST, INVALID_LOGIN_REQUEST, INVALID_LOGIN_ID_LOOKUP_REQUEST,
                    INVALID_PASSWORD_RESET_REQUEST, INVALID_LOGIN_ID_FORMAT,
                    INVALID_PASSWORD_FORMAT, PASSWORD_CONFIRM_MISMATCH, INVALID_NAME_FORMAT,
                    INVALID_NICKNAME_FORMAT, INVALID_NATIONALITY_CODE, INVALID_POLICY_AGREEMENTS,
                    REQUIRED_POLICY_NOT_AGREED, INVALID_DEVICE_ID, INVALID_TOKEN_REFRESH_REQUEST,
                    INVALID_LOGOUT_REQUEST, INVALID_ACCOUNT_WITHDRAWAL_REQUEST,
                    INVALID_REFRESH_TOKEN_FORMAT -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED, INVALID_VERIFICATION_TOKEN, INVALID_LOGIN_CREDENTIALS,
                    INVALID_REFRESH_TOKEN, REFRESH_TOKEN_EXPIRED, REFRESH_TOKEN_REVOKED,
                    REFRESH_TOKEN_REUSE_DETECTED -> HttpStatus.UNAUTHORIZED;
            case VERIFICATION_TOKEN_PURPOSE_MISMATCH, ACCOUNT_SUSPENDED, ACCOUNT_BANNED,
                    ACCOUNT_WITHDRAWN, PHONE_NUMBER_MISMATCH, REFRESH_TOKEN_OWNERSHIP_MISMATCH,
                    LOGIN_RESTRICTED -> HttpStatus.FORBIDDEN;
            case USER_NOT_FOUND, PHONE_VERIFICATION_NOT_FOUND, PHONE_NUMBER_NOT_REGISTERED,
                    POLICY_DOCUMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case LOGIN_ID_ALREADY_EXISTS, NICKNAME_ALREADY_EXISTS, PHONE_NUMBER_ALREADY_REGISTERED,
                    PHONE_VERIFICATION_ALREADY_CONSUMED, POLICY_DOCUMENT_NOT_EFFECTIVE,
                    ACTIVE_HELP_REQUEST_EXISTS, ACTIVE_HELP_SESSION_EXISTS -> HttpStatus.CONFLICT;
            case VERIFICATION_TOKEN_EXPIRED -> HttpStatus.GONE;
            case TOO_MANY_LOGIN_ATTEMPTS, TOO_MANY_TOKEN_REFRESH_ATTEMPTS -> HttpStatus.TOO_MANY_REQUESTS;
        };
        Object data = errorData(exception);
        return ResponseEntity.status(status).body(ApiResponse.failure(
                exception.reason().name(), exception.getMessage(), data
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Object>> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        boolean signUp = request.getRequestURI().endsWith("/sign-up");
        boolean loginIdLookup = request.getRequestURI().endsWith("/login-id/find");
        boolean passwordReset = request.getRequestURI().endsWith("/password/reset");
        boolean tokenRefresh = request.getRequestURI().endsWith("/token/refresh");
        boolean logout = request.getRequestURI().endsWith("/logout");
        boolean withdrawal = request.getRequestURI().endsWith("/account/withdrawal");
        String code = signUp ? "INVALID_SIGN_UP_REQUEST"
                : loginIdLookup ? "INVALID_LOGIN_ID_LOOKUP_REQUEST"
                : passwordReset ? "INVALID_PASSWORD_RESET_REQUEST"
                : tokenRefresh ? "INVALID_TOKEN_REFRESH_REQUEST"
                : logout ? "INVALID_LOGOUT_REQUEST"
                : withdrawal ? "INVALID_ACCOUNT_WITHDRAWAL_REQUEST"
                : "INVALID_LOGIN_REQUEST";
        String message = signUp ? "회원가입 요청 형식이 올바르지 않습니다."
                : loginIdLookup ? "아이디 찾기 요청 형식이 올바르지 않습니다."
                : passwordReset ? "비밀번호 재설정 요청 형식이 올바르지 않습니다."
                : tokenRefresh ? "토큰 재발급 요청 형식이 올바르지 않습니다."
                : logout ? "로그아웃 요청 형식이 올바르지 않습니다."
                : withdrawal ? "회원 탈퇴 요청 형식이 올바르지 않습니다."
                : "로그인 요청 형식이 올바르지 않습니다.";
        return ResponseEntity.badRequest().body(ApiResponse.failure(code, message, null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Object>> handleUnexpectedException(Exception exception) {
        log.error("Unexpected account authentication failure", exception);
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

    private static Object errorData(AccountAuthException exception) {
        if (exception.retryAfter() != null) {
            return Map.of("retryAfter", exception.retryAfter());
        }
        if (exception.restrictionEndsAt() != null) {
            return Map.of("restrictionEndsAt", exception.restrictionEndsAt());
        }
        if (exception.activeResourceType() != null) {
            return Map.of(
                    "activeResourceType", exception.activeResourceType(),
                    "activeResourceId", exception.activeResourceId(),
                    "activeResourceStatus", exception.activeResourceStatus()
            );
        }
        return null;
    }
}
