package com.select.domain.user.dto.request;

import com.select.global.util.EmailNormalizer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotBlank
        String password
) {
        public LoginRequest{
                email = EmailNormalizer.normalize(email);
        }
}
