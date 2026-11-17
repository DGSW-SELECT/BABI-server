package com.select.domain.voice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 프론트 음성 기록 화면에서 확인 후 저장할 때 보내는 요청.
 *
 * <p>{@code items}는 화면 표시용 값이며 서버는 신뢰하지 않는다.
 * 저장 시 transcript를 다시 분석해 육아기록을 만든다. 미리보기 응답의
 * {@code parsedAt}을 그대로 보내면 같은 기준 시각으로 분석해 시간이 어긋나지 않는다.</p>
 */
public record ParsedVoiceRecordRequest(

        @NotBlank(message = "변환된 텍스트를 입력해 주세요.")
        @Size(max = 10000, message = "변환된 텍스트는 10000자 이하여야 합니다.")
        String transcript,

        List<@Valid ParsedVoiceItemRequest> items,

        LocalDateTime parsedAt
) {

    public record ParsedVoiceItemRequest(String type, String label) {
    }
}
