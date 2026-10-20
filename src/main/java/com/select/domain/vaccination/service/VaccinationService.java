package com.select.domain.vaccination.service;

import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleCreateResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;

import java.time.LocalDate;

public interface VaccinationService {

    VaccinationScheduleResponse getVaccinationSchedules(
           Long babyId,
           LocalDate from,
           LocalDate to
    );
    VaccinationScheduleCreateResponse createVaccinationSchedule(
            Long babyId,
            VaccinationScheduleCreateRequest request
    );
}
