package com.allygo.allygo_api.auth.phoneverification.domain;

public final class PhoneVerificationException extends RuntimeException {

    public enum Reason {
        INVALID_PHONE_NUMBER,
        INVALID_VERIFICATION_PURPOSE,
        INVALID_VERIFICATION_ID,
        INVALID_VERIFICATION_CODE_FORMAT,
        VERIFICATION_CODE_MISMATCH,
        UNAUTHORIZED,
        PHONE_NUMBER_MISMATCH,
        PHONE_NUMBER_NOT_REGISTERED,
        PHONE_NUMBER_ALREADY_REGISTERED,
        PHONE_VERIFICATION_NOT_FOUND,
        PHONE_VERIFICATION_ALREADY_CONSUMED,
        VERIFICATION_CODE_EXPIRED,
        VERIFICATION_RESULT_EXPIRED,
        VERIFICATION_ATTEMPT_LIMIT_EXCEEDED,
        VERIFICATION_RESEND_TOO_EARLY,
        VERIFICATION_REQUEST_LIMIT_EXCEEDED,
        SMS_SEND_FAILED,
        SMS_SERVICE_UNAVAILABLE
    }

    private final Reason reason;
    private final Integer retryAfter;
    private final Integer remainingAttempts;

    private PhoneVerificationException(
            Reason reason,
            String message,
            Integer retryAfter,
            Integer remainingAttempts
    ) {
        super(message);
        this.reason = reason;
        this.retryAfter = retryAfter;
        this.remainingAttempts = remainingAttempts;
    }

    public static PhoneVerificationException invalidPhoneNumber() {
        return create(Reason.INVALID_PHONE_NUMBER, "유효한 E.164 휴대폰 번호를 입력해 주세요.");
    }

    public static PhoneVerificationException invalidPurpose() {
        return create(Reason.INVALID_VERIFICATION_PURPOSE, "지원하지 않는 휴대폰 인증 목적입니다.");
    }

    public static PhoneVerificationException invalidVerificationId() {
        return create(Reason.INVALID_VERIFICATION_ID, "유효한 인증 요청 식별자를 입력해 주세요.");
    }

    public static PhoneVerificationException invalidVerificationCodeFormat() {
        return create(Reason.INVALID_VERIFICATION_CODE_FORMAT, "인증번호는 숫자 6자리여야 합니다.");
    }

    public static PhoneVerificationException verificationCodeMismatch(int remainingAttempts) {
        return new PhoneVerificationException(
                Reason.VERIFICATION_CODE_MISMATCH, "인증번호가 일치하지 않습니다.", null, remainingAttempts
        );
    }

    public static PhoneVerificationException unauthorized() {
        return create(Reason.UNAUTHORIZED, "인증이 필요합니다.");
    }

    public static PhoneVerificationException phoneNumberMismatch() {
        return create(Reason.PHONE_NUMBER_MISMATCH, "본인 계정의 휴대폰 번호와 일치하지 않습니다.");
    }

    public static PhoneVerificationException phoneNumberNotRegistered() {
        return create(Reason.PHONE_NUMBER_NOT_REGISTERED, "가입된 휴대폰 번호를 찾을 수 없습니다.");
    }

    public static PhoneVerificationException phoneNumberAlreadyRegistered() {
        return create(Reason.PHONE_NUMBER_ALREADY_REGISTERED, "이미 가입된 휴대폰 번호입니다.");
    }

    public static PhoneVerificationException verificationNotFound() {
        return create(Reason.PHONE_VERIFICATION_NOT_FOUND, "휴대폰 인증 요청을 찾을 수 없습니다.");
    }

    public static PhoneVerificationException alreadyConsumed() {
        return create(Reason.PHONE_VERIFICATION_ALREADY_CONSUMED, "이미 사용된 휴대폰 인증 결과입니다.");
    }

    public static PhoneVerificationException codeExpired() {
        return create(Reason.VERIFICATION_CODE_EXPIRED, "인증번호가 만료되었습니다.");
    }

    public static PhoneVerificationException resultExpired() {
        return create(Reason.VERIFICATION_RESULT_EXPIRED, "휴대폰 인증 결과가 만료되었습니다.");
    }

    public static PhoneVerificationException attemptLimitExceeded() {
        return create(Reason.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED, "인증번호 입력 가능 횟수를 초과했습니다.");
    }

    public static PhoneVerificationException resendTooEarly(int retryAfter) {
        return new PhoneVerificationException(
                Reason.VERIFICATION_RESEND_TOO_EARLY,
                "인증번호 재전송 대기 시간이 지나지 않았습니다.", retryAfter, null
        );
    }

    public static PhoneVerificationException dailyLimitExceeded(int retryAfter) {
        return new PhoneVerificationException(
                Reason.VERIFICATION_REQUEST_LIMIT_EXCEEDED,
                "일일 인증번호 발송 횟수를 초과했습니다.", retryAfter, null
        );
    }

    public static PhoneVerificationException smsSendFailed() {
        return create(Reason.SMS_SEND_FAILED, "인증번호 발송에 실패했습니다.");
    }

    public static PhoneVerificationException smsUnavailable() {
        return create(Reason.SMS_SERVICE_UNAVAILABLE, "문자 발송 서비스를 일시적으로 이용할 수 없습니다.");
    }

    private static PhoneVerificationException create(Reason reason, String message) {
        return new PhoneVerificationException(reason, message, null, null);
    }

    public Reason reason() {
        return reason;
    }

    public Integer retryAfter() {
        return retryAfter;
    }

    public Integer remainingAttempts() {
        return remainingAttempts;
    }
}
