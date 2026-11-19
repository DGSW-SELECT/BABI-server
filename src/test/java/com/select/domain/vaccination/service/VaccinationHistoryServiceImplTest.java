package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.response.VaccinationHistoryResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaccinationHistoryServiceImplTest {

    private BabyVaccinationRepository babyVaccinationRepository;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;
    private BabyAccessRepository babyAccessRepository;
    private VaccinationServiceImpl service;
    private VaccinationTransactionService transService;
    private Clock clock;

    private final Long babyId = 1L;
    private final Long userId = 10L;

    @BeforeEach
    void setUp() {
        babyVaccinationRepository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyAccessRepository = mock(BabyAccessRepository.class);
        transService = mock(VaccinationTransactionService.class);

        clock = Clock.fixed(
                Instant.parse("2026-11-10T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        service = new VaccinationServiceImpl(
                babyVaccinationRepository,
                vaccineRepository,
                babyRepository,
                babyAccessRepository,
                clock,
                transService
        );
    }

    private void mockReadAccess(Long targetBabyId) {
        when(babyRepository.existsById(targetBabyId))
                .thenReturn(true);

        when(babyAccessRepository.findMemberId(
                userId,
                targetBabyId,
                BabyPermission.VACCINATION_READ,
                false
        )).thenReturn(Optional.of(100L));
    }

    @Test
    void VACCINATION_HISTORY_SUCCESS() {
        // given
        LocalDate completedDate = LocalDate.of(2026, 9, 20);

        Vaccine vaccine = mock(Vaccine.class);
        BabyVaccination vaccination = mock(BabyVaccination.class);

        when(vaccine.getName()).thenReturn("DTaP");
        when(vaccination.getId()).thenReturn(10L);
        when(vaccination.getVaccine()).thenReturn(vaccine);
        when(vaccination.getCompletedDate()).thenReturn(completedDate);

        mockReadAccess(babyId);

        when(babyVaccinationRepository
                .findAllByBabyIdAndCompletedTrueOrderByCompletedDateDescIdDesc(babyId))
                .thenReturn(List.of(vaccination));

        // when
        VaccinationHistoryResponse response =
                service.getVaccinationHistory(babyId, userId);

        // then
        assertEquals(babyId, response.babyId());
        assertEquals(1, response.items().size());
        assertEquals(10L, response.items().get(0).vaccinationScheduleId());
        assertEquals("DTaP", response.items().get(0).vaccineName());
        assertEquals(completedDate, response.items().get(0).completedDate());

        verify(babyRepository).existsById(babyId);
        verify(babyAccessRepository).findMemberId(
                userId,
                babyId,
                BabyPermission.VACCINATION_READ,
                false
        );
        verify(babyVaccinationRepository)
                .findAllByBabyIdAndCompletedTrueOrderByCompletedDateDescIdDesc(babyId);
    }

    @Test
    void VACCINATION_HISTORY_EMPTY() {
        // given
        mockReadAccess(babyId);

        when(babyVaccinationRepository
                .findAllByBabyIdAndCompletedTrueOrderByCompletedDateDescIdDesc(babyId))
                .thenReturn(List.of());

        // when
        VaccinationHistoryResponse response =
                service.getVaccinationHistory(babyId, userId);

        // then
        assertEquals(babyId, response.babyId());
        assertNotNull(response.items());
        assertTrue(response.items().isEmpty());

        verify(babyVaccinationRepository)
                .findAllByBabyIdAndCompletedTrueOrderByCompletedDateDescIdDesc(babyId);
    }

    @Test
    void VACCINATION_HISTORY_BABY_NOT_FOUND() {
        // given
        Long notFoundBabyId = 999L;

        when(babyRepository.existsById(notFoundBabyId))
                .thenReturn(false);

        // when / then
        assertThrows(
                BusinessException.class,
                () -> service.getVaccinationHistory(notFoundBabyId, userId)
        );

        verify(babyRepository).existsById(notFoundBabyId);
        verifyNoInteractions(babyVaccinationRepository);
        verifyNoInteractions(babyAccessRepository);
    }
}