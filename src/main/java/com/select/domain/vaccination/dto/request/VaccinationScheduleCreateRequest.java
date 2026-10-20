package com.select.domain.vaccination.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record VaccinationScheduleCreateRequest(
        @NotNull(message = "백신 ID는 필수입니다.")
        @Positive(message = "백신 ID는 양수여야합니다.")
        Long vaccineId,

        @NotNull(message = "접종 예정일은 필수입니다.")
        LocalDate scheduleDate
) {

}
