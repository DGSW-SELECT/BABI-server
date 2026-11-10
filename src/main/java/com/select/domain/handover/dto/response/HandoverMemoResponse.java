package com.select.domain.handover.dto.response;

import java.time.LocalDateTime;

public record HandoverMemoResponse(
        Long memoId,
        Long authorMemberId,
        String content,
        LocalDateTime createdAt
) {
}
