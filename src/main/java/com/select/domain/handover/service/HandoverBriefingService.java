package com.select.domain.handover.service;

import com.select.domain.handover.dto.response.HandoverBriefingResponse;
import com.select.domain.handover.entity.HandoverBriefing;
import com.select.domain.handover.exception.HandoverBriefingNotFoundException;
import com.select.domain.handover.exception.HandoverBriefingGenerationException;
import com.select.domain.handover.exception.NoDailyCareRecordException;
import com.select.domain.handover.exception.HandoverBriefingUnavailableException;
import com.select.domain.handover.repository.HandoverBriefingRepository;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
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
    private final BabyMemberRepository babyMemberRepository;
    private final UserRepository userRepository;
    private final HandoverBriefingRateLimiter rateLimiter;

    public HandoverBriefingService(
            HandoverBriefingRepository handoverBriefingRepository,
            DailyCareRecordProvider dailyCareRecordProvider,
            HandoverBriefingGenerator handoverBriefingGenerator,
            BabyMemberRepository babyMemberRepository,
            UserRepository userRepository,
            HandoverBriefingRateLimiter rateLimiter
    ) {
        this.handoverBriefingRepository = handoverBriefingRepository;
        this.dailyCareRecordProvider = dailyCareRecordProvider;
        this.handoverBriefingGenerator = handoverBriefingGenerator;
        this.babyMemberRepository = babyMemberRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
    }

    public HandoverBriefingResponse createBriefing(
            Long userId,
            Long babyId
    ) {
        ensureBabyAccess(userId, babyId);
        rateLimiter.check(babyId);
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        List<String> careRecords;
        try {
            careRecords = dailyCareRecordProvider.findToday(babyId, today);
        } catch (UnsupportedOperationException e) {
            throw new HandoverBriefingUnavailableException(e);
        }

        if (careRecords.isEmpty()) {
            throw new NoDailyCareRecordException();
        }

        String summary;
        try {
            summary = handoverBriefingGenerator.generate(babyId, today, careRecords);
        } catch (RuntimeException e) {
            throw new HandoverBriefingGenerationException(e);
        }
        HandoverBriefing briefing = handoverBriefingRepository.save(
                new HandoverBriefing(
                        null,
                        null,
                        "COMPLETED",
                        babyId,
                        today,
                        summary,
                        LocalDateTime.now(SEOUL_ZONE)
                )
        );

        return toResponse(briefing);
    }

    @Transactional(readOnly = true)
    public HandoverBriefingResponse getLatestBriefing(Long userId, Long babyId) {
        ensureBabyAccess(userId, babyId);
        HandoverBriefing briefing = handoverBriefingRepository
                .findTopByBabyIdOrderByGeneratedAtDesc(babyId)
                .orElseThrow(HandoverBriefingNotFoundException::new);

        return toResponse(briefing);
    }

    private void ensureBabyAccess(Long userId, Long babyId) {
        if (!userRepository.findById(userId).map(User::isActive).orElse(false)) {
            throw new AccessDeniedException("사용할 수 없는 계정입니다.");
        }
        if (!babyMemberRepository.hasCareAccess(babyId, userId)) {
            throw new AccessDeniedException("아기 기록을 조회할 권한이 없습니다.");
        }
    }

    private HandoverBriefingResponse toResponse(HandoverBriefing briefing) {
        return new HandoverBriefingResponse(
                briefing.getBriefingDate(),
                briefing.getSummary()
        );
    }
}
