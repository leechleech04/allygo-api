package com.allygo.allygo_api.auth.phoneverification.presentation;

import com.allygo.allygo_api.auth.phoneverification.application.PhoneVerificationCommandService;
import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationException;
import com.allygo.allygo_api.auth.phoneverification.presentation.request.ConfirmPhoneVerificationRequest;
import com.allygo.allygo_api.auth.phoneverification.presentation.request.SendPhoneVerificationRequest;
import com.allygo.allygo_api.auth.phoneverification.presentation.response.PhoneVerificationConfirmedResponse;
import com.allygo.allygo_api.auth.phoneverification.presentation.response.PhoneVerificationSentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PhoneVerificationController {
    private static final String SENT_CODE = "PHONE_VERIFICATION_SENT";
    private static final String SENT_MESSAGE = "인증번호가 발송되었습니다.";
    private static final String CONFIRMED_CODE = "PHONE_VERIFICATION_COMPLETED";
    private static final String CONFIRMED_MESSAGE = "휴대폰 인증이 완료되었습니다.";

    private final PhoneVerificationCommandService commandService;

    public PhoneVerificationController(PhoneVerificationCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/api/auth/phone-verifications")
    public ResponseEntity<ApiResponse<PhoneVerificationSentResponse>> send(
            @RequestBody SendPhoneVerificationRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        PhoneVerificationSentResponse response = PhoneVerificationSentResponse.from(
                commandService.send(request.phoneNumber(), request.purpose(), authorizationHeader)
        );
        return ResponseEntity.ok(ApiResponse.success(SENT_CODE, SENT_MESSAGE, response));
    }

    @PostMapping("/api/auth/phone-verifications/{verificationId}/confirm")
    public ResponseEntity<ApiResponse<PhoneVerificationConfirmedResponse>> confirm(
            @PathVariable String verificationId,
            @RequestBody ConfirmPhoneVerificationRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader
    ) {
        PhoneVerificationConfirmedResponse response = PhoneVerificationConfirmedResponse.from(
                commandService.confirm(
                        verificationId,
                        request == null ? null : request.verificationCode(),
                        authorizationHeader
                )
        );
        return ResponseEntity.ok(ApiResponse.success(CONFIRMED_CODE, CONFIRMED_MESSAGE, response));
    }

    @ExceptionHandler(PhoneVerificationException.class)
    ResponseEntity<ApiResponse<Object>> handlePhoneVerificationException(PhoneVerificationException exception) {
        HttpStatus status = switch (exception.reason()) {
            case INVALID_PHONE_NUMBER, INVALID_VERIFICATION_PURPOSE, INVALID_VERIFICATION_ID,
                    INVALID_VERIFICATION_CODE_FORMAT, VERIFICATION_CODE_MISMATCH -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case PHONE_NUMBER_MISMATCH -> HttpStatus.FORBIDDEN;
            case PHONE_NUMBER_NOT_REGISTERED, PHONE_VERIFICATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PHONE_NUMBER_ALREADY_REGISTERED, PHONE_VERIFICATION_ALREADY_CONSUMED -> HttpStatus.CONFLICT;
            case VERIFICATION_CODE_EXPIRED, VERIFICATION_RESULT_EXPIRED -> HttpStatus.GONE;
            case VERIFICATION_ATTEMPT_LIMIT_EXCEEDED, VERIFICATION_RESEND_TOO_EARLY,
                    VERIFICATION_REQUEST_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case SMS_SEND_FAILED -> HttpStatus.BAD_GATEWAY;
            case SMS_SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        Object data = null;
        if (exception.retryAfter() != null) {
            data = Map.of("retryAfter", exception.retryAfter());
        } else if (exception.remainingAttempts() != null) {
            data = Map.of("remainingAttempts", exception.remainingAttempts());
        }
        return ResponseEntity.status(status).body(ApiResponse.failure(
                exception.reason().name(), exception.getMessage(), data
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
