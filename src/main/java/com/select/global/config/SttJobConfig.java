package com.select.global.config;

import com.select.domain.voice.storage.AudioStorage;
import com.select.domain.voice.storage.LocalAudioStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;

/**
 * 비동기 STT 작업에 필요한 저장소와 스케줄링을 활성화한다.
 *
 * <p>워커 자체는 {@code stt.jobs.worker.enabled} 조건으로 등록되므로
 * 테스트에서는 스케줄러가 실행되지 않는다.</p>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SttJobProperties.class)
public class SttJobConfig {

    @Bean
    public AudioStorage audioStorage(SttJobProperties properties) {
        return new LocalAudioStorage(Path.of(properties.storageDir()));
    }
}
