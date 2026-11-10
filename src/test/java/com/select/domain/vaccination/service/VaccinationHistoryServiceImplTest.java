package com.select.domain.vaccination.service;

import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.response.VaccinationHistoryResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaccinationHistoryServiceImplTest {

    private BabyVaccinationRepository babyVaccinationRepository;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;
    private BabyMemberRepository babyMemberRepository;
    private VaccinationServiceImpl service;

    private final Long babyId = 1L;
    private final Long userId = 10L;

    @BeforeEach
    void setUp() {
        babyVaccinationRepository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyMemberRepository = mock(BabyMemberRepository.class);

        service = new VaccinationServiceImpl(
                babyVaccinationRepository,
                vaccineRepository,
                babyRepository,
                babyMemberRepository
        );
    }

    private void mockBabyAccess() {
        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(babyMemberRepository.existsByBabyIdAndUserId(babyId, userId))
                .thenReturn(true);
    }

    @Test
    void VACCINATION_HISTORY_SUCCESS() {
        // given
        LocalDate completedDate = LocalDate.of(2026, 9, 20);

        Vaccine vaccine = mock(Vaccine.class);
        when(vaccine.getName()).thenReturn("DTaP");
        when(vaccine.getDoseNumber()).thenReturn(1);

        BabyVaccination vaccination = mock(BabyVaccination.class);
        when(vaccination.getId()).thenReturn(10L);
        when(vaccination.getVaccine()).thenReturn(vaccine);
        when(vaccination.getCompletedDate()).thenReturn(completedDate);

        mockBabyAccess();
        when(babyVaccinationRepository.findAllByBabyIdAndCompletedTrue(babyId))
                .thenReturn(List.of(vaccination));

        // when
        VaccinationHistoryResponse response = service.getVaccinationHistory(babyId, userId);

        // then
        assertEquals(babyId, response.babyId());
        assertEquals(1, response.items().size());
        assertEquals(10L, response.items().get(0).vaccinationScheduleId());
        assertEquals("DTaP", response.items().get(0).vaccineName());
        assertEquals(1, response.items().get(0).doseNumber());
        assertEquals(completedDate, response.items().get(0).completedDate());

        verify(babyRepository).existsById(babyId);
        verify(babyVaccinationRepository).findAllByBabyIdAndCompletedTrue(babyId);
}

@Test
void VACCINATION_HISTORY_EMPTY() {
    mockBabyAccess();
    // given
    when(babyVaccinationRepository.findAllByBabyIdAndCompletedTrue(babyId))
            .thenReturn(List.of());

    // when
    VaccinationHistoryResponse response = service.getVaccinationHistory(babyId, userId);

    // then
    assertEquals(babyId, response.babyId());
    assertNotNull(response.items());
    assertTrue(response.items().isEmpty());

    verify(babyVaccinationRepository).findAllByBabyIdAndCompletedTrue(babyId);
}

@Test
void VACCINATION_HISTORY_BABY_NOT_FOUND() {
    // given
    Long babyId = 999L;

    mockBabyAccess();

    // when / then
    assertThrows(
            BusinessException.class,
            () -> service.getVaccinationHistory(babyId, userId)
    );

    verify(babyRepository).existsById(babyId);
    verifyNoInteractions(babyVaccinationRepository);
}
}