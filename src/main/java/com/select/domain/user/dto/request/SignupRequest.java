package com.select.domain.user.dto.request;

import jakarta.validation.constraints.*;

public record SignupRequest(

        @Email
        @Size(max = 320)
        @NotBlank
        String email,

        @NotBlank
        @Size(min = 8,max = 20, message = "비밀번호는 최소 8자 이상 20자 이하여야 합니다.")
        @Pattern(
                regexp = "^[!-~]+$",
                message = "비밀번호는 영문, 숫자, 특수문자만 입력할 수 있습니다."
        )
        String password,

        @Size(max = 20)
        @NotBlank
        String passwordConfirm,

        @NotBlank
        @Size(max = 50)
        String nickname

) {
    public SignupRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
