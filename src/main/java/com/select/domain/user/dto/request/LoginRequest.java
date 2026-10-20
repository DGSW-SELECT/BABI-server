package com.select.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @Size(max = 20, message = "비밀번호는 20자 이하여야 합니다.")
        @NotBlank
        String password
) {
        public LoginRequest{
                if (email != null) {
                        email = email.trim();
                }
        }
}
