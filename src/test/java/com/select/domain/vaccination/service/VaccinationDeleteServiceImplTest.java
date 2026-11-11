package com.select.domain.vaccination.service;

import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaccinationDeleteServiceImplTest {
    private BabyVaccinationRepository repository;
    private VaccinationServiceImpl service;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;
    private BabyMemberRepository babyMemberRepository;
    private Clock clock;

    private final Long babyId = 1L;
    private final Long userId = 10L;
    private final Long scheduleId = 10L;

    @BeforeEach
    void setUp() {
        repository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyMemberRepository = mock(BabyMemberRepository.class);

        clock = Clock.fixed(
                Instant.parse("2026-11-10T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        service = new VaccinationServiceImpl(repository, vaccineRepository, babyRepository, babyMemberRepository, clock);
    }
    private void mockBabyAccess() {
        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(babyMemberRepository.existsByBabyIdAndUserId(babyId, userId))
                .thenReturn(true);
    }
    @Test
    void DELETE_INCOMPLETE_SCHEDULE_SUCCESS() {

        BabyVaccination vaccination = mock(BabyVaccination.class);

        mockBabyAccess();

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(babyId);

        service.deleteVaccinationSchedule(babyId, userId, scheduleId);

        verify(repository).delete(vaccination);
    }

    @Test
    void DELETE_COMPLETED_SCHEDULE_SUCCESS() {

        BabyVaccination vaccination = mock(BabyVaccination.class);
        mockBabyAccess();

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(babyId);
        when(vaccination.getCompleted()).thenReturn(true);

        service.deleteVaccinationSchedule(babyId, userId, scheduleId);

        verify(repository).delete(vaccination);
    }
    @Test
    void DELETE_SCHEDULE_BABY_NOT_FOUND() {

        when(babyRepository.existsById(babyId)).thenReturn(false);

        assertThrows(
                BusinessException.class,
                () -> service.deleteVaccinationSchedule(babyId, userId, scheduleId)
        );

        verify(repository, never()).delete(any(BabyVaccination.class));
    }
    @Test
    void DELETE_SCHEDULE_NOT_FOUND() {

        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(repository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> service.deleteVaccinationSchedule(babyId, userId, scheduleId)
        );

        verify(repository, never()).delete(any(BabyVaccination.class));
    }
    @Test
    void DELETE_OTHER_BABY_SCHEDULE_FAIL() {
        Long babyId = 1L;
        Long scheduleId = 10L;

        BabyVaccination vaccination = mock(BabyVaccination.class);

        mockBabyAccess();
        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));
        when(vaccination.getBabyId()).thenReturn(2L);

        assertThrows(
                BusinessException.class,
                () -> service.deleteVaccinationSchedule(babyId, userId, scheduleId)
        );

        verify(repository, never()).delete(any(BabyVaccination.class));
    }
}