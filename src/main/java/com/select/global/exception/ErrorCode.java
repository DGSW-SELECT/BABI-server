package com.select.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 오류가 발생했습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문이 올바르지 않습니다."),
    NO_DAILY_CARE_RECORD(HttpStatus.NOT_FOUND, "오늘은 브리핑을 생성할 기록이 없습니다."),
    HANDOVER_BRIEFING_NOT_FOUND(HttpStatus.NOT_FOUND, "최신 인수인계 브리핑을 찾을 수 없습니다."),
    HANDOVER_BRIEFING_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AI 브리핑 생성에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드 입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

}
