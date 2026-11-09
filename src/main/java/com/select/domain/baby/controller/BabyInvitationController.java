package com.select.domain.baby.controller;

import com.select.domain.baby.request.BabyInvitationRequest;
import com.select.domain.baby.request.InvitationCodeRequest;
import com.select.domain.baby.response.BabyInvitationCodeResponse;
import com.select.domain.baby.response.BabyInvitationResponse;
import com.select.domain.baby.service.BabyInvitationWorkflow;
import com.select.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/babies")
public class BabyInvitationController {
    private final BabyInvitationWorkflow invitations;

    @GetMapping("/{babyId}/invitation-code")
    @Operation(summary = "초대 코드 조회 또는 최초 발급", description = "OWNER만 호출할 수 있습니다. 만료되거나 사용 횟수를 소진한 코드는 자동 재발급하지 않으며 409를 반환합니다.")
    public ApiResponse<BabyInvitationCodeResponse> getCode(@PathVariable Long babyId, @AuthenticationPrincipal String principal) {
        return new ApiResponse<>(true, "초대 코드 조회에 성공했습니다.", invitations.getCode(babyId, Long.valueOf(principal)));
    }

    @PostMapping("/{babyId}/invitation-code/reissue")
    @Operation(summary = "초대 코드 재발급", description = "OWNER가 기존 코드를 무효화하고 14일간 유효한 새 코드를 발급합니다.")
    public ApiResponse<BabyInvitationCodeResponse> reissue(@PathVariable Long babyId, @AuthenticationPrincipal String principal) {
        return new ApiResponse<>(true, "초대 코드를 재발급했습니다.", invitations.rotate(babyId, Long.valueOf(principal)));
    }

    @PostMapping("/{babyId}/invitations")
    @Operation(summary = "이메일 초대 발송", description = "OWNER가 유효한 코드로 초대합니다. 코드가 만료된 경우 먼저 재발급해야 합니다.")
    public ApiResponse<BabyInvitationResponse> send(@PathVariable Long babyId,
            @Valid @RequestBody BabyInvitationRequest request, @AuthenticationPrincipal String principal) {
        return new ApiResponse<>(true, "보호자 초대 이메일을 발송했습니다.",
                invitations.send(babyId, Long.valueOf(principal), request.email()));
    }

    @PostMapping("/invitations/check")
    @Operation(summary = "초대 코드 확인", description = "로그인한 사용자가 코드와 유효기간을 확인합니다.")
    public ApiResponse<BabyInvitationResponse> check(@Valid @RequestBody InvitationCodeRequest request,
            @AuthenticationPrincipal String principal) {
        return new ApiResponse<>(true, "보호자 초대를 확인했습니다.", invitations.inspect(request.code(), Long.valueOf(principal)));
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "초대 코드 수락", description = "로그인한 사용자를 GUARDIAN으로 등록합니다. 코드를 가진 사용자는 누구나 참여할 수 있습니다.")
    public ApiResponse<BabyInvitationResponse> accept(@Valid @RequestBody InvitationCodeRequest request,
            @AuthenticationPrincipal String principal) {
        return new ApiResponse<>(true, "보호자 초대를 수락했습니다.", invitations.accept(request.code(), Long.valueOf(principal)));
    }
}
