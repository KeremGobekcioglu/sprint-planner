CREATE TABLE team (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(50) NOT NULL,
    sprint_length_days INTEGER NOT NULL,
    trello_board_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE developer (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    base_velocity INTEGER NOT NULL,
    CONSTRAINT fk_developer_team FOREIGN KEY (team_id) REFERENCES team(id)
);

CREATE TABLE sprint (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    sprint_number INTEGER NOT NULL,
    goal VARCHAR(500) NOT NULL,
    domain_override VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    planned_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    CONSTRAINT fk_sprint_team FOREIGN KEY (team_id) REFERENCES team(id)
);

CREATE TABLE user_story (
    id UUID PRIMARY KEY,
    sprint_id UUID NOT NULL,
    epic_title VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    story_points INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_user_story_sprint FOREIGN KEY (sprint_id) REFERENCES sprint(id)
);

CREATE TABLE task (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL,
    assigned_dev_id UUID,
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    acceptance_criterion TEXT NOT NULL,
    role_tag VARCHAR(50) NOT NULL,
    story_points INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    trello_card_id VARCHAR(255),
    CONSTRAINT fk_task_story FOREIGN KEY (story_id) REFERENCES user_story(id),
    CONSTRAINT fk_task_developer FOREIGN KEY (assigned_dev_id) REFERENCES developer(id)
);

CREATE TABLE sprint_review (
    id UUID PRIMARY KEY,
    sprint_id UUID NOT NULL UNIQUE,
    goal_result VARCHAR(50) NOT NULL,
    blocker_note TEXT,
    total_planned_pts INTEGER NOT NULL,
    total_completed_pts INTEGER NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sprint_review_sprint FOREIGN KEY (sprint_id) REFERENCES sprint(id)
);

CREATE TABLE dev_completion (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL,
    developer_id UUID NOT NULL,
    planned_pts INTEGER NOT NULL,
    completed_pts INTEGER NOT NULL,
    CONSTRAINT fk_dev_completion_review FOREIGN KEY (review_id) REFERENCES sprint_review(id),
    CONSTRAINT fk_dev_completion_developer FOREIGN KEY (developer_id) REFERENCES developer(id)
);

CREATE TABLE edit_log (
    id UUID PRIMARY KEY,
    sprint_id UUID NOT NULL,
    field VARCHAR(255) NOT NULL,
    original_value TEXT NOT NULL,
    new_value TEXT NOT NULL,
    task_type VARCHAR(100) NOT NULL,
    logged_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_edit_log_sprint FOREIGN KEY (sprint_id) REFERENCES sprint(id)
);
