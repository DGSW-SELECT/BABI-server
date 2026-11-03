-- Source: babi_DB_Real.sql. Active DROP TABLE statements intentionally excluded.

-- =====================================================================
-- 육아 기록 앱 스키마 (MySQL 8.0.16+ 기준, CHECK 제약 사용)
--
-- 설계 원칙
--  1) 아기 단위 가족 공유: baby_members(+baby_permissions)가 단일 기준
--  2) baby_id 일관성 강제: 기록/권한 테이블은 (baby_id, member_id)를
--     baby_members(baby_id, id)에 복합 FK로 연결
--     -> 다른 아기의 멤버가 작성자/권한자로 들어가는 것을 DB가 차단
--  3) 복합 FK의 member 컬럼이 NULL이면 검사를 건너뜀(MATCH SIMPLE)
--     -> author_member_id NULL 허용은 의도대로 동작
--  4) 삭제 정책은 전부 NO ACTION(=RESTRICT) 유지
--     (복합 FK는 SET NULL 시 NOT NULL인 baby_id까지 null 처리하려 해서
--      쓸 수 없고, CASCADE 연쇄 삭제 순서 문제도 있음)
--  5) 가족 구성원 내보내기 = baby_members 소프트 삭제 (removed_at)
--     - removed_at IS NULL     : 현재 구성원
--     - removed_at IS NOT NULL : 내보낸(또는 탈퇴한) 구성원
--     - 행을 지우지 않으므로 기존 기록의 author_member_id 가 계속 유효함
--     - 재초대하면 같은 행의 removed_at 을 NULL 로 되돌려 재활성화
--       (UNIQUE(baby_id, user_id) 때문에 같은 사용자의 행은 하나뿐)
--  6) 회원 탈퇴 = users 소프트 삭제 + 개인정보 익명화
--     - users 행은 지우지 않음 (FK 가 계속 유효, 기록의 작성자 정보 유지)
--     - 탈퇴 시 email/nickname/password_hash 를 익명화하므로
--       같은 이메일로 재가입 가능 (새 users 행이 생기며 이전 계정과는 별개)
--     - 처리 절차(트랜잭션)는 파일 맨 아래 "탈퇴 처리 참고" 참조
--
-- DB가 막지 못해서 앱에서 지켜야 하는 규칙
--  - 내보낸/탈퇴한 멤버를 author_member_id 로 새 기록에 쓰지 않기
--  - 활성 OWNER 가 0명이 되는 상황 막기 (소유권 이전 후 내보내기/탈퇴)
--  - 이미 활성 멤버이거나 탈퇴한 사용자에게 초대 보내지 않기
--  - "현재 구성원" 조회는 removed_at IS NULL (또는 active_baby_members 뷰) 사용
--  - 로그인/세션 발급 시 users.status = 'ACTIVE' 확인
-- =====================================================================

SET NAMES utf8mb4;


-- ---------------------------------------------------------------------
-- 1. 사용자
--    탈퇴해도 행은 유지(소프트 삭제). 탈퇴 시 개인정보는 익명화:
--      email         -> 'withdrawn-<id>@deleted.invalid' (UNIQUE 유지, 재가입 가능)
--      nickname      -> '탈퇴한 사용자'  (기록의 작성자 표시에 그대로 사용 가능)
--      password_hash -> '!'             (어떤 비밀번호와도 매칭되지 않는 값)
--    행 자체를 DELETE 하는 것은 FK 가 막아 준다 (안전장치).
-- ---------------------------------------------------------------------
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
    -- status 와 withdrawn_at 이 서로 어긋나지 않도록
    CONSTRAINT `chk_users_withdrawn`
        CHECK ((`status` = 'ACTIVE'    AND `withdrawn_at` IS NULL)
            OR (`status` = 'WITHDRAWN' AND `withdrawn_at` IS NOT NULL))
);

