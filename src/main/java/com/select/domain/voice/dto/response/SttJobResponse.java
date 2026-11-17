package com.select.domain.voice.dto.response;

import com.select.domain.care.dto.response.CareRecordResponse;
import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;

import java.time.LocalDateTime;
import java.util.List;

public record SttJobResponse(
        Long jobId,
        Long babyId,
        Long familyMemberId,
        SttStatus status,
        String transcript,
        Long voiceInputId,
        int attemptCount,
        int maxAttempts,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<CareRecordResponse> careRecords
) {

    public static SttJobResponse of(
            SttJob job,
            int maxAttempts,
            List<CareRecordResponse> careRecords
    ) {
        return new SttJobResponse(
                job.getId(),
                job.getBabyId(),
                job.getFamilyMemberId(),
                job.getStatus(),
                job.getTranscript(),
                job.getVoiceInputId(),
                job.getAttemptCount(),
                maxAttempts,
                job.getLastError(),
                job.getCreatedAt(),
                job.getCompletedAt(),
                careRecords == null ? List.of() : List.copyOf(careRecords)
        );
    }

    public static SttJobResponse of(SttJob job, int maxAttempts) {
        return of(job, maxAttempts, List.of());
    }
}
