package com.select.domain.vaccination.dto.response;

import java.time.LocalDate;

public record VaccinationScheduleUpdateResponse(
        Long vaccinationScheduleId,
        Long babyId,
        Long vaccineId,
        String vaccineName,
        Integer doseNumber,
        LocalDate scheduledDate,
        LocalDate completedDate,
        String institution,
        String memo,
        Boolean completed
){
}
