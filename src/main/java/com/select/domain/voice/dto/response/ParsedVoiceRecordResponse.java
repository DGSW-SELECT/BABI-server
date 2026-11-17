package com.select.domain.voice.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 프론트 음성 기록 화면의 STT 미리보기 응답.
 *
 * <p>{@code parsedAt}은 미리보기를 만든 시각이다. 저장 요청에 그대로 돌려보내면
 * 서버가 같은 기준 시각으로 다시 분석해 화면에 보인 시간과 저장된 기록이 일치한다.</p>
 */
public record ParsedVoiceRecordResponse(
        String transcript,
        List<ParsedVoiceItemResponse> items,
        LocalDateTime parsedAt
) {

    public ParsedVoiceRecordResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
