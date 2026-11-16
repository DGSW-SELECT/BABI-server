package com.select.domain.vaccination.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VaccinationScheduleCreateRequest(
        @NotNull(message = "백신 ID는 필수입니다.")
        @Positive(message = "백신 ID는 양수여야합니다.")
        Long vaccineId,

        @NotNull(message = "접종 차수는 필수입니다.")
        @Positive(message = "접종 차수는 양수여야합니다.")
        Integer doseNumber,

        @NotNull(message = "접종 예정일은 필수입니다.")
        LocalDate scheduledDate,

        @Size(max = 255, message = "기관명은 255자 이하여야 합니다.")
        String institution,

        @Size(max = 255, message = "메모는 255자 이하여야 합니다.")
        String memo
) {

}
