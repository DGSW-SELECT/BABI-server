package com.select.domain.vaccination.controller;

import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.service.VaccinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
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
}
