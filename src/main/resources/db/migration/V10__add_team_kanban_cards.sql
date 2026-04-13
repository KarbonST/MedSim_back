CREATE TABLE team_kanban_cards (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES session_teams(id) ON DELETE CASCADE,
    problem_state_id BIGINT NOT NULL REFERENCES team_problem_states(id) ON DELETE CASCADE,
    assignee_participant_id BIGINT NULL REFERENCES session_participants(id) ON DELETE SET NULL,
    status VARCHAR(40) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT uq_team_kanban_card_problem UNIQUE (team_id, problem_state_id)
);

CREATE INDEX idx_team_kanban_cards_team_status
    ON team_kanban_cards (team_id, status);

CREATE INDEX idx_team_kanban_cards_problem
    ON team_kanban_cards (problem_state_id);

CREATE INDEX idx_team_kanban_cards_assignee
    ON team_kanban_cards (assignee_participant_id);