-- ---------------------------------------------------------------------
-- 2. 아기
--    user_id -> created_by_user_id : "생성자 기록용"으로 의미를 명확히 함.
--    소유/권한 판단은 baby_members.role 과 baby_permissions 로만 한다.
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 3. 아기별 가족 구성원 (users <-> babies N:M)
--    role 허용 값(CHECK): OWNER / PARENT / GUARDIAN / CAREGIVER / VIEWER
--    (실제 서비스 역할 목록에 맞게 수정할 것)
--    아기 생성 시 생성자를 OWNER로 자동 등록하는 로직은 앱에서 처리.
--
--    removed_at (소프트 삭제)
--      NULL     : 현재 구성원
--      NOT NULL : 내보낸/탈퇴한 구성원 (기록과 작성자 정보는 그대로 유지)
--    내보내기: removed_at = NOW() + baby_permissions 삭제 (한 트랜잭션)
--    재초대 수락: removed_at = NULL, role = 초대 내용으로 덮어쓰기
--                (예전 역할이 그대로 되살아나지 않도록), 권한은 새로 부여
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `baby_members` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `baby_id`    BIGINT      NOT NULL,
    `user_id`    BIGINT      NOT NULL,
    `role`       VARCHAR(30) NOT NULL,
    `removed_at` DATETIME    NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_baby_member`      (`baby_id`, `user_id`),
    -- 복합 FK의 참조 대상으로 쓰기 위한 키 (id는 이미 유일하지만 필수)
    UNIQUE KEY `uk_baby_member_pair` (`baby_id`, `id`),
    -- "내가 속한 아기 목록" 조회: WHERE user_id = ? AND removed_at IS NULL
    KEY `idx_baby_members_user` (`user_id`, `removed_at`),
    -- role 오타 방지 ('ownr' 같은 값 저장 차단). 허용 값을 바꾸려면
    -- 이 CHECK 를 DROP 후 다시 ADD 하면 됨 (마이그레이션 필요).
    -- 주의: 기본 collation 은 대소문자를 구분하지 않아 'owner' 는 통과한다.
    --       엄격히 하려면 role 컬럼에 COLLATE utf8mb4_bin 을 지정.
    CONSTRAINT `chk_baby_members_role`
        CHECK (`role` IN ('OWNER', 'PARENT', 'GUARDIAN', 'CAREGIVER', 'VIEWER')),
    CONSTRAINT `fk_baby_members_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_baby_members_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

-- 현재 구성원만 보는 뷰: 권한 체크/구성원 목록 등 "접근 가능 여부"를 판단하는
-- 쿼리는 baby_members 를 직접 쓰지 말고 이 뷰를 사용 (removed_at 필터 누락 방지).
-- 과거 기록의 작성자 표시(내보낸 구성원 포함)는 baby_members 를 직접 JOIN.
CREATE OR REPLACE VIEW `active_baby_members` AS
SELECT `id`, `baby_id`, `user_id`, `role`
FROM `baby_members`
WHERE `removed_at` IS NULL;

