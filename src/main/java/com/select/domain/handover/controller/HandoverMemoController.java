package com.select.domain.handover.controller;

import com.select.domain.handover.dto.request.HandoverMemoCreateRequest;
import com.select.domain.handover.dto.response.HandoverMemoResponse;
import com.select.domain.handover.service.HandoverMemoService;
import com.select.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/babi/api/babies/{babyId}/handover-memos")
public class HandoverMemoController {

    private final HandoverMemoService handoverMemoService;

    public HandoverMemoController(HandoverMemoService handoverMemoService) {
        this.handoverMemoService = handoverMemoService;
    }

    @PostMapping
    public ApiResponse<HandoverMemoResponse> createMemo(
            @PathVariable Long babyId,
            @RequestAttribute("userId") String userId,
            @Valid @RequestBody HandoverMemoCreateRequest request
    ) {
        HandoverMemoResponse response = handoverMemoService.createMemo(babyId, Long.valueOf(userId), request);
        return new ApiResponse<>(true, "인수인계 메모가 등록되었습니다.", response);
    }
}
