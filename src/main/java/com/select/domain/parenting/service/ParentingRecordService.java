package com.select.domain.parenting.service;

import com.select.domain.parenting.analyzer.ParentingRecordAnalyzer;
import com.select.domain.parenting.analyzer.ParentingRecordDraft;
import com.select.domain.parenting.dto.response.ParentingRecordResponse;
import com.select.domain.parenting.entity.ParentingRecord;
import com.select.domain.parenting.repository.ParentingRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ParentingRecordService {

    private final ParentingRecordRepository parentingRecordRepository;
    private final ParentingRecordAnalyzer parentingRecordAnalyzer;

    public ParentingRecordService(
            ParentingRecordRepository parentingRecordRepository,
            ParentingRecordAnalyzer parentingRecordAnalyzer
    ) {
        this.parentingRecordRepository = parentingRecordRepository;
        this.parentingRecordAnalyzer = parentingRecordAnalyzer;
    }

    @Transactional
    public List<ParentingRecordResponse> analyzeAndSave(
            Long babyId,
            Long familyMemberId,
            Long voiceInputId,
            String transcript,
            LocalDateTime referenceTime
    ) {
        List<ParentingRecordDraft> drafts = parentingRecordAnalyzer.analyze(transcript, referenceTime);
        if (drafts.isEmpty()) {
            return List.of();
        }

        List<ParentingRecord> records = drafts.stream()
                .map(draft -> ParentingRecord.builder()
                        .babyId(babyId)
                        .familyMemberId(familyMemberId)
                        .voiceInputId(voiceInputId)
                        .recordType(draft.recordType())
                        .occurredAt(draft.occurredAt())
                        .amountMl(draft.amountMl())
                        .durationMinutes(draft.durationMinutes())
                        .feedingType(draft.feedingType())
                        .diaperType(draft.diaperType())
                        .memo(draft.memo())
                        .build())
                .toList();

        return parentingRecordRepository.saveAll(records).stream()
                .map(ParentingRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ParentingRecordResponse> findByVoiceInput(Long babyId, Long voiceInputId) {
        return parentingRecordRepository
                .findAllByBabyIdAndVoiceInputIdOrderByIdAsc(babyId, voiceInputId)
                .stream()
                .map(ParentingRecordResponse::from)
                .toList();
    }
}
