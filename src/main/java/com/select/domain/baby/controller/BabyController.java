package com.select.domain.baby.controller;

import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.response.BabyCreateResponse;
import com.select.domain.baby.response.BabyReadResponse;
import com.select.domain.baby.service.BabyService;
import com.select.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/babies")
@RequiredArgsConstructor
public class BabyController {

    private final BabyService babyService;

    @PostMapping
    public ApiResponse<BabyCreateResponse> saveBaby(@Valid @RequestBody BabyCreateRequest request, @AuthenticationPrincipal String principal) {
        Long userId = Long.valueOf(principal);
        BabyCreateResponse response = babyService.createBaby(request,userId);
        return new ApiResponse<>(true, "아기 등록에 성공했습니다.",response);
    }

    @GetMapping("/{babyId}")
    public ApiResponse<BabyReadResponse> getBaby(@PathVariable Long babyId, @AuthenticationPrincipal String principal) {
        Long userId = Long.valueOf(principal);
        BabyReadResponse response= babyService.getBaby(babyId,userId);
        return new ApiResponse<>(true,"아기 조회에 성공했습니다.",response);
    }

    @GetMapping
    public ApiResponse<List<BabyReadResponse>> getBabies(@AuthenticationPrincipal String principal) {
         Long userId = Long.valueOf(principal);
          List<BabyReadResponse> responses = babyService.getBabies(userId);
         return new ApiResponse<>(true,"아기 목록 조회에 성공했습니다.",responses);
    }
}
