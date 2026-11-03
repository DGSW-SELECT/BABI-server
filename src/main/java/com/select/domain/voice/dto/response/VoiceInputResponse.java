package com.select.domain.voice.dto.response;

import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;

import java.time.LocalDateTime;

public record VoiceInputResponse(
        Long voiceInputId,
        Long babyId,
        Long familyMemberId,
        String transcript,
        SttStatus sttStatus,
        LocalDateTime createdAt
) {

    public static VoiceInputResponse from(VoiceInput voiceInput) {
        return new VoiceInputResponse(
                voiceInput.getId(),
                voiceInput.getBabyId(),
                voiceInput.getFamilyMemberId(),
                voiceInput.getTranscript(),
                voiceInput.getSttStatus(),
                voiceInput.getCreatedAt()
        );
    }
}
