package com.select.domain.handover.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HandoverBriefingCreateRequest(
        @NotNull
        LocalDate briefingDate,

        @NotNull
        LocalDateTime periodStart,

        @NotNull
        LocalDateTime periodEnd
) {

    @AssertTrue(message = "조회 시작 시각은 종료 시각보다 빨라야 합니다.")
    public boolean isPeriodValid() {
        return periodStart == null
                || periodEnd == null
                || periodStart.isBefore(periodEnd);
    }
}
