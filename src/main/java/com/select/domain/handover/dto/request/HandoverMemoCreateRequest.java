package com.select.domain.handover.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HandoverMemoCreateRequest(
        @NotBlank(message = "메모 내용을 입력해 주세요.")
        @Size(max = 1000, message = "메모는 1000자 이하로 입력해 주세요.")
        String content
) {
}
