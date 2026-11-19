package com.select.domain.vaccination.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyAccessRepository;
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
import org.springframework.transaction.annotation.Propagation;
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
    private final BabyAccessRepository babyAccessRepository;
    private final Clock clock;
    private final VaccinationTransactionService vaccinationTransactionService;


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
        if (!babyRepository.existsById(babyId))
            throw new BusinessException(ErrorCode.BABY_NOT_FOUND);

        if (babyAccessRepository.findMemberId(
                userId,
                babyId,
                BabyPermission.VACCINATION_READ,
                false
        ).isEmpty()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
    private void validateBabyWriteAccess(Long babyId, Long userId) {
        validateBabyId(babyId);

        if (!babyRepository.existsById(babyId))
            throw new BusinessException(ErrorCode.BABY_NOT_FOUND);

        if (babyAccessRepository.findMemberId(
                userId,
                babyId,
                BabyPermission.VACCINATION_WRITE,
                true
        ).isEmpty()) {
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VaccinationScheduleCreateResponse createVaccinationSchedule(
            Long babyId,
            Long userId,
            VaccinationScheduleCreateRequest request
    ) {
        validateBabyWriteAccess(babyId, userId);

        return vaccinationTransactionService.createVaccinationSchedule(
                babyId, userId, request
        );
    }
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VaccinationScheduleUpdateResponse successVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId,
            VaccinationScheduleSuccessRequest request
    ){
        validateBabyWriteAccess(babyId, userId);

        return vaccinationTransactionService.successVaccinationSchedule(
                babyId, userId, vaccinationScheduleId, request
        );
    }
    @Override
    public VaccinationHistoryResponse getVaccinationHistory(Long babyId, Long userId) {
        validateBabyReadAccess(babyId, userId);
        List<BabyVaccination> vaccinations =
                babyVaccinationRepository.findAllByBabyIdAndCompletedTrueOrderByCompletedDateDescIdDesc(babyId);

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VaccinationScheduleUpdateResponse updateVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId,
            VaccinationScheduleUpdateRequest request
    ) {
        // 아기 존재 여부 확인
        validateBabyWriteAccess(babyId, userId);

        return vaccinationTransactionService.updateVaccinationSchedule(
                babyId, userId, vaccinationScheduleId, request
        );
    }


    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteVaccinationSchedule(
            Long babyId,
            Long userId,
            Long vaccinationScheduleId
    ) {
        validateBabyWriteAccess(babyId, userId);

        vaccinationTransactionService.deleteVaccinationSchedule(
                babyId, userId, vaccinationScheduleId
        );
    }
}
