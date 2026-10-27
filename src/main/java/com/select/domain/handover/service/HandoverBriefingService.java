package com.select.domain.handover.service;

import com.select.domain.handover.dto.response.HandoverBriefingResponse;
import com.select.domain.handover.entity.HandoverBriefing;
import com.select.domain.handover.exception.HandoverBriefingNotFoundException;
import com.select.domain.handover.exception.HandoverBriefingGenerationException;
import com.select.domain.handover.exception.NoDailyCareRecordException;
import com.select.domain.handover.repository.HandoverBriefingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional
public class HandoverBriefingService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final HandoverBriefingRepository handoverBriefingRepository;
    private final DailyCareRecordProvider dailyCareRecordProvider;
    private final HandoverBriefingGenerator handoverBriefingGenerator;

    public HandoverBriefingService(
            HandoverBriefingRepository handoverBriefingRepository,
            DailyCareRecordProvider dailyCareRecordProvider,
            HandoverBriefingGenerator handoverBriefingGenerator
    ) {
        this.handoverBriefingRepository = handoverBriefingRepository;
        this.dailyCareRecordProvider = dailyCareRecordProvider;
        this.handoverBriefingGenerator = handoverBriefingGenerator;
    }

    public HandoverBriefingResponse createBriefing(
            Long groupId,
            Long babyId
    ) {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        List<String> careRecords = dailyCareRecordProvider.findToday(babyId, today);

        if (careRecords.isEmpty()) {
            throw new NoDailyCareRecordException();
        }

        String summary;
        try {
            summary = handoverBriefingGenerator.generate(babyId, today, careRecords);
        } catch (RuntimeException e) {
            throw new HandoverBriefingGenerationException();
        }
        HandoverBriefing briefing = handoverBriefingRepository.save(
                new HandoverBriefing(
                        groupId,
                        babyId,
                        today,
                        summary,
                        LocalDateTime.now(SEOUL_ZONE)
                )
        );

        return toResponse(briefing);
    }

    @Transactional(readOnly = true)
    public HandoverBriefingResponse getLatestBriefing(Long groupId, Long babyId) {
        HandoverBriefing briefing = handoverBriefingRepository
                .findTopByGroupIdAndBabyIdOrderByCreatedAtDesc(groupId, babyId)
                .orElseThrow(HandoverBriefingNotFoundException::new);

        return toResponse(briefing);
    }

    private HandoverBriefingResponse toResponse(HandoverBriefing briefing) {
        return new HandoverBriefingResponse(
                briefing.getBriefingDate(),
                briefing.getSummary()
        );
    }
}
