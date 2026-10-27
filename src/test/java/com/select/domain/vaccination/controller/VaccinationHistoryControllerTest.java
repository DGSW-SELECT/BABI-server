package com.select.domain.vaccination.controller;

import com.select.domain.vaccination.dto.response.VaccinationHistoryResponse;
import com.select.domain.vaccination.service.VaccinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VaccinationHistoryControllerTest {

    private MockMvc mockMvc;
    private VaccinationService vaccinationService;

    @BeforeEach
    void setUp() {
        vaccinationService = mock(VaccinationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VaccinationController(vaccinationService))
                .build();
    }

    @Test
    void VACCINATION_HISTORY_GET_SUCCESS() throws Exception {
        // given
        Long babyId = 1L;
        VaccinationHistoryResponse response = new VaccinationHistoryResponse(
                babyId,
                List.of(new VaccinationHistoryResponse.Item(
                        10L,
                        "DTaP",
                        1,
                        LocalDate.of(2026, 9, 20)
                ))
        );
        when(vaccinationService.getVaccinationHistory(babyId)).thenReturn(response);

        // when / then
        mockMvc.perform(get("/babies/{babyId}/vaccination-history", babyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.babyId").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].vaccinationScheduleId").value(10))
                .andExpect(jsonPath("$.items[0].vaccineName").value("DTaP"))
                .andExpect(jsonPath("$.items[0].doseNumber").value(1))
                .andExpect(jsonPath("$.items[0].completedDate").value("2026-09-20"));

        verify(vaccinationService).getVaccinationHistory(babyId);
    }

    @Test
    void VACCINATION_HISTORY_EMPTY_RETURN_EMPTY_ITEMS() throws Exception {
        // given
        Long babyId = 1L;
        when(vaccinationService.getVaccinationHistory(babyId))
                .thenReturn(new VaccinationHistoryResponse(babyId, List.of()));

        // when / then
        mockMvc.perform(get("/babies/{babyId}/vaccination-history", babyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.babyId").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));

        verify(vaccinationService).getVaccinationHistory(babyId);
    }
}