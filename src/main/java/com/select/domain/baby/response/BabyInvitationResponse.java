package com.select.domain.baby.response;

import java.time.Instant;

public record BabyInvitationResponse(Long babyId, String name, Instant expiresAt) {}
