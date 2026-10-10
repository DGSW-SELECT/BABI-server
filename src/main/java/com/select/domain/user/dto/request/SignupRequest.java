package com.select.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @Email
        @Size(max = 50)
        @NotBlank
        String email,

        @NotBlank
        String password,

        @NotBlank
        String passwordConfirm,

        @NotBlank
        @Size(max = 50)
        String nickname

) {
}
