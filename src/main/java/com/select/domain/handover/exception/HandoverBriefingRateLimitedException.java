package com.select.domain.handover.exception;

public class HandoverBriefingRateLimitedException extends RuntimeException {

    private final int retryAfterSeconds;

    public HandoverBriefingRateLimitedException(int retryAfterSeconds) {
        super("브리핑 생성 요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
