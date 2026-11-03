package com.select.domain.baby.request;

import com.select.global.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BabyInvitationRequest(@NotBlank @Email @Size(max = 320) String email) {
    public BabyInvitationRequest {
        email = EmailNormalizer.normalize(email);
    }
}
