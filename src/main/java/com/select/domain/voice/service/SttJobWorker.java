package com.select.domain.voice.service;

import com.select.domain.voice.client.SttClient;
import com.select.domain.voice.client.dto.response.SttResponse;
import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.exception.SttFailedException;
import com.select.domain.voice.storage.AudioStorage;
import com.select.global.config.SttJobProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * {@code PENDING} 상태의 STT 작업을 주기적으로 가져와 실제 변환을 수행한다.
 *
 * <p>{@code stt.jobs.worker.enabled=false}이면 등록되지 않으므로 테스트나
 * 별도 워커 프로세스 구성에서 유연하게 사용할 수 있다.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "stt.jobs.worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SttJobWorker {

    private static final Logger log = LoggerFactory.getLogger(SttJobWorker.class);

    private final SttJobService sttJobService;
    private final SttClient sttClient;
    private final AudioStorage audioStorage;
    private final SttJobProperties properties;

    public SttJobWorker(
            SttJobService sttJobService,
            SttClient sttClient,
            AudioStorage audioStorage,
            SttJobProperties properties
    ) {
        this.sttJobService = sttJobService;
        this.sttClient = sttClient;
        this.audioStorage = audioStorage;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${stt.jobs.worker.poll-interval:5s}",
            initialDelayString = "${stt.jobs.worker.initial-delay:10s}"
    )
    public void poll() {
        try {
            sttJobService.recoverStaleJobs();
            List<Long> readyJobIds = sttJobService.findReadyJobIds(properties.worker().batchSize());
            for (Long jobId : readyJobIds) {
                if (sttJobService.claim(jobId)) {
                    process(jobId);
                }
            }
        } catch (RuntimeException e) {
            log.error("STT 작업 폴링 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 선점된 작업 하나를 처리한다.
     */
    public void process(Long jobId) {
        SttJob job = sttJobService.findForProcessing(jobId);
        if (job == null || job.getStatus() != SttStatus.PROCESSING) {
            return;
        }

        try {
            MultipartFile audio = audioStorage.load(job.getAudioPath());
            SttResponse response = sttClient.transcribe(audio);
            String transcript = response == null ? null : response.text();
            if (!StringUtils.hasText(transcript)) {
                throw new SttFailedException("음성을 텍스트로 변환하지 못했습니다.");
            }
            sttJobService.complete(jobId, transcript.trim());
        } catch (RuntimeException e) {
            log.warn("STT 작업 처리에 실패했습니다. jobId={}", jobId, e);
            try {
                sttJobService.fail(jobId, errorMessage(e));
            } catch (RuntimeException failException) {
                log.error("STT 작업 실패 상태를 저장하지 못했습니다. jobId={}", jobId, failException);
            }
        }
    }

    private String errorMessage(RuntimeException e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return e.getClass().getSimpleName();
        }
        return message;
    }
}
