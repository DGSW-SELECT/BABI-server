package com.select.global.security.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Component
public class AuthCookies {
    public static final String ACCESS = "babi_access";
    public static final String REFRESH = "babi_refresh";
    public static final String CSRF = "babi_csrf";
    private final boolean secure;
    private final Duration accessLifetime;
    private final SecureRandom random = new SecureRandom();

    public AuthCookies(@Value("${auth.cookies.secure:true}") boolean secure,
                       @Value("${jwt.access-token-expiration-minutes}") long accessMinutes,
                       org.springframework.core.env.Environment environment) {
        var profiles = java.util.List.of(environment.getActiveProfiles());
        if (!secure && (profiles.isEmpty() || profiles.stream().anyMatch(p -> !p.equals("local") && !p.equals("test")))) {
            throw new IllegalArgumentException("보안 쿠키 해제는 local 또는 test 프로필에서만 허용됩니다.");
        }
        this.secure = secure;
        this.accessLifetime = Duration.ofMinutes(accessMinutes);
    }

    public void set(HttpServletResponse response, String access, String refresh) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        add(response, ACCESS, access, accessLifetime, true);
        add(response, REFRESH, refresh, RefreshSessionService.LIFETIME, true);
        add(response, CSRF, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), RefreshSessionService.LIFETIME, false);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS, "", Duration.ZERO, true);
        add(response, REFRESH, "", Duration.ZERO, true);
        add(response, CSRF, "", Duration.ZERO, false);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private void add(HttpServletResponse response, String name, String value, Duration age, boolean httpOnly) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(httpOnly)
                .secure(secure).sameSite("Lax").path("/").maxAge(age).build().toString());
    }

    public static String get(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        String value = null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                if (value != null) return null;
                value = cookie.getValue();
            }
        }
        return value;
    }
}
