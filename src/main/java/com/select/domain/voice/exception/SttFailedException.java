package com.select.domain.voice.exception;

public class SttFailedException extends RuntimeException {

    public SttFailedException(String message) {
        super(message);
    }

    public SttFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
