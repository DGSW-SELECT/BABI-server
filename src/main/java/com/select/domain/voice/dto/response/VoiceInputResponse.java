package com.select.domain.voice.dto.response;

import com.select.domain.parenting.dto.response.ParentingRecordResponse;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;

import java.time.LocalDateTime;
import java.util.List;

public record VoiceInputResponse(
        Long voiceInputId,
        Long babyId,
        Long familyMemberId,
        String transcript,
        SttStatus sttStatus,
        LocalDateTime createdAt,
        List<ParentingRecordResponse> parentingRecords
) {

    public static VoiceInputResponse of(
            VoiceInput voiceInput,
            List<ParentingRecordResponse> parentingRecords
    ) {
        return new VoiceInputResponse(
                voiceInput.getId(),
                voiceInput.getBabyId(),
                voiceInput.getFamilyMemberId(),
                voiceInput.getTranscript(),
                voiceInput.getSttStatus(),
                voiceInput.getCreatedAt(),
                List.copyOf(parentingRecords)
        );
    }
}
