package com.select.domain.voice.client;

import com.select.domain.voice.client.dto.SttResponse;
import com.select.domain.voice.exception.InvalidAudioException;
import com.select.domain.voice.exception.SttFailedException;
import com.select.domain.voice.support.AudioFileSupport;
import com.select.global.config.SttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public class RestSttClient implements SttClient {

    private static final Logger log = LoggerFactory.getLogger(RestSttClient.class);

    private final RestClient restClient;
    private final SttProperties properties;

    public RestSttClient(RestClient restClient, SttProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public SttResponse transcribe(MultipartFile audio) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(audio.getBytes()) {
                @Override
                public String getFilename() {
                    return AudioFileSupport.filenameFor(audio);
                }
            });
            body.add("model", properties.model());
            if (StringUtils.hasText(properties.language())) {
                body.add("language", properties.language());
            }
            body.add("response_format", "json");

            SttResponse response = restClient.post()
                    .uri(properties.baseUrl() + "/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(SttResponse.class);

            if (response == null) {
                throw new SttFailedException("STT 응답이 비어 있습니다.");
            }
            return response;
        } catch (IOException e) {
            throw new InvalidAudioException("음성 파일을 읽을 수 없습니다.", e);
        } catch (RestClientResponseException e) {
            log.warn("STT API 호출에 실패했습니다. status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new SttFailedException("음성 변환에 실패했습니다.", e);
        } catch (RestClientException e) {
            log.warn("STT API 호출에 실패했습니다: {}", e.getMessage());
            throw new SttFailedException("음성 변환에 실패했습니다.", e);
        }
    }
}
