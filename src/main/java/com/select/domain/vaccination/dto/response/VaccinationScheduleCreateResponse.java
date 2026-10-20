package com.select.domain.vaccination.dto.response;

import java.time.LocalDate;

public record VaccinationScheduleCreateResponse(
        Long id,
        Long babyId,
        Long vaccineId,
        String vaccineName,
        Integer doseNumber,
        LocalDate scheduleDate,
        LocalDate completedDate,
        boolean completed
) {

}
