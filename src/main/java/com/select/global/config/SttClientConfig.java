package com.select.global.config;

import com.select.domain.voice.client.FakeSttClient;
import com.select.domain.voice.client.RestSttClient;
import com.select.domain.voice.client.SttClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SttProperties.class)
public class SttClientConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    @Bean
    public SttClient sttClient(SttProperties properties, Environment environment) {
        if (!properties.usesRealProvider()) {
            if (environment.matchesProfiles("aws")) {
                throw new IllegalStateException("aws 프로필에서는 가짜 STT 제공자를 사용할 수 없습니다.");
            }
            return new FakeSttClient(properties.fakeTranscript());
        }
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException("stt.api-key가 설정되지 않았습니다.");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout((int) Math.min(
                properties.timeout().toMillis(),
                Integer.MAX_VALUE
        ));

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        return new RestSttClient(restClient, properties);
    }
}