-- ---------------------------------------------------------------------
-- 4. 가족 초대
--    - 같은 (아기, 초대받는 사용자)에 PENDING 초대는 1건만 허용
--      (MySQL은 부분 유니크 인덱스가 없어서 생성 컬럼 + UNIQUE로 구현.
--       PENDING이 아니면 NULL이 되고, UNIQUE는 NULL 중복을 허용함)
--    - status는 CHECK로 오타 방지
--
--    [만료 처리 주의] 이 유니크 키는 status 만 보고 시간은 모른다.
--      (생성 컬럼에는 NOW() 같은 비결정 함수를 쓸 수 없어서 DB가 스스로
--       만료를 반영할 방법이 없음)
--      그래서 expires_at 이 지났는데 status 가 PENDING 으로 남아 있으면
--      같은 사용자에 대한 새 초대가 유니크 위반으로 막힌다.
--      반드시 아래 세 가지를 함께 적용할 것.
--    1) 초대 생성 직전, 같은 트랜잭션에서 만료된 PENDING 을 먼저 정리
--         UPDATE family_invitations SET status = 'EXPIRED'
--         WHERE baby_id = :baby AND invited_user_id = :user
--           AND status = 'PENDING' AND expires_at IS NOT NULL AND expires_at <= NOW();
--         INSERT INTO family_invitations (...) VALUES (...);
--       (동시에 두 요청이 들어와도 UNIQUE 가 중복 생성을 막아 줌)
--    2) 수락/거절 시 expires_at 을 직접 검사 (status 가 아직 PENDING 이어도
--       만료된 초대는 수락 불가). 유효한 초대 조건:
--         status = 'PENDING' AND (expires_at IS NULL OR expires_at > NOW())
--    3) 주기적으로 일괄 정리 (앱 스케줄러 또는 아래 EVENT 예시)
--    expires_at 이 NULL 이면 영원히 만료되지 않는다. 항상 만료시키려면
--    NOT NULL 로 바꾸고 앱에서 기본값(예: 생성 후 7일)을 넣을 것.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `family_invitations` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT,
    `baby_id`          BIGINT      NOT NULL,
    `inviter_user_id`  BIGINT      NOT NULL,
    `invited_user_id`  BIGINT      NOT NULL,
    `status`           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `expires_at`       DATETIME    NULL,
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `pending_key`      VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN `status` = 'PENDING'
             THEN CONCAT(`baby_id`, '-', `invited_user_id`)
             ELSE NULL END
    ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invitation_pending` (`pending_key`),
    KEY `idx_invitation_baby`    (`baby_id`),
    KEY `idx_invitation_inviter` (`inviter_user_id`),
    KEY `idx_invitation_invited` (`invited_user_id`, `status`),
    -- 만료 일괄 정리용: WHERE status = 'PENDING' AND expires_at <= NOW()
    KEY `idx_invitation_expiry`  (`status`, `expires_at`),
    CONSTRAINT `chk_invitation_status`
        CHECK (`status` IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED', 'EXPIRED')),
    CONSTRAINT `chk_invitation_self`
        CHECK (`inviter_user_id` <> `invited_user_id`),
    CONSTRAINT `fk_invitation_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_invitation_inviter`
        FOREIGN KEY (`inviter_user_id`) REFERENCES `users` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_invitation_invited`
        FOREIGN KEY (`invited_user_id`) REFERENCES `users` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

-- ---------------------------------------------------------------------
-- 5. 아기별 가족 구성원 권한
--    baby_id를 복합 FK로 묶어서 "A 아기의 멤버에게 B 아기 권한" 방지.
--    멤버 내보내기/탈퇴는 baby_members 소프트 삭제라서 이 CASCADE 는 동작하지
--    않는다 -> 앱이 내보낼 때 권한 행을 명시적으로 DELETE 해야 함.
--    (removed_at 필터가 한 곳에서 빠져도 접근이 안 되도록 하는 두 번째 방어선)
--    CASCADE 는 baby_members 행을 실제로 지우는 예외 상황(데이터 정리)용.
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 6. 음성 입력
--    UNIQUE(baby_id, id): 기록 테이블이 (baby_id, voice_input_id)로
--    복합 FK를 걸 수 있게 하는 참조 대상.
--    parse_status 값(예: PENDING/PARSED/FAILED)이 확정되면 CHECK 추가 권장.
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 7. 수유 기록
-- ---------------------------------------------------------------------
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
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_feeding_voice`
        FOREIGN KEY (`baby_id`, `voice_input_id`)
        REFERENCES `voice_inputs` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

-- ---------------------------------------------------------------------
-- 8. 수면 기록 (종료 시각이 시작보다 빠를 수 없음)
-- ---------------------------------------------------------------------
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
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_sleep_voice`
        FOREIGN KEY (`baby_id`, `voice_input_id`)
        REFERENCES `voice_inputs` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

