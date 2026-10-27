package com.select.domain.vaccination.service;

import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.dto.response.VaccinationHistoryResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleCreateResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;

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
    VaccinationScheduleUpdateResponse updateVaccinationSchedule(
            Long babyId,
            Long vaccinationScheduleId,
            VaccinationScheduleUpdateRequest request
    );
    VaccinationHistoryResponse getVaccinationHistory(Long babyId);
}
