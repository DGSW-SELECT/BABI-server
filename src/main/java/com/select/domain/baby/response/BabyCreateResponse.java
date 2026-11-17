package com.select.domain.baby.response;

import java.time.LocalDate;
import com.select.domain.baby.entity.BabySex;

public record BabyCreateResponse(
        Long babyId,
        String name,
        LocalDate birthDate,
        BabySex sex
) {
    public BabyCreateResponse(Long babyId, String name, LocalDate birthDate) { this(babyId, name, birthDate, null); }
}
