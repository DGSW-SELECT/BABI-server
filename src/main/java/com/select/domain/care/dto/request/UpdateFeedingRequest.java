package com.select.domain.care.dto.request;

import com.select.domain.care.entity.FeedingType;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpdateFeedingRequest(
        LocalDateTime occurredAt,
        FeedingType feedingType,
        @PositiveOrZero Integer amountMl,
        @Size(max = 5000) String note
) {
}
