package com.select.domain.auth.controller;

import com.select.domain.auth.exception.EmailVerificationException;
import com.select.domain.auth.dto.response.EmailVerificationResponse;
import com.select.domain.auth.service.EmailVerificationService;
import com.select.global.exception.ErrorCode;
import com.select.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmailVerificationControllerTest {
    private EmailVerificationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(EmailVerificationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EmailVerificationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void normalizesBothEmailRequestsBeforeCallingService() throws Exception {
        mvc.perform(post("/auth/email/send").contentType("application/json")
                        .content("{\"email\":\"  USER@EXAMPLE.COM  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).sendVerificationCode("user@example.com");
        when(service.verifyCode("user@example.com", "123456")).thenReturn(
                new EmailVerificationResponse("user@example.com", true, 600, "A".repeat(43)));
        mvc.perform(post("/auth/email/verify").contentType("application/json")
                        .content("{\"email\":\"  USER@EXAMPLE.COM  \",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원가입 이메일 인증번호 확인 요청이 처리되었습니다."))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.expiresIn").value(600))
                .andExpect(jsonPath("$.data.verificationToken").value("A".repeat(43)));
        verify(service).verifyCode("user@example.com", "123456");
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"EMAIL_CODE_MISMATCH", "EMAIL_CODE_EXPIRED",
            "EMAIL_ATTEMPTS_EXCEEDED", "EMAIL_RESEND_TOO_SOON", "EMAIL_NOT_VERIFIED",
            "EMAIL_VERIFICATION_IN_PROGRESS", "EMAIL_VERIFICATION_UNAVAILABLE", "EMAIL_SEND_FAILED"})
    void errorResponsesFollowApiContract(ErrorCode code) throws Exception {
        doThrow(new EmailVerificationException(code)).when(service).verifyCode(anyString(), anyString());
        mvc.perform(post("/auth/email/verify").contentType("application/json")
                        .content("{\"email\":\"user@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().is(code.getHttpStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(code.getMessage()))
                .andExpect(jsonPath("$.error.code").value(code.name()));
    }
}
