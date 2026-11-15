SHOW CREATE TABLE vaccines;
SHOW CREATE TABLE baby_vaccinations;

SELECT id, vaccine_name FROM vaccines WHERE dose_number IS NULL;

UPDATE vaccines SET dose_number = 1 WHERE dose_number IS NULL;
ALTER TABLE vaccines MODIFY dose_number INT NOT NULL;

ALTER TABLE baby_vaccinations ADD COLUMN dose_number INT NULL AFTER vaccine_id;

UPDATE baby_vaccinations bv
    JOIN vaccines v ON v.id = bv.vaccine_id
    SET bv.dose_number = v.dose_number
WHERE bv.dose_number IS NULL;

SELECT COUNT(*) FROM baby_vaccinations WHERE dose_number IS NULL;

ALTER TABLE baby_vaccinations MODIFY dose_number INT NOT NULL;

ALTER TABLE baby_vaccinations
    ADD CONSTRAINT uk_baby_vaccine_dose UNIQUE (baby_id, vaccine_id, dose_number);
ALTER TABLE baby_vaccinations DROP INDEX uk_baby_vaccine;