-- Isolated test fixture, not a migration for an existing BABI database.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB;
CREATE TABLE babies (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    birth_date DATE NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE baby_members (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    baby_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    CONSTRAINT uk_baby_members_baby_user UNIQUE (baby_id, user_id)
) ENGINE=InnoDB;
