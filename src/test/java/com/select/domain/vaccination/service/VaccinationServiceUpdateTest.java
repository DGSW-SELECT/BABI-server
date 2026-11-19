package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VaccinationServiceUpdateTest {

    private BabyVaccinationRepository repository;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;
    private BabyAccessRepository babyAccessRepository;
    private VaccinationTransactionService transService;
    private VaccinationServiceImpl service;

    private final Long babyId = 1L;
    private final Long userId = 10L;
    private final Long scheduleId = 10L;

    @BeforeEach
    void setUp() {
        repository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyAccessRepository = mock(BabyAccessRepository.class);
        transService = mock(VaccinationTransactionService.class);

        Clock clock = Clock.fixed(
                Instant.parse("2026-11-10T03:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        service = new VaccinationServiceImpl(
                repository, vaccineRepository, babyRepository,
                babyAccessRepository, clock, transService
        );
    }

    private void mockWriteAccess() {
        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(babyAccessRepository.findMemberId(
                userId, babyId, BabyPermission.VACCINATION_WRITE, true
        )).thenReturn(Optional.of(100L));
    }

    private VaccinationScheduleUpdateRequest request() {
        return new VaccinationScheduleUpdateRequest(
                3L, 1, LocalDate.of(2026, 12, 15), "어디든지", "많이 아픔"
        );
    }

    private VaccinationScheduleUpdateResponse response() {
        return new VaccinationScheduleUpdateResponse(
                scheduleId, babyId, 3L, "BCG", 1,
                LocalDate.of(2026, 12, 15), null,
                "어디든지", "많이 아픔", false
        );
    }

    @Test
    void UPDATE_VACCINATION_SCHEDULE_SUCCESS() {
        var request = request();
        var expected = response();

        mockWriteAccess();
        when(transService.updateVaccinationSchedule(babyId, userId, scheduleId, request))
                .thenReturn(expected);

        var actual = service.updateVaccinationSchedule(babyId, userId, scheduleId, request);

        assertSame(expected, actual);
        verify(transService).updateVaccinationSchedule(babyId, userId, scheduleId, request);
    }

    @Test
    void UPDATE_SCHEDULE_INVALID_BABY_ID() {
        assertThrows(BusinessException.class,
                () -> service.updateVaccinationSchedule(0L, userId, scheduleId, request()));

        verifyNoInteractions(transService);
    }

    @Test
    void UPDATE_SCHEDULE_BABY_NOT_FOUND() {
        when(babyRepository.existsById(babyId)).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> service.updateVaccinationSchedule(babyId, userId, scheduleId, request()));

        verifyNoInteractions(transService);
    }

    @Test
    void UPDATE_SCHEDULE_ACCESS_DENIED() {
        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(babyAccessRepository.findMemberId(
                userId, babyId, BabyPermission.VACCINATION_WRITE, true
        )).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> service.updateVaccinationSchedule(babyId, userId, scheduleId, request()));

        verifyNoInteractions(transService);
    }

    @Test
    void UPDATE_SCHEDULE_TRANS_SERVICE_EXCEPTION_PROPAGATES() {
        mockWriteAccess();
        var thrown = new BusinessException(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND);
        when(transService.updateVaccinationSchedule(
                eq(babyId), eq(userId), eq(scheduleId), any()
        )).thenThrow(thrown);

        var ex = assertThrows(BusinessException.class,
                () -> service.updateVaccinationSchedule(babyId, userId, scheduleId, request()));

        assertSame(thrown, ex);
    }
}
