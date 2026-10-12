package com.select.domain.user.controller;

import com.select.domain.user.dto.response.SignupResponse;
import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.service.UserService;
import com.select.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private static final String SIGNUP_URL = "/babi/api/v1/auth/signup";

    private MockMvc mockMvc;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void malformedJsonReturnsBadRequestWithoutInternalDetails() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.error.details").isEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowedWithAllowHeader() throws Exception {
        mockMvc.perform(get(SIGNUP_URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.details").isEmpty());
    }

    @Test
    void passwordWith21CharactersReturnsBadRequest() throws Exception {
        String password = "a".repeat(21);

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(userService);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        doThrow(new DuplicateEmailException()).when(userService).signup(any());

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Test1234!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void internalErrorReturnsGenericResponseWithoutExceptionMessage() throws Exception {
        doThrow(new IllegalStateException("internal diagnostic detail"))
                .when(userService).signup(any());

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Test1234!")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버에 오류가 발생했습니다."))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not("internal diagnostic detail")));
    }

    @Test
    void passwordWithExactly20CharactersIsAccepted() throws Exception {
        String password = "a".repeat(20);
        when(userService.signup(any()))
                .thenReturn(new SignupResponse(1L, "test@example.com", "tester"));

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(password)))
                .andExpect(status().isOk());
    }

    @Test
    void passwordWithKoreanReturnsBadRequest() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("안녕12345!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("password"));

        verifyNoInteractions(userService);
    }

    private String signupJson(String password) {
        return """
                {
                  "email": "test@example.com",
                  "password": "%s",
                  "passwordConfirm": "%s",
                  "nickname": "tester"
                }
                """.formatted(password, password);
    }
}
