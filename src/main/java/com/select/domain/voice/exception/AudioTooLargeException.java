package com.select.domain.voice.exception;

public class AudioTooLargeException extends RuntimeException {

    public AudioTooLargeException() {
        super("음성 파일은 25MB 이하여야 합니다.");
    }
}
