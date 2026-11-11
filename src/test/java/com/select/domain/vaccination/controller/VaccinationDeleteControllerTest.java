package com.select.domain.vaccination.controller;

import com.select.domain.vaccination.service.VaccinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

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
            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();

            context.setAuthentication(createAuthentication());

            SecurityContextHolder.setContext(context);

            return request;
        };
    }

    @Test
    void DELETE_VACCINATION_SCHEDULE_SUCCESS() throws Exception {
        mockMvc.perform(delete("/babies/1/vaccination-schedules/10")
                        .with(authenticated()))
                .andExpect(status().isNoContent());

        verify(vaccinationService)
                .deleteVaccinationSchedule(1L, 10L,10L);
    }
}