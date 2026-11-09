package com.select.domain.user.controller;

import com.select.domain.user.dto.request.LoginRequest;
import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.dto.response.LoginResponse;
import com.select.domain.user.dto.response.SignupResponse;
import com.select.domain.user.service.UserService;
import com.select.domain.user.service.AuthSessionService;
import com.select.domain.user.service.AccountLifecycleService;
import com.select.domain.user.dto.request.WithdrawRequest;
import com.select.domain.user.exception.AccountLifecycleException;
import com.select.global.exception.ErrorCode;
import com.select.global.security.jwt.AuthCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpHeaders;
import java.util.Map;
import com.select.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountLifecycleService accountLifecycle;
    private final AuthSessionService sessions;
    private final AuthCookies cookies;

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(HttpServletRequest request, HttpServletResponse response) {
        String value = AuthCookies.get(request, AuthCookies.CSRF);
        if (value == null || value.isBlank()) throw new AccountLifecycleException(ErrorCode.UNAUTHORIZED);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return new ApiResponse<>(true, "CSRF 토큰을 조회했습니다.", Map.of("token", value));
    }

    @Operation(summary = "로그아웃", description = "현재 액세스 토큰을 만료 시각까지 무효화합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String authorization = request.getHeader("Authorization");
        String accessToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : AuthCookies.get(request, AuthCookies.ACCESS);
        try {
            accountLifecycle.logout(accessToken, AuthCookies.get(request, AuthCookies.REFRESH));
        } finally {
            cookies.clear(response);
        }
        return new ApiResponse<>(true, "로그아웃했습니다.", null);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        var tokens = sessions.refresh(AuthCookies.get(request, AuthCookies.REFRESH));
        cookies.set(response, tokens.access(), tokens.refresh());
        return new ApiResponse<>(true, "토큰을 갱신했습니다.", new LoginResponse(tokens.access()));
    }

    @Operation(summary = "회원 탈퇴", description = "비밀번호 확인 후 계정을 익명화하고 보호자 권한을 제거합니다.")
    @DeleteMapping("/account")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal String principal,
            @Valid @RequestBody WithdrawRequest request,
            HttpServletResponse response) {
        accountLifecycle.withdraw(Long.valueOf(principal), request.password());
        cookies.clear(response);
        return new ApiResponse<>(true, "회원 탈퇴가 완료되었습니다.", null);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signup(request);
        return new ApiResponse<>(true, "회원가입이 완료되었습니다.", response);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse httpResponse) {
        var tokens = sessions.login(request);
        cookies.set(httpResponse, tokens.access(), tokens.refresh());
        return new ApiResponse<>(true, "로그인이 완료되었습니다.", new LoginResponse(tokens.access()));
    }


}
