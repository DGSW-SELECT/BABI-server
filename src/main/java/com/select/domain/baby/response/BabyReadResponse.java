package com.select.domain.baby.response;

import java.time.LocalDate;
import com.select.domain.baby.entity.BabySex;

public record BabyReadResponse(
        Long babyId,
        String name,
        LocalDate birthDate,
        BabySex sex
) {
    public BabyReadResponse(Long babyId, String name, LocalDate birthDate) { this(babyId, name, birthDate, null); }
}
