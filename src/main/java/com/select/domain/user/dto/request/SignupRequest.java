package com.select.domain.user.dto.request;

import jakarta.validation.constraints.*;

import java.nio.charset.StandardCharsets;

public record SignupRequest(

        @Email
        @Size(max = 320)
        @NotBlank
        String email,

        @NotBlank
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
        @Pattern(
                regexp = "^[!-~]+$",
                message = "비밀번호는 영문, 숫자, 특수문자만 입력할 수 있습니다."
        )
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
