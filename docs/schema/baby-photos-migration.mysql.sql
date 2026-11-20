-- 배포 전 DBA 검토 후 별도 실행. 기존 SQL/테이블 변경 없음. 자동 실행하지 않음.
CREATE TABLE baby_photos (
    baby_id BIGINT NOT NULL,
    image_data LONGBLOB NOT NULL,
    PRIMARY KEY (baby_id),
    CONSTRAINT fk_baby_photos_baby FOREIGN KEY (baby_id) REFERENCES babies(id)
) ENGINE=InnoDB;
