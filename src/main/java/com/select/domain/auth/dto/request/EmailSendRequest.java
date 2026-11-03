package com.select.domain.auth.dto.request;

import com.select.global.util.EmailNormalizer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record EmailSendRequest(
        @Email
        @Size(max = 320)
        @NotBlank
        String email
) {
    public EmailSendRequest {
        email = EmailNormalizer.normalize(email);
    }
}
