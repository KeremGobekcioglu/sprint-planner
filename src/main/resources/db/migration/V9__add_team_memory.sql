CREATE TABLE team_memory (
    team_id   UUID         NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    file_type VARCHAR(20)  NOT NULL,
    content   TEXT         NOT NULL DEFAULT '',
    updated_at TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (team_id, file_type)
);
