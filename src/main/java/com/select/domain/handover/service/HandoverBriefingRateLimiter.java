package com.select.domain.handover.service;

import com.select.domain.handover.exception.HandoverBriefingRateLimitedException;
import com.select.domain.handover.exception.HandoverBriefingUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class HandoverBriefingRateLimiter {

    private final StringRedisTemplate redis;
    private final int cooldownSeconds;

    public HandoverBriefingRateLimiter(
            StringRedisTemplate redis,
            @Value("${BABI_HANDOVER_COOLDOWN_SECONDS:60}") int cooldownSeconds
    ) {
        if (cooldownSeconds <= 0) {
            throw new IllegalArgumentException("Briefing cooldown must be positive");
        }
        this.redis = redis;
        this.cooldownSeconds = cooldownSeconds;
    }

    public void check(Long babyId) {
        Boolean accepted;
        try {
            accepted = redis.opsForValue().setIfAbsent(
                    "handover:generate:cooldown:" + babyId,
                    "1",
                    Duration.ofSeconds(cooldownSeconds)
            );
        } catch (RuntimeException exception) {
            throw new HandoverBriefingUnavailableException(exception);
        }
        if (accepted == null) {
            throw new HandoverBriefingUnavailableException(
                    new IllegalStateException("Briefing rate limit result unavailable"));
        }
        if (!accepted) {
            throw new HandoverBriefingRateLimitedException(cooldownSeconds);
        }
    }
}
