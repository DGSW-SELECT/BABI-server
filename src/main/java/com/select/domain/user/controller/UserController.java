package com.select.domain.user.controller;

import com.select.domain.user.dto.request.LoginRequest;
import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.dto.response.LoginResponse;
import com.select.domain.user.dto.response.SignupResponse;
import com.select.domain.user.service.UserService;
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
    private final com.select.domain.user.service.AccountLifecycleService accountLifecycle;
    private final com.select.global.security.jwt.RefreshSessionService refreshSessions;
    private final com.select.global.security.jwt.AuthCookies cookies;
    private final com.select.global.security.jwt.JwtProvider jwt;
    private final com.select.domain.user.repository.UserRepository users;

    @org.springframework.web.bind.annotation.GetMapping("/csrf")
    public ApiResponse<java.util.Map<String, String>> csrf(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        String value = com.select.global.security.jwt.AuthCookies.get(request, com.select.global.security.jwt.AuthCookies.CSRF);
        if (value == null || value.isBlank()) throw new com.select.domain.user.exception.AccountLifecycleException(com.select.global.exception.ErrorCode.UNAUTHORIZED);
        response.setHeader(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store");
        return new ApiResponse<>(true, "CSRF 토큰을 조회했습니다.", java.util.Map.of("token", value));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "로그아웃", description = "현재 액세스 토큰을 만료 시각까지 무효화합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        String accessToken = (String) request.getAttribute("accessToken");
        accountLifecycle.logout(accessToken);
        refreshSessions.revokeSession(jwt.getSessionId(accessToken));
        refreshSessions.revoke(com.select.global.security.jwt.AuthCookies.get(request, com.select.global.security.jwt.AuthCookies.REFRESH));
        cookies.clear(response);
        return new ApiResponse<>(true, "로그아웃했습니다.", null);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        var rotation = refreshSessions.rotate(com.select.global.security.jwt.AuthCookies.get(request, com.select.global.security.jwt.AuthCookies.REFRESH));
        Long userId;
        try { userId = Long.valueOf(rotation.userId()); }
        catch (NumberFormatException e) { throw new com.select.domain.user.exception.AccountLifecycleException(com.select.global.exception.ErrorCode.INVALID_TOKEN); }
        if (users.findById(userId).filter(com.select.domain.user.entity.User::isActive).isEmpty()) {
            refreshSessions.revoke(rotation.token());
            throw new com.select.domain.user.exception.AccountLifecycleException(com.select.global.exception.ErrorCode.INVALID_TOKEN);
        }
        String access = jwt.createAccessToken(rotation.userId(), com.select.global.security.jwt.RefreshSessionService.sessionId(rotation.token()));
        cookies.set(response, access, rotation.token());
        return new ApiResponse<>(true, "토큰을 갱신했습니다.", new LoginResponse(access));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "회원 탈퇴", description = "비밀번호 확인 후 계정을 익명화하고 보호자 권한을 제거합니다.")
    @org.springframework.web.bind.annotation.DeleteMapping("/account")
    public ApiResponse<Void> withdraw(@org.springframework.security.core.annotation.AuthenticationPrincipal String principal,
            @Valid @RequestBody com.select.domain.user.dto.request.WithdrawRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
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
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletResponse httpResponse) {
        LoginResponse response = userService.login(request);
        String userId = jwt.getUserId(response.accessToken());
        String refresh = refreshSessions.create(userId);
        String access = jwt.createAccessToken(userId, com.select.global.security.jwt.RefreshSessionService.sessionId(refresh));
        response = new LoginResponse(access);
        cookies.set(httpResponse, access, refresh);
        return new ApiResponse<>(true,"로그인이 완료되었습니다.",response);
    }


}
