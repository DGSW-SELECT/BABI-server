package com.select.domain.voice.client;

import com.select.domain.voice.client.dto.response.SttResponse;
import org.springframework.web.multipart.MultipartFile;

public interface SttClient {

    SttResponse transcribe(MultipartFile audio);
}
