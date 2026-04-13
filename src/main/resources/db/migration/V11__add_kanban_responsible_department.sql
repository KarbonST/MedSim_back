ALTER TABLE team_kanban_cards
    ADD COLUMN responsible_department VARCHAR(40) NULL;

CREATE INDEX idx_team_kanban_cards_responsible_department
    ON team_kanban_cards (responsible_department);
