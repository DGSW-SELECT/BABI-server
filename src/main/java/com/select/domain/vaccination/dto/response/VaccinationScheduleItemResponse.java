package com.select.domain.vaccination.dto.response;

import java.time.LocalDate;

public record VaccinationScheduleItemResponse(
        Long babyVaccinationId,
        Long vaccineId,
        String vaccineName,
        Integer doseNumber,
        Integer recommendedStartMonth,
        Integer recommendedEndMonth,
        LocalDate scheduledDate,
        LocalDate completedDate,
        Boolean completed
){
}
