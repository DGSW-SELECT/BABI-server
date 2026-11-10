package com.select.domain.handover.dto.response;

import java.time.LocalDateTime;

public record HandoverMemoResponse(
        Long memoId,
        String content,
        LocalDateTime createdAt
) {
}
