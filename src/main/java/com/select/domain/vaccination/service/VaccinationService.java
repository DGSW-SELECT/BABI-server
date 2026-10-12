package com.select.domain.vaccination.service;

import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;

import java.time.LocalDate;

public interface VaccinationService {

    VaccinationScheduleResponse getVaccinationSchedules(
           Long babyId,
           LocalDate from,
           LocalDate to
    );
}