-- ---------------------------------------------------------------------
-- 9. 체온 기록 (DECIMAL(4,1): 최대 999.9 이므로 현실적 범위 CHECK)
-- ---------------------------------------------------------------------
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
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_temperature_voice`
        FOREIGN KEY (`baby_id`, `voice_input_id`)
        REFERENCES `voice_inputs` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

-- ---------------------------------------------------------------------
-- 10. 기저귀 기록
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 11. 울음 기록
--     음성 파싱(voice_inputs)이 아니라 소리 분석 결과를 저장하므로
--     voice_input_id 없이 audio_url만 가짐. (의도된 차이라면 그대로 유지)
-- ---------------------------------------------------------------------
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
    -- confidence 스케일이 0~1 이면 BETWEEN 0 AND 1 로 바꾸세요
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

-- ---------------------------------------------------------------------
-- 12. 성장 기록
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `growth_records` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `baby_id`          BIGINT       NOT NULL,
    `author_member_id` BIGINT       NULL,
    `height_cm`        DECIMAL(5,2) NULL,
    `weight_kg`        DECIMAL(5,2) NULL,
    `measured_date`    DATE         NOT NULL,
    `memo`             VARCHAR(255) NULL,
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_growth_baby_date` (`baby_id`, `measured_date`),
    KEY `idx_growth_author`    (`baby_id`, `author_member_id`),
    CONSTRAINT `chk_growth_values`
        CHECK ((`height_cm` IS NULL OR `height_cm` > 0)
           AND (`weight_kg` IS NULL OR `weight_kg` > 0)),
    CONSTRAINT `chk_growth_has_value`
        CHECK (`height_cm` IS NOT NULL OR `weight_kg` IS NOT NULL),
    CONSTRAINT `fk_growth_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_growth_author`
        FOREIGN KEY (`baby_id`, `author_member_id`)
        REFERENCES `baby_members` (`baby_id`, `id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

-- ---------------------------------------------------------------------
-- 13. 일일 리포트 (아기당 하루 1건)
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 14. 인수인계 브리핑
--     하루에 여러 번 생성될 수 있다고 보고 UNIQUE는 걸지 않음.
--     (하루 1건 정책이면 UNIQUE(baby_id, briefing_date) 추가)
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 15. 백신 마스터 (회차별로 1행)
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 16. 아기 예방접종 (babies <-> vaccines N:M)
--     vaccine_id FK 누락 수정 + 아기당 백신(회차) 1건 UNIQUE
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 17. 활동 추천
--     (원본에는 FK가 빠져 있었음 -> baby_id FK 추가)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `activity_recommendations` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `baby_id`             BIGINT       NOT NULL,
    `recommendation_date` DATE         NOT NULL,
    `title`               VARCHAR(200) NOT NULL,
    `content`             TEXT         NOT NULL,
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_activity_baby_date` (`baby_id`, `recommendation_date`),
    CONSTRAINT `fk_activity_baby`
        FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);


-- =====================================================================
-- Invitation extension approved 2026-11-03.

