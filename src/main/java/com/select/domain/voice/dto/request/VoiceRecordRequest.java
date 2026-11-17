package com.select.domain.voice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoiceRecordRequest(

        @NotBlank(message = "변환된 텍스트를 입력해 주세요.")
        @Size(max = 10000, message = "변환된 텍스트는 10000자 이하여야 합니다.")
        String transcript
) {
}
