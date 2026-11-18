package com.select.domain.care.service;

import com.select.domain.care.analyzer.CareRecordAnalyzer;
import com.select.domain.care.analyzer.CareRecordDraft;
import com.select.domain.care.dto.request.CreateCareRecordRequest;
import com.select.domain.care.dto.response.CareRecordResponse;
import com.select.domain.care.repository.CareRecordRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 음성 transcript를 분석해 육아기록으로 저장한다.
 *
 * <p>트랜잭션은 호출자가 연다. 호출자는 트랜잭션을 열기 전에 쓰기 권한을 확인하고,
 * 트랜잭션 안에서 아기를 잠근 뒤 권한을 다시 확인해야 한다.</p>
 */
@Component
public class CareRecordWriter {

    private static final int MAX_NOTE_LENGTH = 5000;
    private static final int MAX_AMOUNT_ML = 5000;
    private static final Duration MAX_SLEEP_DURATION = Duration.ofHours(24);

    private final CareRecordRepository careRecordRepository;
    private final CareRecordAnalyzer careRecordAnalyzer;

    public CareRecordWriter(
            CareRecordRepository careRecordRepository,
            CareRecordAnalyzer careRecordAnalyzer
    ) {
        this.careRecordRepository = careRecordRepository;
        this.careRecordAnalyzer = careRecordAnalyzer;
    }

    public List<CareRecordResponse> analyzeAndSave(
            Long babyId,
            Long memberId,
            Long voiceInputId,
            String transcript,
            LocalDateTime referenceTime
    ) {
        return careRecordAnalyzer.analyze(transcript, referenceTime)
                .stream()
                .map(draft -> toRequest(draft, voiceInputId))
                .filter(this::isPersistable)
                .map(request -> careRecordRepository.save(babyId, memberId, request))
                .toList();
    }

    private boolean isPersistable(CreateCareRecordRequest request) {
        if (!request.isValidForType()
                || (request.amountMl() != null && (request.amountMl() < 0 || request.amountMl() > MAX_AMOUNT_ML))) {
            return false;
        }
        return request.sleepEndAt() == null
                || Duration.between(request.occurredAt(), request.sleepEndAt()).compareTo(MAX_SLEEP_DURATION) <= 0;
    }

    private CreateCareRecordRequest toRequest(CareRecordDraft draft, Long voiceInputId) {
        LocalDateTime sleepEndAt = draft.durationMinutes() == null
                ? null
                : draft.occurredAt().plusMinutes(draft.durationMinutes());
        return new CreateCareRecordRequest(
                draft.recordType(),
                draft.occurredAt(),
                voiceInputId,
                draft.feedingType(),
                draft.amountMl(),
                sleepEndAt,
                null,
                draft.diaperType(),
                null,
                null,
                truncateNote(draft.memo())
        );
    }

    private String truncateNote(String note) {
        if (note == null || note.length() <= MAX_NOTE_LENGTH) {
            return note;
        }
        return note.substring(0, MAX_NOTE_LENGTH);
    }
}