CREATE TABLE IF NOT EXISTS `allocated_baby_invitation_codes` (
    `code`         VARCHAR(32) NOT NULL,
    `allocated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`code`)
);

CREATE TABLE IF NOT EXISTS `baby_invitation_codes` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `baby_id`              BIGINT       NOT NULL,
    `code`                 VARCHAR(32)  NOT NULL,
    `role`                 VARCHAR(30)  NOT NULL,
    `created_by_member_id` BIGINT       NOT NULL,
    `max_uses`             INT          NOT NULL DEFAULT 1,
    `used_count`           INT          NOT NULL DEFAULT 0,
    `status`               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    `expires_at`           DATETIME     NOT NULL,
    `revoked_at`           DATETIME     NULL,
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invitation_code` (`code`),
    KEY `idx_invitation_codes_baby` (`baby_id`, `status`),
    CONSTRAINT `chk_invitation_code_role`
    CHECK (`role` IN ('PARENT', 'GUARDIAN', 'CAREGIVER', 'VIEWER')),
    CONSTRAINT `chk_invitation_code_status`
    CHECK (`status` IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT `chk_invitation_code_revoked`
    CHECK ((`status` = 'ACTIVE'  AND `revoked_at` IS NULL)
    OR (`status` = 'REVOKED' AND `revoked_at` IS NOT NULL)),
    CONSTRAINT `chk_invitation_code_uses`
    CHECK (`max_uses` >= 1 AND `used_count` >= 0 AND `used_count` <= `max_uses`),
    CONSTRAINT `fk_invitation_codes_baby`
    FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
    ON UPDATE NO ACTION ON DELETE NO ACTION,
    -- 코드를 만든 사람이 같은 아기의 구성원인지 DB가 보장
    CONSTRAINT `fk_invitation_codes_creator`
    FOREIGN KEY (`baby_id`, `created_by_member_id`)
    REFERENCES `baby_members` (`baby_id`, `id`)
    ON UPDATE NO ACTION ON DELETE NO ACTION,
    -- 모든 코드는 원장에 먼저 등록되어 있어야 함 (원장 없이 발급 불가)
    CONSTRAINT `fk_invitation_codes_code`
    FOREIGN KEY (`code`) REFERENCES `allocated_baby_invitation_codes` (`code`)
    ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE IF NOT EXISTS `baby_email_invitations` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `baby_id`              BIGINT       NOT NULL,
    `invited_by_member_id` BIGINT       NOT NULL,
    `invited_email`        VARCHAR(320) NOT NULL,
    `role`                 VARCHAR(30)  NOT NULL,
    `token_hash`           CHAR(64)     NOT NULL,
    `status`               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    `expires_at`           DATETIME     NOT NULL,
    `accepted_user_id`     BIGINT       NULL,
    `responded_at`         DATETIME     NULL,
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `pending_key`          CHAR(64) GENERATED ALWAYS AS (
    CASE WHEN `status` = 'PENDING'
    THEN SHA2(CONCAT(`baby_id`, '|', LOWER(`invited_email`)), 256)
    ELSE NULL END
    ) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_invitation_token`   (`token_hash`),
    UNIQUE KEY `uk_email_invitation_pending` (`pending_key`),
    KEY `idx_email_invitation_baby`   (`baby_id`, `status`),
    KEY `idx_email_invitation_email`  (`invited_email`, `status`),
    -- 만료 일괄 정리용: WHERE status = 'PENDING' AND expires_at <= NOW()
    KEY `idx_email_invitation_expiry` (`status`, `expires_at`),
    CONSTRAINT `chk_email_invitation_role`
    CHECK (`role` IN ('PARENT', 'GUARDIAN', 'CAREGIVER', 'VIEWER')),
    CONSTRAINT `chk_email_invitation_status`
    CHECK (`status` IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED', 'EXPIRED')),
    -- 수락된 초대에만 수락한 사용자가 있어야 함
    CONSTRAINT `chk_email_invitation_accepted`
    CHECK ((`status` = 'ACCEPTED' AND `accepted_user_id` IS NOT NULL)
    OR (`status` <> 'ACCEPTED' AND `accepted_user_id` IS NULL)),
    CONSTRAINT `fk_email_invitation_baby`
    FOREIGN KEY (`baby_id`) REFERENCES `babies` (`id`)
    ON UPDATE NO ACTION ON DELETE NO ACTION,
    -- 초대한 사람이 같은 아기의 구성원인지 DB가 보장
    CONSTRAINT `fk_email_invitation_inviter`
    FOREIGN KEY (`baby_id`, `invited_by_member_id`)
    REFERENCES `baby_members` (`baby_id`, `id`)
    ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT `fk_email_invitation_accepted_user`
    FOREIGN KEY (`accepted_user_id`) REFERENCES `users` (`id`)
    ON UPDATE NO ACTION ON DELETE NO ACTION
);

-- =====================================================================
-- [보류] family_groups : 지금은 baby_members 기반 구조로 충분하므로 주석 유지
--
-- 다시 필요해지는 경우: 한 가족이 아기 여러 명을 일괄 관리/초대하거나,
-- 가족 단위 구독/공통 설정이 생길 때.
-- 복원 순서 (이미 데이터가 있는 DB 기준, ALTER 방식):
--
-- CREATE TABLE IF NOT EXISTS `family_groups` (
--     `id`            BIGINT       NOT NULL AUTO_INCREMENT,
--     `name`          VARCHAR(100) NOT NULL,
--     `owner_user_id` BIGINT       NOT NULL,
--     `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
--     PRIMARY KEY (`id`),
--     CONSTRAINT `fk_family_groups_owner`
--         FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`id`)
--         ON UPDATE NO ACTION ON DELETE NO ACTION
-- );
--
-- ALTER TABLE `babies` ADD COLUMN `family_group_id` BIGINT NULL;
-- ALTER TABLE `babies`
--     ADD CONSTRAINT `fk_babies_family_group`
--     FOREIGN KEY (`family_group_id`) REFERENCES `family_groups` (`id`)
--     ON UPDATE NO ACTION ON DELETE NO ACTION;
-- =====================================================================


