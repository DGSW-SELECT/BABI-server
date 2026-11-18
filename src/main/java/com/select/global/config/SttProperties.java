package com.select.global.config;

import com.select.domain.voice.client.SttProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "stt")
public record SttProperties(
        SttProvider provider,
        String baseUrl,
        String apiKey,
        String model,
        String language,
        String fakeTranscript,
        Duration timeout
) {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "whisper-1";
    private static final String DEFAULT_LANGUAGE = "ko";
    private static final String DEFAULT_FAKE_TRANSCRIPT = "분유 120ml 먹였어요";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public SttProperties {
        provider = provider == null ? SttProvider.OPENAI : provider;
        baseUrl = normalizeBaseUrl(baseUrl);
        apiKey = apiKey == null ? null : apiKey.trim();
        model = StringUtils.hasText(model) ? model.trim() : DEFAULT_MODEL;
        language = StringUtils.hasText(language) ? language.trim() : DEFAULT_LANGUAGE;
        fakeTranscript = StringUtils.hasText(fakeTranscript) ? fakeTranscript : DEFAULT_FAKE_TRANSCRIPT;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("stt.timeout은 0보다 커야 합니다.");
        }
    }

    /**
     * 실제 STT 제공자를 호출해야 하는지 판단한다.
     */
    public boolean usesRealProvider() {
        return switch (provider) {
            case OPENAI -> true;
            case FAKE -> false;
        };
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
