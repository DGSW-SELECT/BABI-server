package com.select.domain.handover.service;

import com.select.domain.handover.dto.response.HandoverBriefingResponse;
import com.select.domain.handover.entity.HandoverBriefing;
import com.select.domain.handover.exception.HandoverBriefingNotFoundException;
import com.select.domain.handover.exception.HandoverBriefingGenerationException;
import com.select.domain.handover.exception.NoDailyCareRecordException;
import com.select.domain.handover.repository.HandoverBriefingRepository;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.report.dto.response.DailyReportFacts;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class HandoverBriefingService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final HandoverBriefingRepository handoverBriefingRepository;
    private final DailyCareRecordProvider dailyCareRecordProvider;
    private final HandoverBriefingGenerator handoverBriefingGenerator;
    private final BabyAccessRepository babyAccessRepository;
    private final BabyRepository babyRepository;
    private final HandoverBriefingRateLimiter rateLimiter;
    private final TransactionTemplate transactions;

    public HandoverBriefingService(
            HandoverBriefingRepository handoverBriefingRepository,
            DailyCareRecordProvider dailyCareRecordProvider,
            HandoverBriefingGenerator handoverBriefingGenerator,
            BabyAccessRepository babyAccessRepository,
            BabyRepository babyRepository,
            HandoverBriefingRateLimiter rateLimiter,
            PlatformTransactionManager transactionManager
    ) {
        this.handoverBriefingRepository = handoverBriefingRepository;
        this.dailyCareRecordProvider = dailyCareRecordProvider;
        this.handoverBriefingGenerator = handoverBriefingGenerator;
        this.babyAccessRepository = babyAccessRepository;
        this.babyRepository = babyRepository;
        this.rateLimiter = rateLimiter;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(propagation = Propagation.NEVER)
    public HandoverBriefingResponse createBriefing(
            Long userId,
            Long babyId
    ) {
        requireWriteAccess(userId, babyId);
        rateLimiter.check(babyId);
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        DailyReportFacts facts = dailyCareRecordProvider.findToday(babyId, today);

        if (!facts.hasRecords()) {
            throw new NoDailyCareRecordException();
        }

        String summary;
        try {
            summary = handoverBriefingGenerator.generate(facts);
        } catch (RuntimeException e) {
            throw new HandoverBriefingGenerationException(e);
        }
        return transactions.execute(status -> {
            babyRepository.findByIdForUpdate(babyId)
                    .orElseThrow(() -> new AccessDeniedException("브리핑 생성 권한이 없습니다."));
            requireWriteAccess(userId, babyId);
            LocalDateTime generatedAt = LocalDateTime.now(SEOUL_ZONE);
            HandoverBriefing briefing = handoverBriefingRepository.save(new HandoverBriefing(
                    today.atStartOfDay(), generatedAt, "COMPLETED", babyId, today, summary, generatedAt));
            return toResponse(briefing);
        });
    }

    @Transactional(readOnly = true)
    public HandoverBriefingResponse getLatestBriefing(Long userId, Long babyId) {
        requireReadAccess(userId, babyId);
        HandoverBriefing briefing = handoverBriefingRepository
                .findTopByBabyIdOrderByGeneratedAtDescIdDesc(babyId)
                .orElseThrow(HandoverBriefingNotFoundException::new);

        return toResponse(briefing);
    }

    private void requireWriteAccess(Long userId, Long babyId) {
        if (babyAccessRepository.findMemberId(userId, babyId, "CARE_WRITE", true).isEmpty()) {
            throw new AccessDeniedException("브리핑 생성 권한이 없습니다.");
        }
    }

    private void requireReadAccess(Long userId, Long babyId) {
        if (babyAccessRepository.findMemberId(userId, babyId, "CARE_READ", false).isEmpty()) {
            throw new AccessDeniedException("브리핑 조회 권한이 없습니다.");
        }
    }

    private HandoverBriefingResponse toResponse(HandoverBriefing briefing) {
        return new HandoverBriefingResponse(
                briefing.getBriefingDate(),
                briefing.getSummary()
        );
    }
}
