package com.select.domain.vaccination.dto.request;

import jakarta.validation.constraints.NotNull;

public record VaccinationScheduleUpdateRequest(
    @NotNull(message = "접종 완료 여부는 필수입니다")
    Boolean completed
) {
}
