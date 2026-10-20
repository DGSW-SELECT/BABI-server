package com.select.domain.vaccination.service;

import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.vaccination.dto.request.VaccinationScheduleCreateRequest;
import com.select.domain.vaccination.dto.request.VaccinationScheduleUpdateRequest;
import com.select.domain.vaccination.dto.response.VaccinationScheduleCreateResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleItemResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleResponse;
import com.select.domain.vaccination.dto.response.VaccinationScheduleUpdateResponse;
import com.select.domain.vaccination.entity.BabyVaccination;
import com.select.domain.vaccination.entity.Vaccine;
import com.select.domain.vaccination.repository.BabyVaccinationRepository;
import com.select.domain.vaccination.repository.VaccineRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VaccinationServiceImpl implements VaccinationService {

    private static final long MAX_RANGE_DAY = 366;

    private final BabyVaccinationRepository babyVaccinationRepository;
    private final VaccineRepository vaccineRepository;
    private final BabyRepository babyRepository;

    @Override
    public VaccinationScheduleResponse getVaccinationSchedules(
            Long babyId,
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

        // 2. 아기별 접근 권 검증
        // TODO: 나중에 로그인 사용 babyId 접근 권한 확인
        // 이를 위해 인증 정 및 권한 부여 서비스 필요

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
            VaccinationScheduleCreateRequest request
    ) {
        // 아기 Id 유효성 검사
        if (babyId == null || babyId <= 0) {
            throw new BusinessException(ErrorCode.BABY_NOT_FOUND);
        }
        // 백신조회
        Vaccine vaccine = vaccineRepository.findById(request.vaccineId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VACCINE_NOT_FOUND));
        LocalDate scheduledDate = request.scheduleDate();
        // 접종 예정 검증
        if (scheduledDate == null || !scheduledDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE);
        }
        // 아기 존재 여부 확인
        if (!babyRepository.existsById(babyId)) {
            throw new BusinessException(ErrorCode.BABY_NOT_FOUND);
        }
        // 중복 등록 검사
        if (babyVaccinationRepository.existsByBabyIdAndVaccine_Id(babyId, vaccine.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_VACCINATION);
        }
        // 엔티티 생성 및 저장
        BabyVaccination vaccination =
                BabyVaccination.create(babyId, vaccine, scheduledDate);

        BabyVaccination saved =
                babyVaccinationRepository.save(vaccination);
        // 응답 반환
        return new VaccinationScheduleCreateResponse(
                saved.getId(),
                babyId,
                vaccine.getId(),
                vaccine.getName(),
                vaccine.getDoseNumber(),
                saved.getScheduledDate(),
                saved.getCompletedDate(),
                saved.getCompleted()
        );
    }
    public VaccinationScheduleUpdateResponse updateVaccinationSchedule(
            Long babyId,
            Long vaccinationScheduleId,
            VaccinationScheduleUpdateRequest request
    ){
        if (!babyRepository.existsById(babyId)) {
            throw new BusinessException(ErrorCode.BABY_NOT_FOUND);
        }

        BabyVaccination vaccination = babyVaccinationRepository
                .findById(vaccinationScheduleId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.VACCINE_INVALID_SCHEDULE_DATE)
                );

        if (!vaccination.getBabyId().equals(babyId)) {
            throw new BusinessException(
                    ErrorCode.VACCINE_INVALID_SCHEDULE_DATE
            );
        }

        if (request.completed()) {
            vaccination.complete(LocalDate.now());
        } else {
            vaccination.cancelComplete();
        }
        return new VaccinationScheduleUpdateResponse(
                vaccination.getId(),
                vaccination.getBabyId(),
                vaccination.getVaccine().getId(),
                vaccination.getScheduledDate(),
                vaccination.getCompletedDate(),
                Boolean.TRUE.equals(vaccination.getCompleted())
        );
    }
}
