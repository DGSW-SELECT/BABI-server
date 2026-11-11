package com.select.domain.voice.client;

import com.select.domain.voice.client.dto.response.SttResponse;
import com.select.domain.voice.exception.SttFailedException;
import com.select.global.config.SttProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestSttClientTest {

    private static final String TRANSCRIPTION_URL = "https://api.openai.com/v1/audio/transcriptions";

    private MockRestServiceServer server;
    private RestSttClient sttClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SttProperties properties = new SttProperties(
                "https://api.openai.com/v1/", "test-key", "whisper-1", "ko", Duration.ofSeconds(30)
        );
        sttClient = new RestSttClient(builder.build(), properties);
    }

    @Test
    void sendsMultipartWaveFileAndParsesTranscript() throws Exception {
        server.expect(requestTo(TRANSCRIPTION_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("whisper-1")))
                .andExpect(content().string(containsString("ko")))
                .andRespond(withSuccess("{\"text\":\"분유 먹였어\"}", MediaType.APPLICATION_JSON));

        byte[] wavAudio;
        try (InputStream input = getClass().getResourceAsStream("/audio/babi-voice-input-test.wav")) {
            if (input == null) {
                throw new IllegalStateException("음성 입력 테스트 파일을 찾을 수 없습니다.");
            }
            wavAudio = input.readAllBytes();
        }
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "babi-voice-input-test.wav", "audio/wav", wavAudio
        );

        SttResponse response = sttClient.transcribe(audio);

        assertThat(response.text()).isEqualTo("분유 먹였어");
        server.verify();
    }

    @Test
    void wrapsUpstreamErrorAsSttFailedException() {
        server.expect(requestTo(TRANSCRIPTION_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> sttClient.transcribe(audio))
                .isInstanceOf(SttFailedException.class);
        server.verify();
    }

    @Test
    void failsWithoutCallingUpstreamWhenApiKeyIsMissing() {
        SttClient clientWithoutKey = new RestSttClient(
                RestClient.builder().build(),
                new SttProperties(
                        "https://api.openai.com/v1", " ", "whisper-1", "ko", Duration.ofSeconds(30)
                )
        );
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> clientWithoutKey.transcribe(audio))
                .isInstanceOf(SttFailedException.class)
                .hasMessageContaining("API 키");
        server.verify();
    }
}
