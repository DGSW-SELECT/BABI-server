package com.select.domain.growth.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.growth.dto.request.GrowthRecordCreateRequest;
import com.select.domain.growth.dto.response.GrowthRecordCreateResponse;
import com.select.domain.growth.dto.response.GrowthRecordItem;
import com.select.domain.growth.dto.response.GrowthRecordPage;
import org.springframework.data.domain.PageRequest;
import com.select.domain.growth.entity.GrowthRecord;
import com.select.domain.growth.repository.GrowthRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import com.select.global.exception.GlobalExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrowthServiceImpl implements GrowthService{

    private final GrowthRepository growthRepository;
    private final BabyMemberRepository babyMemberRepository;
    private final BabyRepository babyRepository;

    @Override
    public GrowthRecordPage listGrowth(Long babyId, Long userId,
            LocalDate from, LocalDate to, Long cursor, int limit) {
        babyMemberRepository.findByBabyIdAndUserId(babyId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BABY_MEMBER_NOT_FOUND));
        if ((from != null && to != null && from.isAfter(to)) || limit < 1 || limit > 100 || (cursor != null && cursor < 1)) {
            throw new BusinessException(ErrorCode.REQUEST_ERROR);
        }
        var results = growthRepository.findPage(babyId, from, to, cursor, PageRequest.of(0, limit + 1));
        boolean more = results.size() > limit;
        var items = results.stream().limit(limit).map(GrowthRecordItem::from).toList();
        Long next = more ? items.get(items.size() - 1).growthRecordId() : null;
        return new GrowthRecordPage(items, next);
    }

    @Override
    @Transactional
    public GrowthRecordCreateResponse createGrowth(
            Long babyId,
            Long userId,
            GrowthRecordCreateRequest request
    ) {
        BabyMember member = babyMemberRepository
                .findByBabyIdAndUserId(babyId, userId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.BABY_MEMBER_NOT_FOUND
                        )
                );
        // 2. 키와 몸무게 둘중에 하나라도 입력했는지 확인
        if (request.heightCm() == null &&
                request.weightKg() == null) {
            throw new BusinessException(
                    ErrorCode.GROWTH_VALUE_REQUIRED
            );
        }
        if (request.measuredDate().isAfter(LocalDate.now())) {
            throw new BusinessException(
                    ErrorCode.GROWTH_INVALID_DATE
            );
        }
        // 4. 동일 날짜 기록 확인
        if (growthRepository.existsByBaby_IdAndMeasuredDate(
                babyId,
                request.measuredDate()
        )) {
            throw new BusinessException(
                    ErrorCode.GROWTH_DUPLICATE_DATE
            );
        }
        Baby baby = babyRepository.findById(babyId).orElseThrow(() -> new BusinessException(ErrorCode.BABY_NOT_FOUND));

        // 5. 성장 기록 생성
        GrowthRecord growthRecord = new GrowthRecord(
                baby,
                member.getId(),
                request.heightCm(),
                request.weightKg(),
                request.measuredDate(),
                request.memo()
        );

        // 6. DB 저장
        GrowthRecord saved = growthRepository.save(growthRecord);

        // 7. Response 반환
        return new GrowthRecordCreateResponse(
              saved.getId(),
                babyId,
                saved.getHeight(),
                saved.getWeight(),
                saved.getMeasuredDate(),
                saved.getMemo(),
                saved.getCreatedAt()
        );
    }

}
