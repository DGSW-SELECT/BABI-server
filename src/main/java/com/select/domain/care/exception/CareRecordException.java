package com.select.domain.care.exception;

import com.select.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class CareRecordException extends RuntimeException {
    private final ErrorCode errorCode;

    public CareRecordException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
