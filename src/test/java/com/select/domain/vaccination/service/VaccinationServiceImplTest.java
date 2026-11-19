package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleSuccessRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleCreateResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaccinationServiceImplTest {
    private BabyVaccinationRepository repository;
    private VaccinationServiceImpl service;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;
    private BabyAccessRepository babyAccessRepository;
    private VaccinationTransactionService transService;
    private Clock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final Long babyId = 1L;
    private final Long userId = 10L;

    @BeforeEach
    void setUp() {
        repository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyAccessRepository = mock(BabyAccessRepository.class);
        transService = mock(VaccinationTransactionService.class);

        clock = Clock.fixed(
                TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")
        );

        service = new VaccinationServiceImpl(
                repository, vaccineRepository, babyRepository,
                babyAccessRepository, clock, transService);
    }

    private void mockViewerAccess() {
        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(babyAccessRepository.findMemberId(
                userId, babyId, BabyPermission.VACCINATION_READ, false
        )).thenReturn(Optional.of(100L));
    }

    private void mockWriteAccess() {
        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(babyAccessRepository.findMemberId(
                userId, babyId, BabyPermission.VACCINATION_WRITE, true
        )).thenReturn(Optional.of(100L));
    }

    private void mockWriteAccessDenied() {
        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(babyAccessRepository.findMemberId(
                userId, babyId, BabyPermission.VACCINATION_WRITE, true
        )).thenReturn(Optional.empty());
    }

    private VaccinationScheduleCreateRequest createRequest() {
        return new VaccinationScheduleCreateRequest(
                3L, 1, TODAY.plusDays(1), null, "", "");
    }

    private VaccinationScheduleUpdateResponse updateResponse(boolean completed) {
        return new VaccinationScheduleUpdateResponse(
                10L, babyId, 3L, "BCG", 1,
                TODAY, completed ? TODAY : null,
                "병원", "메모", completed);
    }

    @Test
        // 정상적인 요청이면 예방접종 일정 반환
    void GOOD_OFFER_VACCINE_SCHEDULE_CHANGE() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        Vaccine vaccine = mock(Vaccine.class);
        BabyVaccination vaccination = mock(BabyVaccination.class);

        when(vaccination.getId()).thenReturn(10L);
        when(vaccination.getVaccine()).thenReturn(vaccine);
        when(vaccine.getId()).thenReturn(20L);
        when(vaccine.getName()).thenReturn("B형간염");
        when(vaccine.getRecommendedStartMonth()).thenReturn(0);
        when(vaccine.getRecommendedEndMonth()).thenReturn(1);
        when(vaccination.getScheduledDate()).thenReturn(LocalDate.of(2026, 2, 1));
        when(vaccination.getCompletedDate()).thenReturn(null);
        when(vaccination.getCompleted()).thenReturn(false);

        when(repository.findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                babyId, from, to)).thenReturn(List.of(vaccination));
        mockViewerAccess();

        VaccinationScheduleResponse response =
                service.getVaccinationSchedules(babyId, userId, from, to);

        assertEquals(babyId, response.babyId());
        assertEquals(1, response.vaccinationSchedules().size());

        verify(repository).findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                babyId, from, to);
    }

    @Test
        // 스케줄이 없으면 빈 목록 반환
    void NO_SCHEDULE_RETURN_EMPTY_LIST() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        when(repository.findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                babyId, from, to)).thenReturn(List.of());
        mockViewerAccess();

        VaccinationScheduleResponse response =
                service.getVaccinationSchedules(babyId, userId, from, to);

        assertEquals(babyId, response.babyId());
        assertNotNull(response.vaccinationSchedules());
        assertTrue(response.vaccinationSchedules().isEmpty());
    }

    @Test
        // 잘못된 아기 아이디를 받으면 예외 발생
    void WRONG_BABY_ID_THROW_EXCEPTION() {
        assertThrows(BusinessException.class,
                () -> service.getVaccinationSchedules(
                        0L, 10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        verifyNoInteractions(repository);
    }

    @Test
        // 조회 기간이 365일을 넘어가면 예외 발생
    void GET_DATE_OVER_MAX_RANGE_THROW_EXCEPTION() {
        assertThrows(BusinessException.class,
                () -> service.getVaccinationSchedules(
                        1L, 10L, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 3)));
        verifyNoInteractions(repository);
    }

    @Test
        // 조회 권한이 없으면 예외 발생
    void GET_SCHEDULES_ACCESS_DENIED() {
        when(babyRepository.existsById(babyId)).thenReturn(true);
        when(babyAccessRepository.findMemberId(
                userId, babyId, BabyPermission.VACCINATION_READ, false
        )).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> service.getVaccinationSchedules(
                        babyId, userId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        verifyNoInteractions(repository);
    }

    @Test
        // 쓰기 권한이 있으면 transService 에 위임하고 그 결과를 그대로 반환
    void CREATE_VACCINATION_SCHEDULE_SUCCESS() {
        var request = createRequest();
        var expected = new VaccinationScheduleCreateResponse(
                1L, babyId, 3L, "BCG", 1, TODAY.plusDays(1), null, "", "", false);

        mockWriteAccess();
        when(transService.createVaccinationSchedule(babyId, userId, request))
                .thenReturn(expected);

        var response = service.createVaccinationSchedule(babyId, userId, request);

        assertSame(expected, response);
        verify(transService).createVaccinationSchedule(babyId, userId, request);
        verifyNoInteractions(repository);
    }

    @Test
        // 존재하지 않는 아기
    void CREATE_VACCINATION_SCHEDULE_BABY_NOT_FOUND() {
        Long unknownBabyId = 999L;
        when(babyRepository.existsById(unknownBabyId)).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> service.createVaccinationSchedule(unknownBabyId, userId, createRequest()));

        verifyNoInteractions(transService);
    }

    @Test
        // 아기의 구성원이 아니거나 쓰기 권한이 없으면 생성할 수 없음
    void CREATE_VACCINATION_SCHEDULE_ACCESS_DENIED() {
        mockWriteAccessDenied();

        assertThrows(BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, userId, createRequest()));

        verifyNoInteractions(transService);
    }

    @Test
        // 잘못된 아기 ID
    void CREATE_VACCINATION_SCHEDULE_INVALID_BABY_ID() {
        assertThrows(BusinessException.class,
                () -> service.createVaccinationSchedule(0L, userId, createRequest()));

        verifyNoInteractions(transService);
    }

    @Test
        // transService 가 던진 예외는 그대로 전파
    void CREATE_VACCINATION_SCHEDULE_TRANS_EXCEPTION_PROPAGATES() {
        mockWriteAccess();
        var thrown = new BusinessException(ErrorCode.DUPLICATE_VACCINATION);
        when(transService.createVaccinationSchedule(babyId, userId, createRequest()))
                .thenThrow(thrown);

        var ex = assertThrows(BusinessException.class,
                () -> service.createVaccinationSchedule(babyId, userId, createRequest()));

        assertSame(thrown, ex);
    }

    @Test
        // 접종 완료 처리 위임
    void SUCCESS_VACCINATION_SCHEDULE_COMPLETE_DELEGATES() {
        Long scheduleId = 10L;
        var request = new VaccinationScheduleSuccessRequest(true, TODAY);
        var expected = updateResponse(true);

        mockWriteAccess();
        when(transService.successVaccinationSchedule(babyId, userId, scheduleId, request))
                .thenReturn(expected);

        var response = service.successVaccinationSchedule(babyId, userId, scheduleId, request);

        assertSame(expected, response);
        verify(transService).successVaccinationSchedule(babyId, userId, scheduleId, request);
    }

    @Test
        // 접종 완료 취소 위임
    void SUCCESS_VACCINATION_SCHEDULE_CANCEL_COMPLETE_DELEGATES() {
        Long scheduleId = 10L;
        var request = new VaccinationScheduleSuccessRequest(false, null);
        var expected = updateResponse(false);

        mockWriteAccess();
        when(transService.successVaccinationSchedule(babyId, userId, scheduleId, request))
                .thenReturn(expected);

        var response = service.successVaccinationSchedule(babyId, userId, scheduleId, request);

        assertSame(expected, response);
        verify(transService).successVaccinationSchedule(babyId, userId, scheduleId, request);
    }

    @Test
        // 쓰기 권한이 없으면 완료 처리 불가 (transService 호출 안 함)
    void SUCCESS_VACCINATION_SCHEDULE_ACCESS_DENIED() {
        var request = new VaccinationScheduleSuccessRequest(true, null);
        mockWriteAccessDenied();

        assertThrows(BusinessException.class,
                () -> service.successVaccinationSchedule(babyId, userId, 10L, request));

        verifyNoInteractions(transService);
    }

    @Test
    void DELETE_VACCINATION_SCHEDULE_DELEGATES() {
        mockWriteAccess();

        service.deleteVaccinationSchedule(babyId, userId, 10L);

        verify(transService).deleteVaccinationSchedule(babyId, userId, 10L);
    }

    @Test
    void DELETE_VACCINATION_SCHEDULE_ACCESS_DENIED() {
        mockWriteAccessDenied();

        assertThrows(BusinessException.class,
                () -> service.deleteVaccinationSchedule(babyId, userId, 10L));

        verifyNoInteractions(transService);
    }
}
