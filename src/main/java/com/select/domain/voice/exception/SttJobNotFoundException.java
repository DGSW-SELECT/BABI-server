package com.select.domain.voice.exception;

public class SttJobNotFoundException extends RuntimeException {

    public SttJobNotFoundException() {
        super("음성 변환 작업을 찾을 수 없습니다.");
    }
}
