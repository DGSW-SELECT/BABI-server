package com.select.domain.baby.controller;

import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.request.TransferOwnershipRequest;
import com.select.domain.baby.response.BabyCreateResponse;
import com.select.domain.baby.response.BabyMemberResponse;
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

    @GetMapping("/{babyId}/members")
    public ApiResponse<List<BabyMemberResponse>> getBabyMembers(@PathVariable Long babyId, @AuthenticationPrincipal String principal) {
        Long userId = Long.valueOf(principal);
        List<BabyMemberResponse> responses = babyService.getBabyMembers(babyId,userId);
        return new ApiResponse<>(true,"아기 보호자 목록 조회에 성공했습니다.",responses);
    }

    @DeleteMapping("/{babyId}/members/me")
    public ApiResponse<Void> leaveBaby(@PathVariable Long babyId, @AuthenticationPrincipal String principal) {
        Long userId = Long.valueOf(principal);
        babyService.leaveBaby(babyId,userId);
        return new ApiResponse<>(true,"보호자 그룹에서 탈퇴했습니다.",null);
    }

    @PatchMapping("/{babyId}/members/owner")
    public ApiResponse<Void> transferOwnership(@PathVariable Long babyId, @Valid @RequestBody TransferOwnershipRequest request, @AuthenticationPrincipal String principal) {
        Long userId = Long.valueOf(principal);
        babyService.transferOwnership(babyId,userId,request.newOwnerUserId());
        return new ApiResponse<>(true,"보호자 관리권한을 위임하였습니다.",null);
    }

}
