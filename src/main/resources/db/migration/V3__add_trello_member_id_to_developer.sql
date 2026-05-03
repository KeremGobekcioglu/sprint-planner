-- Add trello_member_id column to developer table
-- Used to map developers to Trello board members during import and card assignment

ALTER TABLE developer ADD COLUMN trello_member_id VARCHAR(100);

