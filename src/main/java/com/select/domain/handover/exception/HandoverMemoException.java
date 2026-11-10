package com.select.domain.handover.exception;

import com.select.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class HandoverMemoException extends RuntimeException {

    private final ErrorCode errorCode;

    public HandoverMemoException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
