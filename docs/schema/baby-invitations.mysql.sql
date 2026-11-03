-- Review and apply to the intended MySQL 8.4 environment before deploying invitation APIs.
-- This file is a deployment artifact only; it is not automatically executed.
-- Existing babies, baby_members and users tables are not modified.

CREATE TABLE baby_invitation_codes (
    baby_id BIGINT NOT NULL,
    code VARBINARY(6) NOT NULL,
    generation VARCHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (baby_id),
    CONSTRAINT uk_baby_invitation_code UNIQUE (code),
    CONSTRAINT fk_baby_invitation_code_baby FOREIGN KEY (baby_id) REFERENCES babies (id)
);

CREATE TABLE allocated_baby_invitation_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARBINARY(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_baby_invitation_code_allocation UNIQUE (code)
);

CREATE TABLE baby_email_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    baby_id BIGINT NOT NULL,
    email VARCHAR(320) NOT NULL,
    generation VARCHAR(36) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_baby_invitation_email UNIQUE (baby_id, email),
    CONSTRAINT fk_baby_email_invitation_baby FOREIGN KEY (baby_id) REFERENCES babies (id),
    CONSTRAINT ck_baby_email_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'))
);
