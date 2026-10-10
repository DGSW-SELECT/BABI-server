package com.select.domain.user.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

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
    @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordWithin72Bytes() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
