package com.select.domain.auth.controller;

import com.select.domain.auth.dto.request.EmailSendRequest;
import com.select.domain.auth.dto.request.EmailVerifyRequest;
import com.select.domain.auth.dto.response.EmailVerificationResponse;
import com.select.domain.auth.service.EmailVerificationService;
import com.select.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/send")
    public ApiResponse<Void> send(@Valid @RequestBody EmailSendRequest request) {
        emailVerificationService.sendVerificationCode(request.email());
        return new ApiResponse<>(true,"인증번호가 발송되었습니다.",null);
    }

    @PostMapping("/verify")
    public ApiResponse<EmailVerificationResponse> verify(@Valid @RequestBody EmailVerifyRequest request) {
        EmailVerificationResponse response = emailVerificationService.verifyCode(request.email(),request.code());
        return new ApiResponse<>(true,"회원가입 이메일 인증번호 확인 요청이 처리되었습니다.",response);
    }
}
