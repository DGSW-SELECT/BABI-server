package com.select.global.config;

import com.select.domain.voice.client.FakeSttClient;
import com.select.domain.voice.client.RestSttClient;
import com.select.domain.voice.client.SttClient;
import com.select.domain.voice.client.SttProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SttProperties.class)
public class SttClientConfig {

    private static final Logger log = LoggerFactory.getLogger(SttClientConfig.class);
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    @Bean
    public SttClient sttClient(SttProperties properties) {
        SttProvider provider = resolveProvider(properties);
        if (provider == SttProvider.OPENAI) {
            return createRestSttClient(properties);
        }

        log.warn("STT 제공자가 fake로 동작합니다. 실제 음성 변환을 사용하려면 "
                + "stt.provider=openai와 stt.api-key를 설정해 주세요.");
        return new FakeSttClient(properties.fakeTranscript());
    }

    private SttProvider resolveProvider(SttProperties properties) {
        if (properties.provider() == SttProvider.FAKE) {
            return SttProvider.FAKE;
        }
        if (properties.provider() == SttProvider.OPENAI) {
            return requireApiKey(properties);
        }
        return StringUtils.hasText(properties.apiKey()) ? SttProvider.OPENAI : SttProvider.FAKE;
    }

    private SttProvider requireApiKey(SttProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException(
                    "stt.api-key가 설정되지 않았습니다. OPENAI_API_KEY를 설정해 주세요."
            );
        }
        return SttProvider.OPENAI;
    }

    private RestSttClient createRestSttClient(SttProperties properties) {
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
