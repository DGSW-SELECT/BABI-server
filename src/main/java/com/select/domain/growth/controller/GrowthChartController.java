package com.select.domain.growth.controller;

import com.select.domain.growth.dto.response.GrowthChartResponse;
import com.select.domain.growth.service.GrowthChartService;
import com.select.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GrowthChartController {
    private final GrowthChartService service;

    @Operation(summary = "0~24개월 성장 그래프", description = "날짜순 측정값과 성별 표준 3·50·97백분위 선. 50백분위는 평균이 아닌 중앙값입니다. 의료 진단이 아닙니다.")
    @GetMapping("/babies/{babyId}/growth-chart")
    public ApiResponse<GrowthChartResponse> get(@PathVariable long babyId, @AuthenticationPrincipal String userId) {
        return ApiResponse.success("성장 그래프를 조회했습니다.", service.get(babyId, Long.parseLong(userId)));
    }
}
