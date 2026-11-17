package com.select.domain.report.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateDailyReportRequest(
        @NotNull LocalDate reportDate,
        @NotBlank String summary
) {
}