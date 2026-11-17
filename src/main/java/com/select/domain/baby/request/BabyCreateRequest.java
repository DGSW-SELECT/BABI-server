package com.select.domain.baby.request;

import com.select.domain.baby.entity.BabySex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BabyCreateRequest(
        @NotBlank
        @Size(max = 50)
        String name,

        @NotNull
        @PastOrPresent
        LocalDate birthDate,
        BabySex sex
) {
    public BabyCreateRequest(String name, LocalDate birthDate) { this(name, birthDate, null); }
}
