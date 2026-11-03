package com.select.domain.user.dto.response;

public record SignupResponse(
        Long userId,
        String email,
        String nickname
) {
}
