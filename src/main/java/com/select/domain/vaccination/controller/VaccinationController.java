package com.select.domain.vaccination.controller;

import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleSuccessRequest;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class VaccinationController {

    private final VaccinationService vaccinationService;

    @GetMapping("/babies/{babyId}/vaccination-schedules")
    public VaccinationScheduleResponse getVaccinationSchedules(
            @PathVariable Long babyId,

            @AuthenticationPrincipal String principal,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        Long userId = Long.parseLong(principal);

        return vaccinationService.getVaccinationSchedules(
                babyId,
                userId,
                from,
                to
        );
    }

    @PostMapping("/babies/{babyId}/vaccination-schedules")
    public ResponseEntity<VaccinationScheduleCreateResponse> createVaccinationSchedule(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @Valid @RequestBody VaccinationScheduleCreateRequest request
    ) {
        Long userId = Long.parseLong(principal);

        VaccinationScheduleCreateResponse response =
                vaccinationService.createVaccinationSchedule(babyId, userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @PatchMapping(
            "/babies/{babyId}/vaccination-schedules/{vaccinationScheduleId}/completed"
    )
    public ResponseEntity<VaccinationScheduleUpdateResponse> updateVaccinationSchedule(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @PathVariable Long vaccinationScheduleId,
            @Valid @RequestBody VaccinationScheduleSuccessRequest request
    ) {
        Long userId = Long.parseLong(principal);

        VaccinationScheduleUpdateResponse response =
                vaccinationService.successVaccinationSchedule(
                        babyId,
                        userId,
                        vaccinationScheduleId,
                        request
                );
        return ResponseEntity.ok(response);
    }
    @GetMapping("/babies/{babyId}/vaccination-history")
    public VaccinationHistoryResponse getVaccinationHistory(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal
    ) {
        Long userId = Long.parseLong(principal);

        return vaccinationService.getVaccinationHistory(babyId, userId);
    }
    @DeleteMapping("/babies/{babyId}/vaccination-schedules/{vaccinationScheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVaccinationSchedule(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @PathVariable Long vaccinationScheduleId
    ) {
        Long userId = Long.parseLong(principal);

        vaccinationService.deleteVaccinationSchedule(
                babyId, userId, vaccinationScheduleId
        );
    }
    @PatchMapping("/babies/{babyId}/vaccination-schedules/{vaccinationScheduleId}")
    public ResponseEntity<VaccinationScheduleUpdateResponse> updateVaccinationSchedule(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @PathVariable Long vaccinationScheduleId,
            @Valid @RequestBody VaccinationScheduleUpdateRequest request
    ) {
        Long userId = Long.parseLong(principal);

        VaccinationScheduleUpdateResponse response =
                vaccinationService.updateVaccinationSchedule(
                        babyId,
                        userId,
                        vaccinationScheduleId,
                        request
                );
        return ResponseEntity.ok(response);
    }
}
