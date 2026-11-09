package com.select.domain.baby.exception;

import com.select.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class BabyInvitationException extends RuntimeException {
    private final ErrorCode errorCode;

    public BabyInvitationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
