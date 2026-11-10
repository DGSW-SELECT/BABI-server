-- H2 fixture for the domains exercised by application tests. Full DDL is validated with MySQL.

CREATE TABLE IF NOT EXISTS `users` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `email`         VARCHAR(320)  NOT NULL,
    `password_hash` VARCHAR(255)  NOT NULL,
    `nickname`      VARCHAR(50)   NOT NULL,
    `status`        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    `withdrawn_at`  DATETIME      NULL,
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`),
    CONSTRAINT `chk_users_status`
        CHECK (`status` IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT `chk_users_withdrawn`
        CHECK ((`status` = 'ACTIVE'    AND `withdrawn_at` IS NULL)
            OR (`status` = 'WITHDRAWN' AND `withdrawn_at` IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS `babies` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `created_by_user_id` BIGINT       NOT NULL,
    `name`               VARCHAR(50)  NOT NULL,
    `birth_date`         DATE         NOT NULL,
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_babies_created_by` (`created_by_user_id`),
    CONSTRAINT `fk_babies_created_by`
        FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS `baby_members` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `baby_id`    BIGINT      NOT NULL,
    `user_id`    BIGINT      NOT NULL,
    `role`       VARCHAR(30) NOT NULL,
    `removed_at` DATETIME    NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_baby_member`      (`baby_id`, `user_id`),
    UNIQUE KEY `uk_baby_member_pair` (`baby_id`, `id`),
    KEY `idx_baby_members_user` (`user_id`, `removed_at`),
    CONSTRAINT `chk_baby_members_role`
        CHECK (`role` IN ('OWNER', 'PARENT', 'GUARDIAN', 'CAREGIVER', 'VIEWER')),
    CONSTRAINT `fk_baby_members_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_baby_members_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS `baby_permissions` (
    `baby_id`        BIGINT      NOT NULL,
    `baby_member_id` BIGINT      NOT NULL,
    `permission`     VARCHAR(50) NOT NULL,
    PRIMARY KEY (`baby_member_id`, `permission`),
    KEY `idx_baby_permissions_baby` (`baby_id`),
    CONSTRAINT `fk_baby_permissions_member`
        FOREIGN KEY (`baby_id`, `baby_member_id`)
        REFERENCES `baby_members` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `feeding_records` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT,
    `baby_id`          BIGINT      NOT NULL,
    `author_member_id` BIGINT      NULL,
    `voice_input_id`   BIGINT      NULL,
    `feeding_type`     VARCHAR(30) NULL,
    `amount_ml`        INTEGER     NULL,
    `occurred_at`      DATETIME    NOT NULL,
    `note`             TEXT        NULL,
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_feeding_baby_time` (`baby_id`, `occurred_at`),
    KEY `idx_feeding_author`    (`baby_id`, `author_member_id`),
    KEY `idx_feeding_voice`     (`baby_id`, `voice_input_id`),
    CONSTRAINT `chk_feeding_amount` CHECK (`amount_ml` IS NULL OR `amount_ml` >= 0),
    CONSTRAINT `fk_feeding_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_feeding_author`
        FOREIGN KEY (`baby_id`, `author_member_id`)
        REFERENCES `baby_members` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS `sleep_records` (
    `id`               BIGINT   NOT NULL AUTO_INCREMENT,
    `baby_id`          BIGINT   NOT NULL,
    `author_member_id` BIGINT   NULL,
    `voice_input_id`   BIGINT   NULL,
    `sleep_start_at`   DATETIME NOT NULL,
    `sleep_end_at`     DATETIME NULL,
    `note`             TEXT     NULL,
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sleep_baby_time` (`baby_id`, `sleep_start_at`),
    KEY `idx_sleep_author`    (`baby_id`, `author_member_id`),
    KEY `idx_sleep_voice`     (`baby_id`, `voice_input_id`),
    CONSTRAINT `chk_sleep_time`
        CHECK (`sleep_end_at` IS NULL OR `sleep_end_at` >= `sleep_start_at`),
    CONSTRAINT `fk_sleep_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_sleep_author`
        FOREIGN KEY (`baby_id`, `author_member_id`)
        REFERENCES `baby_members` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS `temperature_records` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `baby_id`             BIGINT       NOT NULL,
    `author_member_id`    BIGINT       NULL,
    `voice_input_id`      BIGINT       NULL,
    `temperature_celsius` DECIMAL(4,1) NOT NULL,
    `measured_at`         DATETIME     NOT NULL,
    `note`                TEXT         NULL,
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_temperature_baby_time` (`baby_id`, `measured_at`),
    KEY `idx_temperature_author`    (`baby_id`, `author_member_id`),
    KEY `idx_temperature_voice`     (`baby_id`, `voice_input_id`),
    CONSTRAINT `chk_temperature_range`
        CHECK (`temperature_celsius` BETWEEN 30.0 AND 45.0),
    CONSTRAINT `fk_temperature_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_temperature_author`
        FOREIGN KEY (`baby_id`, `author_member_id`)
        REFERENCES `baby_members` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS `daily_reports` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `baby_id`      BIGINT      NOT NULL,
    `report_date`  DATE        NOT NULL,
    `summary`      TEXT        NOT NULL,
    `status`       VARCHAR(20) NOT NULL,
    `generated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_daily_report` (`baby_id`, `report_date`),
    CONSTRAINT `fk_daily_reports_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS `vaccines` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT,
    `vaccine_name`            VARCHAR(100) NOT NULL,
    `dose_number`             INTEGER      NULL,
    `recommended_start_month` INTEGER      NULL,
    `recommended_end_month`   INTEGER      NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vaccine_dose` (`vaccine_name`, `dose_number`),
    CONSTRAINT `chk_vaccine_months`
    CHECK (`recommended_start_month` IS NULL
           OR `recommended_end_month` IS NULL
           OR `recommended_end_month` >= `recommended_start_month`)
    );

CREATE TABLE IF NOT EXISTS `baby_vaccinations` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `baby_id`        BIGINT       NOT NULL,
    `vaccine_id`     BIGINT       NOT NULL,
    `scheduled_date` DATE         NULL,
    `completed_date` DATE         NULL,
    `institution`    VARCHAR(255) NULL,
    `memo`           VARCHAR(255) NULL,
    `completed`      BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_baby_vaccine` (`baby_id`, `vaccine_id`),
    KEY `idx_baby_vaccinations_vaccine` (`vaccine_id`),
    KEY `idx_baby_vaccinations_schedule` (`baby_id`, `scheduled_date`),
    -- 완료 여부와 완료일은 항상 함께 움직여야 함 (양방향 일관성)
    --   완료 처리: UPDATE ... SET completed = TRUE,  completed_date = <날짜>  (한 문장으로)
    --   완료 취소: UPDATE ... SET completed = FALSE, completed_date = NULL    (한 문장으로)
    --   새 접종 일정 INSERT 시 completed_date 는 NULL (예정일은 scheduled_date 에)
    CONSTRAINT `chk_vaccination_completed`
    CHECK ((`completed` = TRUE  AND `completed_date` IS NOT NULL)
    OR (`completed` = FALSE AND `completed_date` IS NULL)),
    CONSTRAINT `fk_baby_vaccinations_baby`
    FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
    ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_baby_vaccinations_vaccine`
    FOREIGN KEY (`vaccine_id`) REFERENCES `vaccines` (`id`)
    ON UPDATE NO ACTION ON DELETE NO ACTION
    );

CREATE OR REPLACE VIEW active_baby_members AS SELECT id, baby_id, user_id, `role` FROM baby_members WHERE removed_at IS NULL;

-- H2-compatible subset of the final invitation schema; MySQL constraints are verified separately.
CREATE TABLE IF NOT EXISTS allocated_baby_invitation_codes (
    code VARCHAR(32) NOT NULL PRIMARY KEY,
    allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS baby_invitation_codes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    baby_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL UNIQUE,
    role VARCHAR(30) NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    max_uses INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (max_uses >= 1 AND used_count >= 0 AND used_count <= max_uses),
    FOREIGN KEY (baby_id) REFERENCES babies(id),
    FOREIGN KEY (baby_id, created_by_member_id) REFERENCES baby_members(baby_id, id),
    FOREIGN KEY (code) REFERENCES allocated_baby_invitation_codes(code)
);
CREATE TABLE IF NOT EXISTS baby_email_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    baby_id BIGINT NOT NULL,
    invited_by_member_id BIGINT NOT NULL,
    invited_email VARCHAR(320) NOT NULL,
    role VARCHAR(30) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME NOT NULL,
    accepted_user_id BIGINT,
    responded_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (baby_id) REFERENCES babies(id),
    FOREIGN KEY (baby_id, invited_by_member_id) REFERENCES baby_members(baby_id, id),
    FOREIGN KEY (accepted_user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS `voice_inputs` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `baby_id`        BIGINT       NOT NULL,
    `baby_member_id` BIGINT       NULL,
    `audio_url`      VARCHAR(500) NULL,
    `transcript`     TEXT         NOT NULL,
    `parse_status`   VARCHAR(20)  NOT NULL,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_voice_inputs_pair` (`baby_id`, `id`),
    KEY `idx_voice_inputs_baby_time` (`baby_id`, `created_at`),
    CONSTRAINT `fk_voice_inputs_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_voice_inputs_member`
        FOREIGN KEY (`baby_id`, `baby_member_id`)
        REFERENCES `baby_members` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS `diaper_records` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT,
    `baby_id`          BIGINT      NOT NULL,
    `author_member_id` BIGINT      NULL,
    `voice_input_id`   BIGINT      NULL,
    `diaper_type`      VARCHAR(20) NOT NULL,
    `occurred_at`      DATETIME    NOT NULL,
    `note`             TEXT        NULL,
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_diaper_baby_time` (`baby_id`, `occurred_at`),
    KEY `idx_diaper_author`    (`baby_id`, `author_member_id`),
    KEY `idx_diaper_voice`     (`baby_id`, `voice_input_id`),
    CONSTRAINT `fk_diaper_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_diaper_author`
        FOREIGN KEY (`baby_id`, `author_member_id`)
        REFERENCES `baby_members` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_diaper_voice`
        FOREIGN KEY (`baby_id`, `voice_input_id`)
        REFERENCES `voice_inputs` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS `cry_records` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `baby_id`          BIGINT       NOT NULL,
    `author_member_id` BIGINT       NULL,
    `audio_url`        VARCHAR(500) NULL,
    `cry_type`         VARCHAR(50)  NULL,
    `confidence`       DECIMAL(5,2) NULL,
    `occurred_at`      DATETIME     NOT NULL,
    `note`             TEXT         NULL,
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_cry_baby_time` (`baby_id`, `occurred_at`),
    KEY `idx_cry_author`    (`baby_id`, `author_member_id`),
    CONSTRAINT `chk_cry_confidence`
        CHECK (`confidence` IS NULL OR `confidence` BETWEEN 0 AND 100),
    CONSTRAINT `fk_cry_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_cry_author`
        FOREIGN KEY (`baby_id`, `author_member_id`)
        REFERENCES `baby_members` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);