-- =====================================================================
-- [선택] 만료된 초대 일괄 정리 (MySQL EVENT)
--   event_scheduler 가 켜져 있고 EVENT 권한이 있어야 동작한다.
--   (관리형 DB는 설정이 다를 수 있으니, 앱 스케줄러로 같은 UPDATE 를
--    주기 실행해도 된다. 위 1)~2) 처리가 있으면 정리가 늦어도 문제는 없음)
--
-- SET GLOBAL event_scheduler = ON;
-- CREATE EVENT IF NOT EXISTS `ev_expire_family_invitations`
--     ON SCHEDULE EVERY 10 MINUTE
--     DO
--         UPDATE family_invitations
--         SET status = 'EXPIRED'
--         WHERE status = 'PENDING' AND expires_at IS NOT NULL AND expires_at <= NOW();
-- =====================================================================


-- =====================================================================
-- [참고] 탈퇴 처리 절차 (실행 대상이 아니라 앱 구현용 가이드, 전부 주석)
-- :uid = 탈퇴하려는 users.id
--
-- STEP 1. 탈퇴 가능 여부 확인
--   다른 활성 구성원이 남아 있는 아기의 "유일한 활성 OWNER"이면 먼저
--   소유권을 이전하게 안내 (결과가 1행 이상이면 탈퇴 보류).
--
-- SELECT m.baby_id
-- FROM baby_members m
-- WHERE m.user_id = :uid AND m.removed_at IS NULL AND m.role = 'OWNER'
--   AND NOT EXISTS (SELECT 1 FROM baby_members o
--                   WHERE o.baby_id = m.baby_id AND o.id <> m.id
--                     AND o.removed_at IS NULL AND o.role = 'OWNER')
--   AND EXISTS (SELECT 1 FROM baby_members x
--               WHERE x.baby_id = m.baby_id AND x.id <> m.id
--                 AND x.removed_at IS NULL);
--
-- STEP 2. 탈퇴 처리 (한 트랜잭션)
--
-- START TRANSACTION;
--
-- -- (a) 권한 삭제
-- DELETE p FROM baby_permissions p
-- JOIN baby_members m ON m.id = p.baby_member_id
-- WHERE m.user_id = :uid;
--
-- -- (b) 구성원 소프트 삭제 (이미 내보낸 행의 removed_at 은 보존)
-- UPDATE baby_members SET removed_at = NOW()
-- WHERE user_id = :uid AND removed_at IS NULL;
--
-- -- (c) 대기 중인 초대 취소 (보낸 것/받은 것 모두)
-- UPDATE family_invitations SET status = 'CANCELED'
-- WHERE status = 'PENDING'
--   AND (inviter_user_id = :uid OR invited_user_id = :uid);
--
-- -- (d) 개인정보 익명화 + 탈퇴 표시
-- UPDATE users
-- SET status        = 'WITHDRAWN',
--     withdrawn_at  = NOW(),
--     email         = CONCAT('withdrawn-', id, '@deleted.invalid'),
--     nickname      = '탈퇴한 사용자',
--     password_hash = '!'
-- WHERE id = :uid AND status = 'ACTIVE';
--
-- COMMIT;
--
-- STEP 3. DB 밖에서 처리할 것
--   - 로그인 세션/리프레시 토큰/푸시 토큰/소셜 로그인 연동 정보 폐기
--   - 활성 구성원이 0명이 된 아기의 데이터를 어떻게 할지 정책 결정
--       (a) 일정 기간 보관 후 삭제  (b) 즉시 삭제  (c) 계속 보관
--     삭제한다면 FK 가 NO ACTION 이므로 자식부터 순서대로:
--       baby_permissions -> 각종 *_records, daily_reports, handoff_briefings,
--       baby_vaccinations, activity_recommendations, family_invitations
--       -> voice_inputs -> baby_members -> babies
--   - 음성 파일(voice_inputs.audio_url, cry_records.audio_url)과 transcript 에
--     개인정보가 담길 수 있으니 보존/파기 범위를 정책에 맞게 결정
--   - 개인정보 보존 의무 기간 등 법적 요건은 별도 확인 필요
-- =====================================================================
