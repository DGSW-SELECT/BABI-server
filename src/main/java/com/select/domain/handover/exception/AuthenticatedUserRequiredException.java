package com.select.domain.handover.exception;

public class AuthenticatedUserRequiredException extends RuntimeException {

    public AuthenticatedUserRequiredException() {
        super("로그인 후 이용할 수 있습니다.");
    }
}
