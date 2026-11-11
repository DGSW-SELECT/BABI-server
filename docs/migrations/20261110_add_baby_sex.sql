-- Apply only after a reviewed backup and deployment plan; never executed automatically.
-- Existing babies remain NULL until an owner supplies the correct value.
ALTER TABLE babies ADD COLUMN sex VARCHAR(6) NULL;
