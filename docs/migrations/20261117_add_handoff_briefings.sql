-- Apply only after a reviewed backup and deployment plan; never executed automatically.
-- This migration only creates the handoff briefing table and does not modify existing rows.
CREATE TABLE IF NOT EXISTS `handoff_briefings` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `baby_id`       BIGINT      NOT NULL,
    `briefing_date` DATE        NOT NULL,
    `period_start`  DATETIME    NULL,
    `period_end`    DATETIME    NULL,
    `summary`       TEXT        NOT NULL,
    `status`        VARCHAR(20) NOT NULL,
    `generated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_handoff_baby_date` (`baby_id`, `briefing_date`),
    CONSTRAINT `chk_handoff_period`
        CHECK (`period_start` IS NULL OR `period_end` IS NULL OR `period_end` >= `period_start`),
    CONSTRAINT `fk_handoff_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);
