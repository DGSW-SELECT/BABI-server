package com.select.domain.handover.controller;

import com.select.domain.handover.dto.request.HandoverMemoCreateRequest;
import com.select.domain.handover.dto.response.HandoverMemoResponse;
import com.select.domain.handover.exception.AuthenticatedUserRequiredException;
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
@RequestMapping("/groups/{groupId}/babies/{babyId}/handover-memos")
public class HandoverMemoController {

    private final HandoverMemoService handoverMemoService;

    public HandoverMemoController(HandoverMemoService handoverMemoService) {
        this.handoverMemoService = handoverMemoService;
    }

    @PostMapping
    public ApiResponse<HandoverMemoResponse> createMemo(
            @PathVariable Long groupId,
            @PathVariable Long babyId,
            @RequestAttribute(value = "authenticatedUserId", required = false)
            Long authenticatedUserId,
            @Valid @RequestBody HandoverMemoCreateRequest request
    ) {
        if (authenticatedUserId == null) {
            throw new AuthenticatedUserRequiredException();
        }

        HandoverMemoResponse response = handoverMemoService.createMemo(
                groupId,
                babyId,
                authenticatedUserId,
                request
        );

        return new ApiResponse<>(true, "인수인계 메모가 등록되었습니다.", response);
    }
}
