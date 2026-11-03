package com.select.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 비동기 STT 작업 파이프라인 설정.
 *
 * <p>모델과 무관한 저장소, 재시도, 워커 정책을 담는다.</p>
 */
@ConfigurationProperties(prefix = "stt.jobs")
public record SttJobProperties(
        String storageDir,
        int maxAttempts,
        Duration retryDelay,
        Duration staleTimeout,
        Worker worker
) {

    private static final String DEFAULT_STORAGE_DIR = "./storage/stt";
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration DEFAULT_STALE_TIMEOUT = Duration.ofMinutes(10);

    public SttJobProperties {
        storageDir = StringUtils.hasText(storageDir) ? storageDir.trim() : DEFAULT_STORAGE_DIR;
        maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
        retryDelay = positiveOrDefault(retryDelay, DEFAULT_RETRY_DELAY);
        staleTimeout = positiveOrDefault(staleTimeout, DEFAULT_STALE_TIMEOUT);
        worker = worker == null ? new Worker(5, Duration.ofSeconds(5), Duration.ofSeconds(10)) : worker;
    }

    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        if (value == null || value.isZero() || value.isNegative()) {
            return defaultValue;
        }
        return value;
    }

    public record Worker(
            int batchSize,
            Duration pollInterval,
            Duration initialDelay
    ) {

        public Worker {
            batchSize = batchSize > 0 ? batchSize : 5;
            pollInterval = positiveOrDefault(pollInterval, Duration.ofSeconds(5));
            initialDelay = positiveOrDefault(initialDelay, Duration.ofSeconds(10));
        }
    }
}
