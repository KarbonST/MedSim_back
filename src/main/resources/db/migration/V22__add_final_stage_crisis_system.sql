ALTER TABLE game_sessions
    ADD COLUMN final_stage_crisis_type VARCHAR(40) NULL;

ALTER TABLE game_sessions
    ADD COLUMN final_stage_crisis_activated_at TIMESTAMP NULL;

ALTER TABLE team_problem_states
    ADD COLUMN escalation_type VARCHAR(40) NULL;

ALTER TABLE team_problem_states
    ADD COLUMN escalated_at TIMESTAMP NULL;

ALTER TABLE team_problem_states
    ADD COLUMN escalation_resolved_at TIMESTAMP NULL;

CREATE INDEX idx_team_problem_states_escalation_type
    ON team_problem_states (escalation_type);
