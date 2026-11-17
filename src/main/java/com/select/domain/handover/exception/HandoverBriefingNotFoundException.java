package com.select.domain.handover.exception;

public class HandoverBriefingNotFoundException extends RuntimeException {

    public HandoverBriefingNotFoundException() {
        super("최신 인수인계 브리핑을 찾을 수 없습니다.");
    }
}
