package com.select.domain.baby.request;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(
        @NotNull
        Long newOwnerUserId
) {
}
