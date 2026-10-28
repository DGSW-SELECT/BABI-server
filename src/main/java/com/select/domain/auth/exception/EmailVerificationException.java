package com.select.domain.auth.exception;

import com.select.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class EmailVerificationException extends RuntimeException {

    private final ErrorCode errorCode;

    public EmailVerificationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public EmailVerificationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
