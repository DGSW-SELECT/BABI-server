package com.select.domain.vaccination.dto.request;

import jakarta.validation.constraints.NotNull;


import java.time.LocalDate;

public record VaccinationScheduleUpdateRequest (
        @NotNull(message = "백신 ID는 필수입니다.")
        Long vaccineId,

        @NotNull(message = "접종 예방일은 필수입니다.")
        LocalDate scheduledDate
){
}
