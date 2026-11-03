package com.select.domain.voice.exception;

public class SttJobNotRetryableException extends RuntimeException {

    public SttJobNotRetryableException(String message) {
        super(message);
    }
}
