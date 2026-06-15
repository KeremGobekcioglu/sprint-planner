-- Preserve deterministic ordering for user stories and tasks
ALTER TABLE user_story
    ADD COLUMN story_order INT;

ALTER TABLE task
    ADD COLUMN task_order INT;
