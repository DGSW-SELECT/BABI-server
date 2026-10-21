package com.select.domain.baby.controller;

import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.response.BabyCreateResponse;
import com.select.domain.baby.service.BabyService;
import com.select.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/babies")
@RequiredArgsConstructor
public class BabyController {

    private final BabyService babyService;

    @PostMapping
    public ApiResponse<BabyCreateResponse> saveBaby(@Valid @RequestBody BabyCreateRequest request) {
        String userId = (String)SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        Long authenticatedUserId = Long.valueOf(userId);
        BabyCreateResponse response = babyService.createBaby(request,authenticatedUserId);
        return new ApiResponse<BabyCreateResponse>(true, "아기 등록에 성공했습니다.",response);
    }
}
