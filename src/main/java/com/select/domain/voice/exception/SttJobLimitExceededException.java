package com.select.domain.voice.exception;

public class SttJobLimitExceededException extends RuntimeException {

    public SttJobLimitExceededException() {
        super("음성 작업 한도를 초과했습니다.");
    }
}
