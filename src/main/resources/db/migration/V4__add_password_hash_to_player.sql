ALTER TABLE players
    ADD COLUMN password_hash VARCHAR(255);

UPDATE players
SET password_hash = 'TEMPORARY_ACCOUNT_LOCKED_HASH'
WHERE password_hash IS NULL;

ALTER TABLE players
    ALTER COLUMN password_hash SET NOT NULL;