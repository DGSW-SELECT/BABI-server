-- Apply only after a reviewed backup and deployment plan; never executed automatically.
-- 비동기 STT 작업 테이블. 애플리케이션은 ddl-auto=validate로만 검증한다.
CREATE TABLE IF NOT EXISTS stt_jobs (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    baby_id         BIGINT        NOT NULL,
    baby_member_id  BIGINT        NOT NULL,
    audio_path      VARCHAR(500)  NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    transcript      TEXT          NULL,
    voice_input_id  BIGINT        NULL,
    attempt_count   INT           NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000) NULL,
    next_attempt_at DATETIME      NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    started_at      DATETIME      NULL,
    completed_at    DATETIME      NULL,
    PRIMARY KEY (id),
    KEY idx_stt_jobs_baby_id (baby_id),
    KEY idx_stt_jobs_status_next_attempt_at (status, next_attempt_at),
    CONSTRAINT fk_stt_jobs_baby
        FOREIGN KEY (baby_id) REFERENCES babies (id)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT fk_stt_jobs_member
        FOREIGN KEY (baby_id, baby_member_id) REFERENCES baby_members (baby_id, id)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);
