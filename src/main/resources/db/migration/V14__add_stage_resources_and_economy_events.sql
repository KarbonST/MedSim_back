ALTER TABLE clinic_room_problem_templates ADD COLUMN stage_number INTEGER NOT NULL DEFAULT 2;
ALTER TABLE clinic_room_problem_templates ADD COLUMN budget_cost NUMERIC(12, 2) NOT NULL DEFAULT 1.00;
ALTER TABLE clinic_room_problem_templates ADD COLUMN time_cost INTEGER NOT NULL DEFAULT 1;
ALTER TABLE clinic_room_problem_templates ADD COLUMN required_item_name VARCHAR(200) NULL;
ALTER TABLE clinic_room_problem_templates ADD COLUMN required_item_quantity INTEGER NOT NULL DEFAULT 0;

UPDATE clinic_room_problem_templates
SET stage_number = CASE
    WHEN problem_number BETWEEN 1 AND 13 THEN 2
    WHEN problem_number BETWEEN 14 AND 26 THEN 3
    ELSE 4
END,
budget_cost = CASE severity
    WHEN 'CRITICAL' THEN 3.00
    WHEN 'SERIOUS' THEN 2.00
    ELSE 1.00
END,
time_cost = CASE severity
    WHEN 'CRITICAL' THEN 3
    WHEN 'SERIOUS' THEN 2
    ELSE 1
END;

UPDATE clinic_room_problem_templates
SET required_item_name = CASE
    WHEN LOWER(title) LIKE '%ведр%' THEN 'Ведра для отходов'
    WHEN LOWER(title) LIKE '%уф-ламп%' THEN 'УФ лампы'
    WHEN LOWER(title) LIKE '%ламп%' THEN 'Лампы основного освещения'
    WHEN LOWER(title) LIKE '%лекарств%' THEN 'Лекарства'
    WHEN LOWER(title) LIKE '%градусник%' THEN 'Футляры для градусников'
    WHEN LOWER(title) LIKE '%перчатк%' THEN 'Одноразовые перчатки'
    WHEN LOWER(title) LIKE '%простын%' THEN 'Простыни'
    WHEN LOWER(title) LIKE '%свинцов%' THEN 'Свинцовые накидки'
    WHEN LOWER(title) LIKE '%антисептик%' THEN 'Антисептик'
    WHEN LOWER(title) LIKE '%туалетная бумага%' THEN 'Туалетная бумага'
    WHEN LOWER(title) LIKE '%мыло%' THEN 'Мыло'
    WHEN LOWER(title) LIKE '%инвалидн%' THEN 'Инвалидное кресло'
    ELSE NULL
END;

UPDATE clinic_room_problem_templates
SET required_item_quantity = CASE
    WHEN required_item_name IS NULL THEN 0
    ELSE 1
END;

ALTER TABLE team_kanban_cards ALTER COLUMN priority DROP NOT NULL;
ALTER TABLE team_kanban_cards ADD COLUMN resources_spent_at TIMESTAMP NULL;

UPDATE team_kanban_cards
SET priority = NULL
WHERE status IN ('REGISTERED', 'REWORK')
  AND responsible_department IS NULL;

CREATE TABLE team_economy_events (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES session_teams(id) ON DELETE CASCADE,
    actor_participant_id BIGINT NULL REFERENCES session_participants(id) ON DELETE SET NULL,
    kanban_card_id BIGINT NULL REFERENCES team_kanban_cards(id) ON DELETE SET NULL,
    event_type VARCHAR(60) NOT NULL,
    stage_number INTEGER NULL,
    amount_delta NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    time_delta INTEGER NOT NULL DEFAULT 0,
    item_name VARCHAR(200) NULL,
    item_quantity_delta INTEGER NOT NULL DEFAULT 0,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_team_economy_events_team
    ON team_economy_events (team_id, created_at DESC, id DESC);

CREATE INDEX idx_team_economy_events_team_stage_type
    ON team_economy_events (team_id, stage_number, event_type);
