-- BABI handover feature migration for MySQL 8.4
-- Review the target database and take a backup before execution.
-- This file does not select a database, drop tables, or modify existing rows.

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

CREATE TABLE IF NOT EXISTS `handover_memos` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT,
    `baby_id`          BIGINT        NOT NULL,
    `author_member_id` BIGINT        NOT NULL,
    `content`          VARCHAR(1000) NOT NULL,
    `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_handover_memos_baby_created` (`baby_id`, `created_at`, `id`),
    KEY `idx_handover_memos_author` (`baby_id`, `author_member_id`),
    CONSTRAINT `chk_handover_memos_content`
        CHECK (CHAR_LENGTH(TRIM(`content`)) > 0),
    CONSTRAINT `fk_handover_memos_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_handover_memos_author`
        FOREIGN KEY (`baby_id`, `author_member_id`)
        REFERENCES `baby_members` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);
