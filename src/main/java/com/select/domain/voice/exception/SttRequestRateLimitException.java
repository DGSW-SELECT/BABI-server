package com.select.domain.voice.exception;

import com.select.global.exception.ErrorCode;

public class SttRequestRateLimitException extends RuntimeException {

    private final ErrorCode errorCode;

    public SttRequestRateLimitException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
