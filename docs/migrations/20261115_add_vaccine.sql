SELECT id, vaccine_name
FROM vaccines
WHERE dose_number IS NULL;

ALTER TABLE vaccines
    MODIFY COLUMN dose_number INTEGER NOT NULL;