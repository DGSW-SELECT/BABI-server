package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleSuccessRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VaccinationCompleteServiceImplTest {

    private BabyVaccinationRepository repository;
    private VaccinationServiceImpl service;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;
    private BabyMemberRepository babyMemberRepository;
    private BabyMember babyMember;
    private Clock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final Long babyId = 1L;
    private final Long userId = 10L;
    @BeforeEach
    void setUp() {
        repository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyMemberRepository = mock(BabyMemberRepository.class);
        babyMember = mock(BabyMember.class);

        clock = Clock.fixed(
                TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")
        );

        service = new VaccinationServiceImpl(repository, vaccineRepository, babyRepository, babyMemberRepository, clock);
    }

    private void mockViewerAccess() {
        when(babyRepository.existsById(babyId)).thenReturn(true);

        when(babyMemberRepository.findByBabyIdAndUserId(babyId, userId))
                .thenReturn(Optional.of(babyMember));

        when(babyMember.getRole()).thenReturn(BabyRole.VIEWER);
    }
    private void mockWriteAccess() {
        when(babyRepository.existsById(babyId)).thenReturn(true);

        when(babyMemberRepository.findByBabyIdAndUserId(babyId, userId))
                .thenReturn(Optional.of(babyMember));

        when(babyMember.getRole()).thenReturn(BabyRole.GUARDIAN);
    }

    @Test
// 완료일을 지정하지 않으면 오늘 날짜로 완료 처리
    void COMPLETE_VACCINATION_WITHOUT_DATE_SUCCESS() {
        Long scheduleId = 10L;

        Vaccine vaccine = mock(Vaccine.class);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId, vaccine, TODAY, null, null, null
        );

        mockWriteAccess();

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleSuccessRequest(true, null);

        VaccinationScheduleUpdateResponse response =
                service.successVaccinationSchedule(
                        babyId, userId, scheduleId, request
                );

        assertTrue(response.completed());
        assertEquals(TODAY, response.completedDate());
        assertTrue(vaccination.getCompleted());
        assertEquals(TODAY, vaccination.getCompletedDate());
    }
    @Test
// 미래 날짜로 접종 완료 처리 불가
    void COMPLETE_VACCINATION_WITH_FUTURE_DATE_FAIL() {
        Long scheduleId = 10L;

        Vaccine vaccine = mock(Vaccine.class);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId, vaccine, TODAY, null, null, null
        );

        mockWriteAccess();

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleSuccessRequest(
                true, TODAY.plusDays(1)
        );

        assertThrows(
                BusinessException.class,
                () -> service.successVaccinationSchedule(
                        babyId, userId, scheduleId, request
                )
        );

        assertFalse(vaccination.getCompleted());
    }
    @Test
// 이미 완료된 접종을 다시 완료 처리해도 기존 완료일 유지
    void ALREADY_COMPLETED_VACCINATION_DATE_NOT_CHANGED() {
        Long scheduleId = 10L;
        LocalDate originalCompletedDate = TODAY.minusDays(5);

        Vaccine vaccine = mock(Vaccine.class);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId,
                vaccine,
                TODAY,
                null,
                null,
                null
        );

        vaccination.complete(originalCompletedDate);

        mockWriteAccess();

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleSuccessRequest(
                true, TODAY.minusDays(2)
        );

        VaccinationScheduleUpdateResponse response =
                service.successVaccinationSchedule(
                        babyId, userId, scheduleId, request
                );

        assertTrue(response.completed());
        assertEquals(originalCompletedDate, response.completedDate());
        assertEquals(originalCompletedDate, vaccination.getCompletedDate());
    }
    @Test
// 완료 취소 시 완료 여부와 완료일 초기화
    void CANCEL_VACCINATION_COMPLETION_SUCCESS() {
        Long scheduleId = 10L;

        Vaccine vaccine = mock(Vaccine.class);

        BabyVaccination vaccination = BabyVaccination.create(
                babyId,
                vaccine,
                TODAY,
                null,
                null,
                null
        );

        vaccination.complete(TODAY.minusDays(3));

        mockWriteAccess();

        when(repository.findById(scheduleId))
                .thenReturn(Optional.of(vaccination));

        var request = new VaccinationScheduleSuccessRequest(false, null);

        VaccinationScheduleUpdateResponse response =
                service.successVaccinationSchedule(
                        babyId, userId, scheduleId, request
                );

        assertFalse(response.completed());
        assertNull(response.completedDate());
        assertFalse(vaccination.getCompleted());
        assertNull(vaccination.getCompletedDate());
    }
}