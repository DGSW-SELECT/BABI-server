package com.select.domain.voice.dto.response;

import com.select.domain.parenting.dto.response.ParentingRecordResponse;

import java.util.List;

/**
 * 프론트 음성 기록 화면의 저장 완료 응답.
 *
 * <p>프론트는 {@code ok}만 확인하지만, 저장된 기록으로 이동할 수 있도록
 * {@code voiceInputId}와 자동 생성된 육아기록도 함께 반환한다.</p>
 */
public record VoiceRecordSavedResponse(
        boolean ok,
        Long voiceInputId,
        List<ParentingRecordResponse> parentingRecords
) {

    public VoiceRecordSavedResponse {
        parentingRecords = parentingRecords == null ? List.of() : List.copyOf(parentingRecords);
    }
}
