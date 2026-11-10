CREATE TABLE `handover_memos` (
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
