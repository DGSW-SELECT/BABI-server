package com.select.domain.vaccination.service;

import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleSuccessRequest;
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
    VaccinationScheduleUpdateResponse successVaccinationSchedule(
            Long babyId,
            Long vaccinationScheduleId,
            VaccinationScheduleSuccessRequest request
    );
    VaccinationHistoryResponse getVaccinationHistory(Long babyId);
    void deleteVaccinationSchedule(Long BabyId, Long vaccinationScheduleId);
    VaccinationScheduleUpdateResponse updateVaccinationSchedule(
            Long babyId,
            Long vaccinationScheduleId,
            VaccinationScheduleUpdateRequest request
    );
}
