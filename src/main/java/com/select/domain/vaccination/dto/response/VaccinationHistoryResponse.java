package com.select.domain.vaccination.dto.response;

import java.time.LocalDate;
import java.util.List;

public record VaccinationHistoryResponse(
        Long babyId,
        List<Item> items
) {
    public record Item(
            Long vaccinationScheduleId,
            String vaccineName,
            Integer doseNumber,
            LocalDate completedDate
    ) {}
}