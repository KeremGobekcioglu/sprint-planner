-- Add team ownership for phase 2 access control
ALTER TABLE team
    ADD COLUMN owner_id UUID REFERENCES app_user(id);