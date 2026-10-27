CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS family_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_family_groups_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS family_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    `role` VARCHAR(30) NOT NULL,
    CONSTRAINT fk_family_members_group FOREIGN KEY (family_group_id) REFERENCES family_groups (id),
    CONSTRAINT fk_family_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS babies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    family_group_id BIGINT,
    name VARCHAR(50) NOT NULL,
    birth_date DATE NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_babies_family_group FOREIGN KEY (family_group_id) REFERENCES family_groups (id)
);

CREATE TABLE IF NOT EXISTS baby_permissions (
    baby_id BIGINT NOT NULL,
    family_member_id BIGINT NOT NULL,
    permission VARCHAR(50) NOT NULL,
    PRIMARY KEY (baby_id, family_member_id, permission),
    CONSTRAINT fk_baby_permissions_baby FOREIGN KEY (baby_id) REFERENCES babies (id),
    CONSTRAINT fk_baby_permissions_member FOREIGN KEY (family_member_id) REFERENCES family_members (id)
);

CREATE TABLE IF NOT EXISTS feeding_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    baby_id BIGINT NOT NULL,
    author_member_id BIGINT,
    voice_input_id BIGINT,
    feeding_type VARCHAR(30),
    amount_ml INTEGER,
    occurred_at DATETIME NOT NULL,
    note TEXT,
    created_at DATETIME NOT NULL,
    INDEX idx_feeding_baby_occurred_at (baby_id, occurred_at),
    CONSTRAINT fk_feeding_records_baby FOREIGN KEY (baby_id) REFERENCES babies (id)
);

CREATE TABLE IF NOT EXISTS sleep_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    baby_id BIGINT NOT NULL,
    author_member_id BIGINT,
    voice_input_id BIGINT,
    sleep_start_at DATETIME NOT NULL,
    sleep_end_at DATETIME,
    note TEXT,
    created_at DATETIME NOT NULL,
    INDEX idx_sleep_baby_start_end (baby_id, sleep_start_at, sleep_end_at),
    CONSTRAINT fk_sleep_records_baby FOREIGN KEY (baby_id) REFERENCES babies (id)
);

CREATE TABLE IF NOT EXISTS temperature_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    baby_id BIGINT NOT NULL,
    author_member_id BIGINT,
    voice_input_id BIGINT,
    temperature_celsius DECIMAL(4, 1) NOT NULL,
    measured_at DATETIME NOT NULL,
    note TEXT,
    created_at DATETIME NOT NULL,
    INDEX idx_temperature_baby_measured_at (baby_id, measured_at),
    CONSTRAINT fk_temperature_records_baby FOREIGN KEY (baby_id) REFERENCES babies (id)
);

CREATE TABLE IF NOT EXISTS daily_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    baby_id BIGINT NOT NULL,
    report_date DATE NOT NULL,
    summary TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    generated_at DATETIME NOT NULL,
    CONSTRAINT uk_daily_reports_baby_date UNIQUE (baby_id, report_date),
    CONSTRAINT fk_daily_reports_baby FOREIGN KEY (baby_id) REFERENCES babies (id)
);
