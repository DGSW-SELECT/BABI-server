package com.select.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VACCINE_INVALID_DATE_RANGE(
            HttpStatus.BAD_REQUEST,
            "Vaccination_001",
            "조회 날짜 범위가 올바르지 않습니다."
    ),
    VACCINE_INVALID_BABY_ID(
            HttpStatus.BAD_REQUEST,
            "Vaccination_002",
            "올바른 아기 ID가 필요합니다."
    ),
    VACCINE_NOT_FOUND (
            HttpStatus.NOT_FOUND,
            "Vaccination_003",
            "백신을 찾을 수 없습니다."
    ),
    VACCINE_INVALID_DATE_SET(
            HttpStatus.BAD_REQUEST,
            "Vaccination_004",
            "조회 기간은 최대 365일입니다."
    ),
    VACCINE_INVALID_SCHEDULE_DATE(
            HttpStatus.NOT_FOUND,
            "Vaccination_005",
            "접종 예정일을 찾을수 없습니다"
    ),
    DUPLICATE_VACCINATION (
            HttpStatus.CONFLICT,
            "Vaccination_006",
            "이미 있는 백신 예약입니다."
    ),
    VACCINATION_SCHEDULE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Vaccination_007",
            "접종 일정을 찾을 수 없습니다."
    ),
    BABY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Baby_001",
            "아기를 찾을 수 없습니다."
    ),
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "Common_001",
            "해당 리소스에 접근할 권한이 없습니다."
    );
    private final HttpStatus status;
    private final String Code;
    private final String message;
}
