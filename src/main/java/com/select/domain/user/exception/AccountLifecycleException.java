package com.select.domain.user.exception;

import com.select.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class AccountLifecycleException extends RuntimeException {
    private final ErrorCode errorCode;
    public AccountLifecycleException(ErrorCode errorCode) { super(errorCode.getMessage()); this.errorCode = errorCode; }
}
