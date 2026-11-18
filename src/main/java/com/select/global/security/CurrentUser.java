package com.select.global.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * JWT 필터가 인증 principal로 넣은 사용자 ID 문자열을 Long으로 변환한다.
 */
public final class CurrentUser {

    public static Long id(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        try {
            return Long.valueOf(principal);
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("인증 정보가 올바르지 않습니다.");
        }
    }

    private CurrentUser() {
    }
}
