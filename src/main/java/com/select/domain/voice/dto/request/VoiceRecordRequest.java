package com.select.domain.voice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VoiceRecordRequest(

        @NotBlank(message = "변환된 텍스트를 입력해 주세요.")
        @Size(max = 10000, message = "변환된 텍스트는 10000자 이하여야 합니다.")
        String transcript,

        // TODO: family_members 도메인/JWT 연동 후 클라이언트 입력 대신 인증된 가족 구성원 ID를 사용해야 합니다.
        @NotNull(message = "가족 구성원 ID를 입력해 주세요.")
        @Positive(message = "가족 구성원 ID는 양수여야 합니다.")
        Long familyMemberId
) {
}
