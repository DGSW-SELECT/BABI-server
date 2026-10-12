package com.select.domain.vaccination.dto.response;

import java.util.List;

public record VaccinationScheduleResponse(
        Long babyId,
        List<VaccinationScheduleItemResponse> vaccinationSchedules
) {
}
