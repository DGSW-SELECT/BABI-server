package com.select.domain.growth.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

public record GrowthRecordCreateRequest(

        @NotNull
        LocalDate measuredDate,

        @DecimalMin("0.01")
        @Digits(integer = 3, fraction = 2)
        BigDecimal heightCm,

        @DecimalMin("0.01")
        @Digits(integer = 3, fraction = 2)
        BigDecimal weightKg,

        @Size(max = 255)
        String memo
) {
}
