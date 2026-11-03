package com.select.domain.user.controller;

import com.select.domain.auth.service.EmailVerificationService;

import com.select.domain.user.dto.request.LoginRequest;
import com.select.domain.user.dto.response.LoginResponse;
import com.select.domain.user.dto.response.SignupResponse;
import com.select.domain.user.entity.User;
import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.repository.UserRepository;
import com.select.global.security.jwt.JwtProvider;
import com.select.domain.user.service.UserService;
import com.select.global.exception.GlobalExceptionHandler;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private static final String SIGNUP_URL = "/auth/signup";
    private static final String LOGIN_URL = "/auth/login";

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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1L))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));

        verify(userService).signup(any());
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

    @Test
    void signupWithPaddedUppercaseEmailStoresNormalizedEmail() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MockMvc signupMockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(new UserService(userRepository, passwordEncoder, mock(JwtProvider.class), mock(EmailVerificationService.class))))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        signupMockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("  USER@EXAMPLE.COM  ", "Test1234!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@example.com"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals("user@example.com", userCaptor.getValue().getEmail());
        verify(userRepository).existsByEmail("user@example.com");
    }

    @Test
    void blankEmailReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("   ", "Test1234!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(userService);
    }

    @Test
    void loginEmailWith321CharactersReturnsValidationErrorBeforeCallingService() throws Exception {
        String email = "a".repeat(64) + "@" + "b".repeat(252) + ".com";
        assertEquals(321, email.length());

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "Test1234!")))
                .andExpect(sizeValidationError("email"));

        verifyNoInteractions(userService);
    }

    @Test
    void loginPasswordWith21CharactersPassesValidationAndCallsService() throws Exception {
        String password = "a".repeat(21);
        when(userService.login(any())).thenReturn(new LoginResponse("test-access-token"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("test@example.com", password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(userService).login(new LoginRequest("test@example.com", password));
    }

    @Test
    void signupPasswordConfirmWith21CharactersReturnsValidationErrorBeforeCallingService() throws Exception {
        String body = """
                {
                  "email": "test@example.com",
                  "password": "Test1234!",
                  "passwordConfirm": "%s",
                  "nickname": "tester",
                  "verificationToken": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                }
                """.formatted("a".repeat(21));

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(sizeValidationError("passwordConfirm"));

        verifyNoInteractions(userService);
    }

    @Test
    void loginPasswordWithExactly20CharactersReturnsExistingTokenResponse() throws Exception {
        String password = "a".repeat(20);
        when(userService.login(any())).thenReturn(new LoginResponse("test-access-token"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("test@example.com", password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("로그인이 완료되었습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(userService).login(new LoginRequest("test@example.com", password));
    }

    private ResultMatcher sizeValidationError(String field) {
        return result -> {
            status().isBadRequest().match(result);
            jsonPath("$.success").value(false).match(result);
            jsonPath("$.message").value(ErrorCode.VALIDATION_ERROR.getMessage()).match(result);
            jsonPath("$.data").value(nullValue()).match(result);
            jsonPath("$.error.code").value("VALIDATION_ERROR").match(result);
            jsonPath("$.error.details[*].field").value(hasItem(field)).match(result);
            var exception = assertInstanceOf(MethodArgumentNotValidException.class, result.getResolvedException());
            assertTrue(exception.getBindingResult().getFieldErrors(field).stream()
                    .anyMatch(error -> "Size".equals(error.getCode())));
        };
    }

    private String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private String signupJson(String password) {
        return signupJson("test@example.com", password);
    }

    private String signupJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "passwordConfirm": "%s",
                  "nickname": "tester",
                  "verificationToken": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                }
                """.formatted(email, password, password);
    }
}
