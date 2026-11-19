package com.select.domain.handover.exception;

public class HandoverBriefingUnavailableException extends RuntimeException {

    public HandoverBriefingUnavailableException(Throwable cause) {
        super("인수인계 브리핑 서비스를 아직 사용할 수 없습니다.", cause);
    }
}
