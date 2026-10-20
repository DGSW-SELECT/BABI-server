package com.select.domain.vaccination.dto.response;

import java.time.LocalDate;

public record VaccinationScheduleUpdateResponse (
        Long vaccinationScheduleId,
        Long babyId,
        Long vaccineId,
        LocalDate scheduledDate,
        LocalDate completedDate,
        Boolean completed
){
}
