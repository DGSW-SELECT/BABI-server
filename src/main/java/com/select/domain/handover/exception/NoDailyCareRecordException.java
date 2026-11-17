package com.select.domain.handover.exception;

public class NoDailyCareRecordException extends RuntimeException {

    public NoDailyCareRecordException() {
        super("오늘은 브리핑을 생성할 기록이 없습니다.");
    }
}
