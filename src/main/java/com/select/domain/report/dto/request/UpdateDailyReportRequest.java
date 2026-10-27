package com.select.domain.report.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateDailyReportRequest(
        @NotBlank String summary
) {
}