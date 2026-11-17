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
           Long userId,
           LocalDate from,
           LocalDate to
    );
    VaccinationScheduleCreateResponse createVaccinationSchedule(
            Long babyId,
            Long userId,
            VaccinationScheduleCreateRequest request
    );
    VaccinationScheduleUpdateResponse successVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId,
            VaccinationScheduleSuccessRequest request
    );
    VaccinationHistoryResponse getVaccinationHistory(Long babyId, Long userId);
    void deleteVaccinationSchedule(Long BabyId, Long userId, Long vaccinationScheduleId);
    VaccinationScheduleUpdateResponse updateVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId,
            VaccinationScheduleUpdateRequest request
    );
}
