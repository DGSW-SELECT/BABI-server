package com.select.domain.handover.controller;

import com.select.domain.handover.dto.response.HandoverBriefingResponse;
import com.select.domain.handover.service.HandoverBriefingService;
import com.select.global.response.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestAttribute;

@RestController
@RequestMapping("/babi/api/babies/{babyId}/handover-briefings")
public class HandoverBriefingController {

    private final HandoverBriefingService handoverBriefingService;

    public HandoverBriefingController(HandoverBriefingService handoverBriefingService) {
        this.handoverBriefingService = handoverBriefingService;
    }

    @PostMapping
    public ApiResponse<HandoverBriefingResponse> createBriefing(
            @PathVariable Long babyId,
            @RequestAttribute("userId") String userId
    ) {
        HandoverBriefingResponse response =
                handoverBriefingService.createBriefing(Long.valueOf(userId), babyId);

        return new ApiResponse<>(true, "인수인계 브리핑이 생성되었습니다.", response);
    }

    @GetMapping("/latest")
    public ApiResponse<HandoverBriefingResponse> getLatestBriefing(
            @PathVariable Long babyId,
            @RequestAttribute("userId") String userId
    ) {
        HandoverBriefingResponse response =
                handoverBriefingService.getLatestBriefing(Long.valueOf(userId), babyId);

        return new ApiResponse<>(true, "최신 인수인계 브리핑을 조회했습니다.", response);
    }
}
