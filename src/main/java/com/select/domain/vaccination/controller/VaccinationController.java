package com.select.domain.vaccination.controller;

import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.dto.response.VaccinationHistoryResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleCreateResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.service.VaccinationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class VaccinationController {

    private final VaccinationService vaccinationService;

    @GetMapping("/babies/{babyId}/vaccination-schedules")
    public VaccinationScheduleResponse getVaccinationSchedules(
            @PathVariable Long babyId,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        return vaccinationService.getVaccinationSchedules(
                babyId,
                from,
                to
        );
    }

    @PostMapping("/babies/{babyId}/vaccination-schedules")
    public ResponseEntity<VaccinationScheduleCreateResponse> createVaccinationSchedule(
            @PathVariable Long babyId,
            @Valid @RequestBody VaccinationScheduleCreateRequest request
    ) {
        VaccinationScheduleCreateResponse response =
                vaccinationService.createVaccinationSchedule(babyId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @PatchMapping(
            "/babies/{babyId}/vaccination-schedules/{vaccinationScheduleId}"
    )
    public ResponseEntity<VaccinationScheduleUpdateResponse> updateVaccinationSchedule(
            @PathVariable Long babyId,
            @PathVariable Long vaccinationScheduleId,
            @Valid @RequestBody VaccinationScheduleUpdateRequest request
    ) {
        VaccinationScheduleUpdateResponse response =
                vaccinationService.updateVaccinationSchedule(
                        babyId,
                        vaccinationScheduleId,
                        request
                );
        return ResponseEntity.ok(response);
    }
    @GetMapping("/babies/{babyId}/vaccination-history")
    public VaccinationHistoryResponse getVaccinationHistory(
            @PathVariable Long babyId
    ) {
        return vaccinationService.getVaccinationHistory(babyId);
    }
}
