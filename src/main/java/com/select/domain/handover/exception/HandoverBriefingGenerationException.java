package com.select.domain.handover.exception;

public class HandoverBriefingGenerationException extends RuntimeException {

    public HandoverBriefingGenerationException() {
        super("AI 브리핑 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
}
