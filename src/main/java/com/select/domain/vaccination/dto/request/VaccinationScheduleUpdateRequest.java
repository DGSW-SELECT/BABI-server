package com.select.domain.vaccination.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class VaccinationScheduleUpdateRequest {

    @NotNull(message = "백신 ID는 필수입니다.")
    private Long vaccineId;

    @NotNull(message = "접종 예방일은 필수입니다.")
    private LocalDate scheduledDate;
}
