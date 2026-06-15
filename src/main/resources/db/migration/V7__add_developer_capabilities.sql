-- Add freeform capability tags for flexible role matching
ALTER TABLE developer
    ADD COLUMN capabilities TEXT;
