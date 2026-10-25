package com.select.domain.auth.dto.response;

public record EmailVerificationResponse(
        String email,
        boolean verified,
        long expiresIn,
        String verificationToken
) {
}
