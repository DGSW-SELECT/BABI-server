package com.select.domain.voice.exception;

public class VoiceInputNotFoundException extends RuntimeException {

    public VoiceInputNotFoundException() {
        super("음성 기록을 찾을 수 없습니다.");
    }
}
