package com.select.domain.vaccination.controller;

import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.service.VaccinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class VaccinationControllerTest {
    private MockMvc mockMvc;
    private VaccinationService vaccinationService;

    @BeforeEach
    void setUp() {
        vaccinationService = mock(VaccinationService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new VaccinationController(vaccinationService)).build();
    }

    @Test
    void GOOD_OFFER_RETURN_VACCINATION_SCHEDULE() throws Exception {
        LocalDate from = LocalDate.of(2026,1,1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        VaccinationScheduleResponse response =
                new VaccinationScheduleResponse(
                        1L,
                        List.of()
                );

        when(vaccinationService.getVaccinationSchedules(
                1L, from, to
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/babies/1/vaccination-schedules")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.babyId").value(1))
                .andExpect(jsonPath("$.vaccinationSchedules").isArray());
        verify(vaccinationService).getVaccinationSchedules(
                1L,from,to
        );
    }
    @Test
    void BAD_DATETIME_RETURN_400() throws Exception {
        mockMvc.perform(get("/api/v1/babies/1/vaccination-schedules")
                .param("from", "잘못된 날짜")
                .param("to","2026-12-31"))
                .andExpect(status().isBadRequest());
    }
}