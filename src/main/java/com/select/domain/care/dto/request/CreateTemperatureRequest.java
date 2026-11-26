package com.select.domain.care.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTemperatureRequest(
        @NotNull LocalDateTime occurredAt,
        @NotNull @DecimalMin("30.0") @DecimalMax("45.0") @Digits(integer = 2, fraction = 1)
        BigDecimal temperatureCelsius,
        @Size(max = 5000) String note
) {
}
