package com.select.domain.baby.response;

import com.select.domain.baby.entity.BabyRole;

public record BabyMemberResponse(
        Long userId,
        String nickname,
        BabyRole role
) {
}
