package com.select.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawRequest(@NotBlank @Size(max = 20) String password) {}
