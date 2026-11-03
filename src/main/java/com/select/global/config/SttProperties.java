package com.select.global.config;

import com.select.domain.voice.client.SttProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "stt")
public record SttProperties(
        String baseUrl,
        String apiKey,
        String model,
        String language,
        Duration timeout,
        SttProvider provider,
        String fakeTranscript
) {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "whisper-1";
    private static final String DEFAULT_LANGUAGE = "ko";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final String DEFAULT_FAKE_TRANSCRIPT = "분유 120ml 먹였어요";

    public SttProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        apiKey = apiKey == null ? null : apiKey.trim();
        model = StringUtils.hasText(model) ? model.trim() : DEFAULT_MODEL;
        language = StringUtils.hasText(language) ? language.trim() : DEFAULT_LANGUAGE;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        provider = provider == null ? SttProvider.AUTO : provider;
        fakeTranscript = StringUtils.hasText(fakeTranscript)
                ? fakeTranscript.trim()
                : DEFAULT_FAKE_TRANSCRIPT;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("stt.timeout은 0보다 커야 합니다.");
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return DEFAULT_BASE_URL;
        }

        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
