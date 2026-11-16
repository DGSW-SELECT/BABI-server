package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleSuccessRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.dto.response.*;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VaccinationServiceImpl implements VaccinationService {

    private static final long MAX_RANGE_DAY = 365;

    private final BabyVaccinationRepository babyVaccinationRepository;
    private final VaccineRepository vaccineRepository;
    private final BabyRepository babyRepository;
    private final BabyMemberRepository babyMemberRepository;
    private final Clock clock;


    // 아기 권한 검사 로직
    private void validateBabyId(Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new BusinessException(
                    ErrorCode.VACCINE_INVALID_BABY_ID
            );
        }
    }

    private void validateBabyReadAccess(Long babyId, Long userId) {
        validateBabyId(babyId);
        // 아기 존재 여부 확인
        if (!babyRepository.existsById(babyId)) {
            throw new BusinessException(ErrorCode.BABY_NOT_FOUND);
        }

        // 로그인 사용자가 해당 아기의 활성 구성원인지 확인
                babyMemberRepository
                .findByBabyIdAndUserId(babyId, userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.ACCESS_DENIED));
    }
    private void validateBabyWriteAccess(Long babyId, Long userId) {
        validateBabyId(babyId);

        if (!babyRepository.existsById(babyId)) {
            throw new BusinessException(ErrorCode.BABY_NOT_FOUND);
        }

        BabyMember member = babyMemberRepository
                .findByBabyIdAndUserId(babyId, userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.ACCESS_DENIED));

        if (member.getRole() == BabyRole.VIEWER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
    private boolean isDuplicateVaccinationConstraint(Throwable e) {
        Throwable cause = e;

        while (cause != null) {
            if (cause instanceof
                    org.hibernate.exception.ConstraintViolationException ex) {
                return "uk_baby_vaccine".equals(ex.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
    }

    @Override
    public VaccinationScheduleResponse getVaccinationSchedules(
            Long babyId,
            Long userId,
            LocalDate from,
            LocalDate to

    ) {
        // 1. 입력값 검증 부분
        if (babyId == null || babyId <= 0) {
            throw new BusinessException(
                    ErrorCode.VACCINE_INVALID_BABY_ID
            );
        }
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessException(
                    ErrorCode.VACCINE_INVALID_DATE_RANGE
            );
        }

        long rangeDays = ChronoUnit.DAYS.between(from, to);

        if (rangeDays > MAX_RANGE_DAY) {
            throw new BusinessException(
                    ErrorCode.VACCINE_INVALID_DATE_SET
            );
        }

        // 2. 아기 존재 여부 확인 + 사용자 권한 검증
        validateBabyReadAccess(babyId, userId);

        // 3. 일정 조회
        List<BabyVaccination> babyVaccinations =
                babyVaccinationRepository.findByBabyIdAndScheduledDateBetweenOrderByScheduledDateAscIdAsc(
                        babyId,
                        from,
                        to
                );
        // 4. Entity -> DTO변환
        List<VaccinationScheduleItemResponse> schedules =
                babyVaccinations.stream().map(babyVaccination -> {
                    Vaccine vaccine = babyVaccination.getVaccine();
                    return new VaccinationScheduleItemResponse(
                            babyVaccination.getId(),
                            vaccine.getId(),
                            vaccine.getName(),
                            vaccine.getDoseNumber(),
                            vaccine.getRecommendedStartMonth(),
                            vaccine.getRecommendedEndMonth(),
                            babyVaccination.getScheduledDate(),
                            babyVaccination.getCompletedDate(),
                            babyVaccination.getInstitution(),
                            babyVaccination.getMemo(),
                            babyVaccination.getCompleted()
                    );
                }).toList();

        // 5. 응답 출력
        return new VaccinationScheduleResponse(
                babyId,
                schedules
        );
    }

    @Override
    @Transactional
    public VaccinationScheduleCreateResponse createVaccinationSchedule(
            Long babyId,
            Long userId,
            VaccinationScheduleCreateRequest request
    ) {
        // 아기 Id 유효성 검사
        if (babyId == null || babyId <= 0) {
            throw new BusinessException(ErrorCode.VACCINE_INVALID_BABY_ID);
        }
        // 아기 존재 여부 확인
        validateBabyWriteAccess(babyId, userId);
        // 백신조회
        Vaccine vaccine = vaccineRepository.findById(request.vaccineId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VACCINE_NOT_FOUND));
        LocalDate scheduledDate = request.scheduledDate();

        if (!Objects.equals(vaccine.getDoseNumber(), request.doseNumber())) {
            throw new BusinessException(ErrorCode.VACCINATION_INVALID_DOSE_NUMBER);
        }
        // 접종 예정 검증
        if (scheduledDate == null || scheduledDate.isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE);
        }
        // 중복 등록 검사
        if (babyVaccinationRepository.existsByBabyIdAndVaccine_Id(babyId, vaccine.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_VACCINATION);
        }
        // 엔티티 생성 및 저장
        BabyVaccination vaccination =
                BabyVaccination.create(babyId, vaccine, scheduledDate,request.institution(), request.memo());

        BabyVaccination saved;
        try {
            saved = babyVaccinationRepository.saveAndFlush(vaccination);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateVaccinationConstraint(e)) {
                throw new BusinessException(ErrorCode.DUPLICATE_VACCINATION);
            }
            throw e;
        }
        // 응답 반환
        return new VaccinationScheduleCreateResponse(
                saved.getId(),
                babyId,
                vaccine.getId(),
                vaccine.getName(),
                vaccine.getDoseNumber(),
                saved.getScheduledDate(),
                saved.getCompletedDate(),
                saved.getInstitution(),
                saved.getMemo(),
                saved.getCompleted()
        );
    }
    @Override
    @Transactional
    public VaccinationScheduleUpdateResponse successVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId,
            VaccinationScheduleSuccessRequest request
    ){
        validateBabyWriteAccess(babyId, userId);

        BabyVaccination vaccination = babyVaccinationRepository
                .findById(vaccinationScheduleId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND)
                );

        if (!vaccination.getBabyId().equals(babyId)) {
            throw new BusinessException(
                    ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND
            );
        }

        if (request.completed()) {
            if (vaccination.getScheduledDate() != null && vaccination.getScheduledDate().isAfter(LocalDate.now(clock)))
                throw new BusinessException(
                        ErrorCode.VACCINE_INVALID_SCHEDULE_DATE
                );
            if (!Boolean.TRUE.equals(vaccination.getCompleted()))
                vaccination.complete(LocalDate.now(clock));
        } else {
            vaccination.cancelComplete();
        }
        return new VaccinationScheduleUpdateResponse(
                vaccination.getId(),
                vaccination.getBabyId(),
                vaccination.getVaccine().getId(),
                vaccination.getVaccine().getDoseNumber(),
                vaccination.getScheduledDate(),
                vaccination.getCompletedDate(),
                vaccination.getInstitution(),
                vaccination.getMemo(),
                Boolean.TRUE.equals(vaccination.getCompleted())
        );
    }
    @Override
    public VaccinationHistoryResponse getVaccinationHistory(Long babyId, Long userId) {
        validateBabyReadAccess(babyId, userId);
        List<BabyVaccination> vaccinations =
                babyVaccinationRepository.findAllByBabyIdAndCompletedTrue(babyId);

        List<VaccinationHistoryResponse.Item> items =
                vaccinations.stream()
                        .map(vaccination ->
                                new VaccinationHistoryResponse.Item(
                                        vaccination.getId(),
                                        vaccination.getVaccine().getName(),
                                        vaccination.getVaccine().getDoseNumber(),
                                        vaccination.getCompletedDate()
                                )
                        ).toList();

        return new VaccinationHistoryResponse(babyId, items);
    }
    @Override
    @Transactional
    public VaccinationScheduleUpdateResponse updateVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId,
            VaccinationScheduleUpdateRequest request
    ) {
        // 아기 존재 여부 확인
        validateBabyWriteAccess(babyId, userId);

        // 접종 일정 조회
        BabyVaccination vaccination = babyVaccinationRepository
                .findById(vaccinationScheduleId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND)
                );

        // 해당 아기의 예방접종인지 확인
        if(!vaccination.getBabyId().equals(babyId)) {
            throw new BusinessException(ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND);
        }

        if (Boolean.TRUE.equals(vaccination.getCompleted())) {
            throw new BusinessException(ErrorCode.VACCINATION_ALREADY_COMPLETED);
        }

        // 변경할 백신 조회
        Vaccine vaccine = vaccineRepository.findById(request.vaccineId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.VACCINE_NOT_FOUND)
                );
        // 접종 차수 유효성 검사
        if (request.doseNumber() == null || request.doseNumber() < 1) {
            throw new BusinessException(ErrorCode.VACCINATION_INVALID_DOSE_NUMBER);
        }
        if (!Objects.equals(vaccine.getDoseNumber(), request.doseNumber())) {
            throw new BusinessException(ErrorCode.VACCINATION_INVALID_DOSE_NUMBER);
        }
        if (request.scheduledDate() == null) {
            throw new BusinessException(
                    ErrorCode.VACCINE_INVALID_SCHEDULE_DATE
            );
        }

        boolean dataChange = !Objects.equals(vaccination.getScheduledDate(), request.scheduledDate());
        if (dataChange && request.scheduledDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(
                    ErrorCode.VACCINE_INVALID_SCHEDULE_DATE
            );
        }

        boolean duplicated =
                babyVaccinationRepository.existsByBabyIdAndVaccine_IdAndIdNot(
                        babyId,
                        request.vaccineId(),
                        vaccinationScheduleId
                );
        if(duplicated)
            throw new BusinessException(ErrorCode.DUPLICATE_VACCINATION);

        // 엔티티 수정
        vaccination.updateSchedule(vaccine, request.scheduledDate(), request.institution(), request.memo());
        try {
            babyVaccinationRepository.flush();
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateVaccinationConstraint(e)) {
                throw new BusinessException(ErrorCode.DUPLICATE_VACCINATION);
            }
            throw e;
        }

        return new VaccinationScheduleUpdateResponse(
                vaccination.getId(),
                vaccination.getBabyId(),
                vaccination.getVaccine().getId(),
                vaccination.getVaccine().getDoseNumber(),
                vaccination.getScheduledDate(),
                vaccination.getCompletedDate(),
                vaccination.getInstitution(),
                vaccination.getMemo(),
                vaccination.getCompleted()
        );
    }


    @Override
    @Transactional
    public void deleteVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId
    ) {
        validateBabyWriteAccess(babyId, userId);

        BabyVaccination vaccination = babyVaccinationRepository
                .findById(vaccinationScheduleId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND
                        )
                );

        if (!vaccination.getBabyId().equals(babyId)) {
            throw new BusinessException(
                    ErrorCode.VACCINATION_SCHEDULE_NOT_FOUND
            );
        }

        babyVaccinationRepository.delete(vaccination);
    }
}
