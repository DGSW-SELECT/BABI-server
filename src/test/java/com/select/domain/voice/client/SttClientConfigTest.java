package com.select.domain.voice.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SttClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(com.select.global.config.SttClientConfig.class);

    @Test
    void explicitFakeProviderCreatesFakeClient() {
        contextRunner
                .withPropertyValues(
                        "stt.provider=fake",
                        "stt.fake-transcript=테스트 음성"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SttClient.class);
                    SttClient client = context.getBean(SttClient.class);
                    assertThat(client).isInstanceOf(FakeSttClient.class);
                    assertThat(client.transcribe(audio()).text()).isEqualTo("테스트 음성");
                });
    }

    @Test
    void defaultProviderFailsFastWhenApiKeyIsMissing() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("stt.api-key");
                });
    }

    @Test
    void openAiProviderCreatesRestClientWithApiKey() {
        contextRunner
                .withPropertyValues(
                        "stt.provider=openai",
                        "stt.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SttClient.class);
                    assertThat(context.getBean(SttClient.class)).isInstanceOf(RestSttClient.class);
                });
    }

    @Test
    void awsProfileRejectsExplicitFakeProvider() {
        contextRunner
                .withPropertyValues("spring.profiles.active=aws", "stt.provider=fake")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("aws 프로필에서는 가짜 STT 제공자");
                });
    }

    @Test
    void unknownProviderFailsBinding() {
        contextRunner
                .withPropertyValues("stt.provider=unknown")
                .run(context -> assertThat(context).hasFailed());
    }

    private static MockMultipartFile audio() {
        return new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
    }
}
