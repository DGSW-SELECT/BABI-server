package com.select.domain.auth.dto.request;

import com.select.global.util.EmailNormalizer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerifyRequest(
        @Email
        @Size(max = 320)
        @NotBlank
        String email,

        @Pattern(regexp = "\\d{6}")
        @NotBlank
        String code
) {
    public EmailVerifyRequest {
        email = EmailNormalizer.normalize(email);
    }
}
