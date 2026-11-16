-- =========================================================
-- 20261115_add_vaccine.sql
-- 기존 MySQL DB의 예방접종 스키마 정합성 보정
-- MySQL 8.0.16+
-- =========================================================

-- 1. 백신 차수 NULL 데이터 확인
SELECT
    id,
    vaccine_name,
    dose_number
FROM vaccines
WHERE dose_number IS NULL;

-- 주의:
-- 결과가 존재하면 아래 ALTER를 실행하기 전에
-- 실제 백신 차수에 맞게 데이터를 먼저 보정해야 한다.
-- 임의로 1을 넣으면 잘못된 접종 차수 데이터가 만들어질 수 있다.

-- 2. 백신 차수 NOT NULL 보장
ALTER TABLE vaccines
    MODIFY COLUMN dose_number INT NOT NULL;

-- 3. 기존 UNIQUE 인덱스 제거
-- 기존 인덱스가 uk_baby_vaccine_dose라는 이름이고
-- (baby_id, vaccine_id) 두 컬럼으로 구성되어 있다는 전제
ALTER TABLE baby_vaccinations
DROP INDEX uk_baby_vaccine_dose;

-- 4. 현재 코드와 일치하는 UNIQUE 제약조건 추가
ALTER TABLE baby_vaccinations
    ADD CONSTRAINT uk_baby_vaccine
        UNIQUE (baby_id, vaccine_id);

-- 5. 최종 구조 확인
SHOW CREATE TABLE vaccines;
SHOW CREATE TABLE baby_vaccinations;

SHOW INDEX FROM baby_vaccinations;