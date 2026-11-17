package com.select.domain.baby.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record InvitationCodeRequest(@NotNull @Pattern(regexp = "[A-Za-z0-9]{6}") String code) {}
