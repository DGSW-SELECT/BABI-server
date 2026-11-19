package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleSuccessRequest;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VaccinationTransactionServiceCreateTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final LocalDate BIRTH_DATE = LocalDate.of(2025, 1, 1);

    private static final Long BABY_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long SCHEDULE_ID = 10L;
    private static final Long VACCINE_ID = 3L;

    private BabyVaccinationRepository babyVaccinationRepository;
    private VaccineRepository vaccineRepository;
    private BabyRepository babyRepository;
    private BabyAccessRepository babyAccessRepository;
    private VaccinationTransactionService service;

    private Baby baby;
    private Vaccine vaccine;

    @BeforeEach
    void setUp() {
        babyVaccinationRepository = mock(BabyVaccinationRepository.class);
        vaccineRepository = mock(VaccineRepository.class);
        babyRepository = mock(BabyRepository.class);
        babyAccessRepository = mock(BabyAccessRepository.class);

        // LocalDate.now(clock) == 2026-11-10
        Clock clock = Clock.fixed(
                TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")
        );

        service = new VaccinationTransactionService(
                babyVaccinationRepository, vaccineRepository,
                babyRepository, babyAccessRepository, clock);

        baby = mock(Baby.class);
        when(baby.getBirthDate()).thenReturn(BIRTH_DATE);

        vaccine = mock(Vaccine.class);
        when(vaccine.getId()).thenReturn(VACCINE_ID);
        when(vaccine.getName()).thenReturn("BCG");
        when(vaccine.getDoseNumber()).thenReturn(1);
    }

    // ---------- helpers ----------

    private void givenWriteAccess() {
        when(babyRepository.findByIdForUpdate(BABY_ID)).thenReturn(Optional.of(baby));
        when(babyAccessRepository.findMemberId(
                USER_ID, BABY_ID, BabyPermission.VACCINATION_WRITE, true
        )).thenReturn(Optional.of(100L));
    }

    private void givenVaccine() {
        when(vaccineRepository.findById(VACCINE_ID)).thenReturn(Optional.of(vaccine));
    }

    /** 저장 시 넘겨받은 엔티티를 그대로 반환 */
    private void givenSaveReturnsArgument() {
        when(babyVaccinationRepository.saveAndFlush(any(BabyVaccination.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private VaccinationScheduleCreateRequest createRequest(
            Integer dose, LocalDate scheduledDate, LocalDate completedDate
    ) {
        return new VaccinationScheduleCreateRequest(
                VACCINE_ID, dose, scheduledDate, completedDate, "병원", "메모");
    }

    private BabyVaccination givenSchedule(Long ownerBabyId) {
        BabyVaccination vaccination = BabyVaccination.create(
                ownerBabyId, vaccine, TODAY, null, null, null);
        when(babyVaccinationRepository.findById(SCHEDULE_ID))
                .thenReturn(Optional.of(vaccination));
        return vaccination;
    }

    private void assertErrorCode(ErrorCode expected, Executable executable) {
        BusinessException ex = assertThrows(BusinessException.class, executable);
        // BusinessException 의 getter 이름이 다르면 여기만 수정
        assertEquals(expected, ex.getErrorCode());
    }

    private BabyVaccination capturedSavedEntity() {
        ArgumentCaptor<BabyVaccination> captor = ArgumentCaptor.forClass(BabyVaccination.class);
        verify(babyVaccinationRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    // =========================== create ===========================

    @Test
        // 존재하지 않는 백신
    void CREATE_VACCINE_NOT_FOUND() {
        givenWriteAccess();
        when(vaccineRepository.findById(VACCINE_ID)).thenReturn(Optional.empty());

        assertErrorCode(ErrorCode.VACCINE_NOT_FOUND, () ->
                service.createVaccinationSchedule(
                        BABY_ID, USER_ID, createRequest(1, TODAY.plusDays(1), null)));

        verify(babyVaccinationRepository, never()).saveAndFlush(any(BabyVaccination.class));
    }

    @Test
        // 백신의 접종 차수와 요청한 차수가 다르면 등록할 수 없음
    void CREATE_DOSE_MISMATCH() {
        givenWriteAccess();
        givenVaccine(); // doseNumber == 1

        assertErrorCode(ErrorCode.VACCINATION_INVALID_DOSE_NUMBER, () ->
                service.createVaccinationSchedule(
                        BABY_ID, USER_ID, createRequest(2, TODAY.plusDays(1), null)));

        verify(babyVaccinationRepository, never()).saveAndFlush(any(BabyVaccination.class));
    }

    @Test
        // 중복 등록
    void CREATE_DUPLICATE() {
        givenWriteAccess();
        givenVaccine();
        when(babyVaccinationRepository.existsByBabyIdAndVaccine_Id(BABY_ID, VACCINE_ID))
                .thenReturn(true);

        assertErrorCode(ErrorCode.DUPLICATE_VACCINATION, () ->
                service.createVaccinationSchedule(
                        BABY_ID, USER_ID, createRequest(1, TODAY.plusDays(1), null)));

        verify(babyVaccinationRepository, never()).saveAndFlush(any(BabyVaccination.class));
    }

    @Test
        // 오늘 날짜 예정일은 허용 (현재 코드에는 "오늘 불가" 규칙이 없음)
    void CREATE_SCHEDULED_TODAY_SUCCESS() {
        givenWriteAccess();
        givenVaccine();
        givenSaveReturnsArgument();

        assertDoesNotThrow(() ->
                service.createVaccinationSchedule(
                        BABY_ID, USER_ID, createRequest(1, TODAY, null)));

        assertEquals(TODAY, capturedSavedEntity().getScheduledDate());
    }

    @Test
        // 과거 날짜로 예방접종 일정을 생성할 수 있음
    void CREATE_PAST_SCHEDULED_DATE_SUCCESS() {
        LocalDate past = TODAY.minusDays(1);
        givenWriteAccess();
        givenVaccine();
        givenSaveReturnsArgument();

        assertDoesNotThrow(() ->
                service.createVaccinationSchedule(
                        BABY_ID, USER_ID, createRequest(1, past, null)));

        assertEquals(past, capturedSavedEntity().getScheduledDate());
    }

    @Test
        // 예정일이 아기 생일보다 앞서면 등록할 수 없음
    void CREATE_SCHEDULED_BEFORE_BIRTH_FAIL() {
        givenWriteAccess();
        givenVaccine();

        assertErrorCode(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE, () ->
                service.createVaccinationSchedule(
                        BABY_ID, USER_ID, createRequest(1, BIRTH_DATE.minusDays(1), null)));

        verify(babyVaccinationRepository, never()).saveAndFlush(any(BabyVaccination.class));
    }

    @Test
        // 과거 완료일을 지정하여 예방접종 기록을 생성할 수 있음
    void CREATE_WITH_PAST_COMPLETED_DATE_SUCCESS() {
        LocalDate completedDate = TODAY.minusDays(5);
        givenWriteAccess();
        givenVaccine();
        givenSaveReturnsArgument();

        service.createVaccinationSchedule(
                BABY_ID, USER_ID, createRequest(1, null, completedDate));

        BabyVaccination created = capturedSavedEntity();
        assertTrue(created.getCompleted());
        assertEquals(completedDate, created.getCompletedDate());
    }

    @Test
        // 미래 완료일로 예방접종 기록을 생성할 수 없음
    void CREATE_WITH_FUTURE_COMPLETED_DATE_FAIL() {
        givenWriteAccess();
        givenVaccine();

        assertErrorCode(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE, () ->
                service.createVaccinationSchedule(
                        BABY_ID, USER_ID, createRequest(1, null, TODAY.plusDays(1))));

        verify(babyVaccinationRepository, never()).saveAndFlush(any(BabyVaccination.class));
    }

    @Test
        // 예정일과 완료일이 모두 없으면 예방접종 기록을 생성할 수 없음
    void CREATE_WITHOUT_ANY_DATE_FAIL() {
        givenWriteAccess();
        givenVaccine();

        assertErrorCode(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE, () ->
                service.createVaccinationSchedule(
                        BABY_ID, USER_ID, createRequest(1, null, null)));

        verify(babyVaccinationRepository, never()).saveAndFlush(any(BabyVaccination.class));
    }

    // =========================== success (완료 처리 / 취소) ===========================

    @Test
        // 접종 완료 처리: 완료 여부가 true이고 완료일이 지정됨
    void SUCCESS_COMPLETE_SUCCESS() {
        givenWriteAccess();
        BabyVaccination vaccination = givenSchedule(BABY_ID);

        var response = service.successVaccinationSchedule(
                BABY_ID, USER_ID, SCHEDULE_ID,
                new VaccinationScheduleSuccessRequest(true, TODAY));

        assertTrue(response.completed());
        assertEquals(TODAY, response.completedDate());
        assertTrue(vaccination.getCompleted());
    }

    @Test
        // 완료일을 지정하지 않으면 오늘 날짜로 완료 처리
    void SUCCESS_COMPLETE_WITHOUT_DATE_USES_TODAY() {
        givenWriteAccess();
        BabyVaccination vaccination = givenSchedule(BABY_ID);

        var response = service.successVaccinationSchedule(
                BABY_ID, USER_ID, SCHEDULE_ID,
                new VaccinationScheduleSuccessRequest(true, null));

        assertEquals(TODAY, response.completedDate());
        assertEquals(TODAY, vaccination.getCompletedDate());
    }

    @Test
        // 미래 완료일로는 완료 처리할 수 없음
    void SUCCESS_COMPLETE_FUTURE_DATE_FAIL() {
        givenWriteAccess();
        BabyVaccination vaccination = givenSchedule(BABY_ID);

        assertErrorCode(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE, () ->
                service.successVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID,
                        new VaccinationScheduleSuccessRequest(true, TODAY.plusDays(1))));

        assertFalse(Boolean.TRUE.equals(vaccination.getCompleted()));
    }

    @Test
        // 아기 생일 이전 완료일로는 완료 처리할 수 없음
    void SUCCESS_COMPLETE_BEFORE_BIRTH_FAIL() {
        givenWriteAccess();
        givenSchedule(BABY_ID);

        assertErrorCode(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE, () ->
                service.successVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID,
                        new VaccinationScheduleSuccessRequest(true, BIRTH_DATE.minusDays(1))));
    }

    @Test
        // 접종 완료 취소: 완료 여부가 false이고 완료일이 제거됨
    void SUCCESS_CANCEL_COMPLETE_SUCCESS() {
        givenWriteAccess();
        BabyVaccination vaccination = givenSchedule(BABY_ID);
        vaccination.complete(TODAY); // 먼저 완료 상태로 만들어 둠

        var response = service.successVaccinationSchedule(
                BABY_ID, USER_ID, SCHEDULE_ID,
                new VaccinationScheduleSuccessRequest(false, null));

        assertFalse(response.completed());
        assertNull(response.completedDate());
        assertFalse(Boolean.TRUE.equals(vaccination.getCompleted()));
    }

    @Test
        // 접종 일정이 존재하지 않으면 예외 발생
    void SUCCESS_SCHEDULE_NOT_FOUND() {
        givenWriteAccess();
        when(babyVaccinationRepository.findById(999L)).thenReturn(Optional.empty());

        assertErrorCode(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND, () ->
                service.successVaccinationSchedule(
                        BABY_ID, USER_ID, 999L,
                        new VaccinationScheduleSuccessRequest(true, null)));
    }

    @Test
        // 다른 아기의 접종 일정은 완료 처리할 수 없음
    void SUCCESS_WRONG_BABY() {
        givenWriteAccess();
        BabyVaccination other = givenSchedule(2L); // 다른 아기 소유

        assertErrorCode(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND, () ->
                service.successVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID,
                        new VaccinationScheduleSuccessRequest(true, null)));

        assertFalse(Boolean.TRUE.equals(other.getCompleted()));
        assertNull(other.getCompletedDate());
    }

    @Test
        // 트랜잭션 내부에서도 쓰기 권한을 다시 검사
    void SUCCESS_ACCESS_DENIED_INSIDE_TRANSACTION() {
        when(babyRepository.findByIdForUpdate(BABY_ID)).thenReturn(Optional.of(baby));
        when(babyAccessRepository.findMemberId(
                USER_ID, BABY_ID, BabyPermission.VACCINATION_WRITE, true
        )).thenReturn(Optional.empty());

        assertErrorCode(ErrorCode.ACCESS_DENIED, () ->
                service.successVaccinationSchedule(
                        BABY_ID, USER_ID, SCHEDULE_ID,
                        new VaccinationScheduleSuccessRequest(true, null)));

        verifyNoInteractions(babyVaccinationRepository);
    }

}