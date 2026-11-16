package com.select.domain.voice.dto.response;

import java.util.List;

/**
 * 프론트 음성 기록 화면의 STT 미리보기 응답.
 */
public record ParsedVoiceRecordResponse(
        String transcript,
        List<ParsedVoiceItemResponse> items
) {

    public ParsedVoiceRecordResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
