-- Add display name and role columns to app_user for phase 2 auth
ALTER TABLE app_user
    ADD COLUMN name VARCHAR(255);

UPDATE app_user
SET name = COALESCE(NULLIF(split_part(email, '@', 1), ''), email)
WHERE name IS NULL;

ALTER TABLE app_user
    ALTER COLUMN name SET NOT NULL;

ALTER TABLE app_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

UPDATE app_user
SET role = 'USER'
WHERE role IS NULL;

