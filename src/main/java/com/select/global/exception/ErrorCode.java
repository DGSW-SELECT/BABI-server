package com.select.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    EMAIL_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 없거나 만료되었습니다."),
    EMAIL_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증번호 입력 횟수를 초과했습니다."),
    EMAIL_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 재발송하려면 60초가 지나야 합니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 필요하거나 인증 유효기간이 만료되었습니다."),
    EMAIL_VERIFICATION_IN_PROGRESS(HttpStatus.CONFLICT, "해당 이메일의 회원가입이 진행 중입니다."),
    EMAIL_VERIFICATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이메일 인증 서비스를 일시적으로 사용할 수 없습니다."),
    EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "인증번호 전송에 실패했습니다."),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    REQUEST_ERROR(HttpStatus.BAD_REQUEST, "요청을 처리할 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 오류가 발생했습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문이 올바르지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.BAD_REQUEST, "이메일 또는 비밀번호가 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드 입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

}
