package com.select.domain.baby.request;

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
        LocalDate birthDate
) {
}
