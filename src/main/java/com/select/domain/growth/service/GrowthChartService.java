package com.select.domain.growth.service;

import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.growth.dto.response.GrowthChartResponse;
import com.select.domain.growth.repository.GrowthRepository;
import com.select.domain.growth.standard.GrowthStandardService;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class GrowthChartService {
    private final BabyMemberRepository members;
    private final BabyRepository babies;
    private final GrowthRepository records;
    private final GrowthStandardService standards;

    @Transactional(readOnly = true)
    public GrowthChartResponse get(long babyId, long userId) {
        members.findByBabyIdAndUserId(babyId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BABY_MEMBER_NOT_FOUND));
        var baby = babies.findById(babyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BABY_MEMBER_NOT_FOUND));
        var birth = baby.getBirthDate();
        var points = records.findByBaby_IdAndMeasuredDateBetweenOrderByMeasuredDateAscIdAsc(
                babyId, birth, birth.plusMonths(24)).stream()
                .map(r -> new GrowthChartResponse.Measurement(r.getId(), r.getMeasuredDate(),
                        ChronoUnit.DAYS.between(birth, r.getMeasuredDate()), r.getHeight(), r.getWeight())).toList();
        return new GrowthChartResponse(birth, points, baby.getSex() == null ? "SEX_REQUIRED" : "AVAILABLE",
                standards.source(), standards.sourceUrl(), standards.reference(baby));
    }
}
