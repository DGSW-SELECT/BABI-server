package com.select.domain.voice.dto.response;

import com.select.domain.parenting.dto.response.ParentingRecordResponse;
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
        List<ParentingRecordResponse> parentingRecords
) {

    public static SttJobResponse of(
            SttJob job,
            int maxAttempts,
            List<ParentingRecordResponse> parentingRecords
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
                List.copyOf(parentingRecords)
        );
    }

    public static SttJobResponse of(SttJob job, int maxAttempts) {
        return of(job, maxAttempts, List.of());
    }
}
