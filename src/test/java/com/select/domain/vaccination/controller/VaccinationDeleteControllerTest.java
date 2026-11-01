package com.select.domain.vaccination.controller;

import com.select.domain.vaccination.service.VaccinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VaccinationDeleteControllerTest {
    private MockMvc mockMvc;
    private VaccinationService vaccinationService;

    @BeforeEach
    void setUp() {
        vaccinationService = mock(VaccinationService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new VaccinationController(vaccinationService)).build();
    }

    @Test
    void DELETE_VACCINATION_SCHEDULE_SUCCESS() throws Exception {
        mockMvc.perform(
                delete("/babies/1/vaccination-schedules/10")
        ).andExpect(status().isNoContent());

        verify(vaccinationService)
                .deleteVaccinationSchedule(1L, 10L);
    }
}