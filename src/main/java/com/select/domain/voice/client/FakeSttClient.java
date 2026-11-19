package com.select.domain.voice.client;

import com.select.domain.voice.client.dto.response.SttResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 실제 STT 모델 없이 전체 파이프라인을 검증하기 위한 개발용 클라이언트.
 *
 * <p>실제 모델이 확정되면 {@code stt.provider=openai}로 전환하면 된다.
 * 응답 문구는 {@code stt.fake-transcript}로 바꿀 수 있다.</p>
 */
public class FakeSttClient implements SttClient {

    private final String transcript;

    public FakeSttClient(String transcript) {
        this.transcript = transcript;
    }

    @Override
    public SttResponse transcribe(MultipartFile audio) {
        return new SttResponse(transcript);
    }
}
