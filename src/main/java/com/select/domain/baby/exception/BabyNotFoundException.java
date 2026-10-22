package com.select.domain.baby.exception;

public class BabyNotFoundException extends RuntimeException {
    public BabyNotFoundException() {
        super("아기 정보를 찾을 수 없습니다.");
    }
}
