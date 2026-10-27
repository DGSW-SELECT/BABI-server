package com.select.domain.vaccination.controller;

import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleCreateResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.service.VaccinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;


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

        mockMvc.perform(get("/babies/1/vaccination-schedules")
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
        mockMvc.perform(get("/babies/1/vaccination-schedules")
                .param("from", "잘못된 날짜")
                .param("to","2026-12-31"))
                .andExpect(status().isBadRequest());
    }
    @Test
    void VACCINATION_SCHEDULES_SUCCESS() throws Exception {
        when(vaccinationService.getVaccinationSchedules(
                1L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        )).thenReturn(
                new VaccinationScheduleResponse(1L, List.of())
        );

        mockMvc.perform(get("/babies/1/vaccination-schedules")
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.babyId").value(1))
                .andExpect(jsonPath("$.vaccinationSchedules").isArray())
                .andExpect(jsonPath("$.vaccinationSchedules").isEmpty());

        verify(vaccinationService).getVaccinationSchedules(
                1L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
    }

    @Test
    void VACCINATION_SCHEDULE_POST_SUCCESS() throws Exception {
        LocalDate scheduleDate = LocalDate.of(2099, 11, 1);

        when(vaccinationService.createVaccinationSchedule(
                eq(1L),
                any(VaccinationScheduleCreateRequest.class)
        )).thenReturn(
                new VaccinationScheduleCreateResponse(
                        10L,
                        1L,
                        3L,
                        "B형간염",
                        1,
                        scheduleDate,
                        null,
                        false
                )
        );

        mockMvc.perform(post("/babies/1/vaccination-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "vaccineId": 3,
                                    "scheduleDate": "2099-11-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.babyId").value(1))
                .andExpect(jsonPath("$.vaccineId").value(3))
                .andExpect(jsonPath("$.vaccineName").value("B형간염"))
                .andExpect(jsonPath("$.doseNumber").value(1))
                .andExpect(jsonPath("$.scheduleDate").value("2099-11-01"))
                .andExpect(jsonPath("$.completed").value(false));

        verify(vaccinationService).createVaccinationSchedule(
                eq(1L),
                any(VaccinationScheduleCreateRequest.class)
        );
    }

    @Test
    void VACCINE_ID_NONE_RETURN_400() throws Exception {
        mockMvc.perform(post("/babies/1/vaccination-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "scheduleDate": "2099-11-01"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vaccinationService);
    }

    @Test
    void VACCINE_ID_NO_POSITIVE_NUM_RETURN_400() throws Exception {
        mockMvc.perform(post("/babies/1/vaccination-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "vaccineId": 0,
                                    "scheduleDate": "2099-11-01"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vaccinationService);
    }

    @Test
    void VACCINATION_SCHEDULES_OMMITED_RETURN_400() throws Exception {
        mockMvc.perform(post("/babies/1/vaccination-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "vaccineId": 3
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vaccinationService);
    }

    @Test
    void VACCINATION_SCHEDULE_PATCH_SUCCESS() throws Exception {
        // given
        Long babyId = 1L;
        Long scheduleId = 10L;
        LocalDate scheduleDate = LocalDate.of(2026, 11, 10);
        LocalDate completedDate = LocalDate.now();

        when(vaccinationService.updateVaccinationSchedule(
                eq(babyId),
                eq(scheduleId),
                any(VaccinationScheduleUpdateRequest.class)
        )).thenReturn(
                new VaccinationScheduleUpdateResponse(
                        scheduleId,
                        babyId,
                        3L,
                        scheduleDate,
                        completedDate,
                        true
                )
        );

        // when & then
        mockMvc.perform(patch(
                        "/babies/{babyId}/vaccination-schedules/{vaccinationScheduleId}",
                        babyId, scheduleId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "completed": true
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vaccinationScheduleId")
                        .value(scheduleId))
                .andExpect(jsonPath("$.babyId").value(babyId))
                .andExpect(jsonPath("$.vaccineId").value(3))
                .andExpect(jsonPath("$.scheduledDate")
                        .value("2026-11-10"))
                .andExpect(jsonPath("$.completedDate")
                        .value(completedDate.toString()))
                .andExpect(jsonPath("$.completed").value(true));

        verify(vaccinationService).updateVaccinationSchedule(
                eq(babyId),
                eq(scheduleId),
                any(VaccinationScheduleUpdateRequest.class)
        );
    }
}
