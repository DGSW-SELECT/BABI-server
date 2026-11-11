package com.select.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    VACCINE_INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "조회 날짜 범위가 올바르지 않습니다."),
    VACCINE_INVALID_BABY_ID(HttpStatus.BAD_REQUEST, "올바른 아기 ID가 필요합니다."),
    VACCINE_NOT_FOUND (HttpStatus.NOT_FOUND, "백신을 찾을 수 없습니다."),
    VACCINE_INVALID_DATE_SET(HttpStatus.BAD_REQUEST, "조회 기간은 최대 365일입니다."),
    VACCINE_INVALID_SCHEDULE_DATE(HttpStatus.BAD_REQUEST, "올바른 접종 예정일을 입력해 주세요."),
    DUPLICATE_VACCINATION (HttpStatus.CONFLICT, "이미 있는 백신 예약입니다."),
    VACCINATION_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "접종 일정을 찾을 수 없습니다."),
    VACCINATION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료된 일정입니다."),
    BABY_NOT_FOUND(HttpStatus.NOT_FOUND, "아기를 찾을 수 없습니다."),
    VACCINE_DUPLICATE(HttpStatus.CONFLICT, "다른 접종과 중복될 수 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 리소스에 접근할 권한이 없습니다."),
  
    INVALID_CARE_RECORD(HttpStatus.BAD_REQUEST, "기록 내용 또는 연결된 음성 기록을 확인해 주세요."),
    DAILY_REPORT_NO_RECORDS(HttpStatus.BAD_REQUEST, "해당 날짜에 요약할 육아 기록이 없습니다."),
    DAILY_REPORT_GENERATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 리포트 생성을 일시적으로 사용할 수 없습니다."),

    INVITATION_INVALID(HttpStatus.BAD_REQUEST, "유효한 보호자 초대를 확인할 수 없습니다."),
    INVITATION_CODE_EXPIRED(HttpStatus.CONFLICT, "초대 코드가 만료되었습니다. 소유자가 재발급해야 합니다."),
    INVITATION_CODE_EXHAUSTED(HttpStatus.CONFLICT, "초대 코드의 사용 횟수를 모두 사용했습니다. 소유자가 재발급해야 합니다."),
    INVITATION_SELF(HttpStatus.BAD_REQUEST, "자기 자신을 초대할 수 없습니다."),
    INVITATION_ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 해당 아기의 보호자입니다."),
    ACCOUNT_WITHDRAWAL_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "회원 탈퇴 시도 횟수를 초과했습니다."),
    INVITATION_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "초대 요청 횟수를 초과했습니다."),
    INVITATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "보호자 초대 서비스를 일시적으로 사용할 수 없습니다."),
    INVITATION_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "보호자 초대 이메일 발송에 실패했습니다."),
    BABY_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "멤버를 찾을 수 없습니다."),
    GROWTH_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "성장 기록을 찾을 수 없습니다."),
    GROWTH_VALUE_REQUIRED(HttpStatus.BAD_REQUEST, "키와 몸무게 중 하나라도 입력해야합니다."),
    GROWTH_INVALID_DATE(HttpStatus.BAD_REQUEST, "현재 이전의 날짜를 입력해주세요."),
    GROWTH_DUPLICATE_DATE(HttpStatus.CONFLICT, "동일 날짜의 기록이 존재합니다. 다른 날짜의 기록을 생성해주세요"),

    EMAIL_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다."),
    EMAIL_DEMO_ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "시연 환경에서는 example.invalid 이메일만 사용할 수 있습니다."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 없거나 만료되었습니다."),
    EMAIL_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증번호 입력 횟수를 초과했습니다."),
    EMAIL_SEND_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "인증번호 발송 요청 횟수를 초과했습니다."),
    EMAIL_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 재발송하려면 60초가 지나야 합니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 필요하거나 인증 유효기간이 만료되었습니다."),
    EMAIL_VERIFICATION_IN_PROGRESS(HttpStatus.CONFLICT, "해당 이메일의 회원가입이 진행 중입니다."),
    EMAIL_VERIFICATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이메일 인증 서비스를 일시적으로 사용할 수 없습니다."),
    EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "인증번호 전송에 실패했습니다."),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    AUTH_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인증 서비스를 일시적으로 사용할 수 없습니다."),
    ACCOUNT_OWNER_TRANSFER_REQUIRED(HttpStatus.CONFLICT, "회원 탈퇴 전에 아기의 관리자 권한을 이전해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    REQUEST_ERROR(HttpStatus.BAD_REQUEST, "요청을 처리할 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 오류가 발생했습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문이 올바르지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.BAD_REQUEST, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_AUDIO(HttpStatus.BAD_REQUEST, "지원하지 않거나 비어 있는 음성 파일입니다."),
    AUDIO_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "음성 파일은 25MB 이하여야 합니다."),
    STT_FAILED(HttpStatus.BAD_GATEWAY, "음성 변환에 실패했습니다."),
    VOICE_INPUT_NOT_FOUND(HttpStatus.NOT_FOUND, "음성 기록을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드 입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    DAILY_REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "해당 날짜의 리포트가 이미 존재합니다."),
    DAILY_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 날짜의 리포트를 찾을 수 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
