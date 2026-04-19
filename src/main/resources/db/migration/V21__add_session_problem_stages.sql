ALTER TABLE team_problem_states ADD COLUMN stage_number INTEGER;

UPDATE team_problem_states
SET stage_number = (
    SELECT crpt.stage_number
    FROM clinic_room_problem_templates crpt
    WHERE crpt.id = clinic_room_problem_template_id
);

ALTER TABLE team_problem_states ALTER COLUMN stage_number SET NOT NULL;
ALTER TABLE team_problem_states ADD CONSTRAINT chk_team_problem_states_stage_number_positive CHECK (stage_number >= 1);

CREATE INDEX idx_team_problem_states_stage
    ON team_problem_states (stage_number);
