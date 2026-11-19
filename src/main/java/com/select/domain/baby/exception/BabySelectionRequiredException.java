package com.select.domain.baby.exception;

public class BabySelectionRequiredException extends RuntimeException {

    public BabySelectionRequiredException() {
        super("아기를 선택해 주세요.");
    }
}
