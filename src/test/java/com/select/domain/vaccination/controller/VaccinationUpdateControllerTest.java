package com.select.domain.vaccination.controller;

import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.service.VaccinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VaccinationUpdateControllerTest {
    private MockMvc mockMvc;
    private VaccinationService vaccinationService;

    @BeforeEach
    void setUp() {
        vaccinationService = mock(VaccinationService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new VaccinationController(vaccinationService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }
    private Authentication createAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "10",
                null,
                Collections.emptyList()
        );
    }
    private RequestPostProcessor authenticated() {
        return request -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(createAuthentication());
            SecurityContextHolder.setContext(context);
            return request;
        };
    }

    @Test
    void UPDATE_VACCINATION_SCHEDULE_SUCCESS() throws Exception {

        VaccinationScheduleUpdateResponse response =
                new VaccinationScheduleUpdateResponse(
                        10L,
                        1L,
                        3L,
                        LocalDate.of(2026, 12, 15),
                        null,
                        "",
                        "",
                        false
                );

        when(vaccinationService.updateVaccinationSchedule(
                eq(1L),
                eq(10L),
                eq(10L),
                any(VaccinationScheduleUpdateRequest.class)
        )).thenReturn(response);

        mockMvc.perform(patch(
                        "/babies/1/vaccination-schedules/10"
                ).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "vaccineId": 3,
                        "scheduledDate": "2026-12-15"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vaccinationScheduleId").value(10))
                .andExpect(jsonPath("$.babyId").value(1))
                .andExpect(jsonPath("$.vaccineId").value(3))
                .andExpect(jsonPath("$.scheduledDate")
                        .value("2026-12-15"))
                .andExpect(jsonPath("$.completed").value(false));

        verify(vaccinationService).updateVaccinationSchedule(
                eq(1L),
                eq(10L),
                eq(10L),
                any(VaccinationScheduleUpdateRequest.class)
        );
    }

}