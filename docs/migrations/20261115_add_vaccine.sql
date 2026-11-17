-- =========================================================
-- 20261115_add_vaccine.sql
-- vaccines.dose_number NOT NULL 보장
-- MySQL 8.0+ / DDL은 암묵적 커밋되므로 실행 전 백업 필수
-- =========================================================

-- 1. NULL 차수 데이터 확인
SELECT v.id, v.vaccine_name, v.dose_number,
       (SELECT COUNT(*) FROM baby_vaccinations bv
        WHERE bv.vaccine_id = v.id) AS referenced_by
FROM vaccines v
WHERE v.dose_number IS NULL;

-- 결과가 1건이라도 있으면 아래 2번을 실행하지 말고
-- 실제 백신 차수로 먼저 UPDATE 한다. (임의로 1을 넣지 않는다)
-- 예) UPDATE vaccines SET dose_number = 1 WHERE id = ?;
-- 주의: (vaccine_name, dose_number) 유니크라 같은 이름의 차수는 겹치면 안 된다.

-- 2. NOT NULL 적용
ALTER TABLE vaccines
    MODIFY COLUMN dose_number INT NOT NULL;

-- 3. 검증 (uk_baby_vaccine 이 (baby_id, vaccine_id) 유니크인지 확인)
SELECT index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'baby_vaccinations'
  AND index_name = 'uk_baby_vaccine'
ORDER BY seq_in_index;
-- 기대 결과: non_unique=0, 1행 baby_id, 2행 vaccine_id

SHOW CREATE TABLE vaccines;