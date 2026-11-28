package com.select.domain.care.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateSleepRequest(
        @NotNull LocalDateTime occurredAt,
        LocalDateTime sleepEndAt,
        @Size(max = 5000) String note
) {
}
