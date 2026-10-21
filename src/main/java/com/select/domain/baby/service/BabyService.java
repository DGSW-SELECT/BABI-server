package com.select.domain.baby.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.response.BabyCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class BabyService {

    private final BabyRepository babyRepository;

    @Transactional
    public BabyCreateResponse createBaby(BabyCreateRequest request, Long userId) {
        Baby baby = Baby.builder()
                .name(request.name())
                .birthDate(request.birthDate())
                .createdByUserId(userId)
                .build();
        Baby savedBaby = babyRepository.save(baby);
        return new BabyCreateResponse(savedBaby.getId(),savedBaby.getName(),savedBaby.getBirthDate());
    }
}
