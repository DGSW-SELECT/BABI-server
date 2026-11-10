package com.select.domain.growth.controller;

import com.select.domain.growth.dto.response.GrowthComparisonResponse;
import com.select.domain.growth.service.GrowthService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GrowthComparisonController {
    private final GrowthService growthService;

    @Operation(summary = "이전 성장 기록과 비교", description = "지정 기록보다 측정일이 이전인 가장 최근 기록과 비교합니다.")
    @GetMapping({"/babies/{babyId}/growth-comparison", "/api/v1/babies/{babyId}/growth-comparison"})
    public GrowthComparisonResponse compare(@PathVariable Long babyId,
            @AuthenticationPrincipal(expression = "toString()") String userId,
            @RequestParam Long growthRecordId) {
        return growthService.compareGrowth(babyId, Long.valueOf(userId), growthRecordId);
    }
}
