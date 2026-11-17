package com.select.domain.baby.exception;

public class InvalidOwnershipTransferException extends RuntimeException{
    public InvalidOwnershipTransferException() {
        super("자기 자신에게 권한을 위임할수 없습니다.");
    }
}
