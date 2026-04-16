CREATE TABLE kanban_solution_options (
    id BIGSERIAL PRIMARY KEY,
    problem_template_id BIGINT NOT NULL REFERENCES clinic_room_problem_templates(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500) NULL,
    budget_cost NUMERIC(12, 2) NOT NULL,
    time_cost INTEGER NOT NULL,
    required_item_name VARCHAR(200) NULL,
    required_item_quantity INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kanban_solution_options_problem
    ON kanban_solution_options (problem_template_id, sort_order, id);

INSERT INTO kanban_solution_options (
    problem_template_id,
    title,
    description,
    budget_cost,
    time_cost,
    required_item_name,
    required_item_quantity,
    sort_order,
    active
)
SELECT
    id,
    'Стандартное решение',
    'Базовый способ устранения проблемы.',
    budget_cost,
    time_cost,
    required_item_name,
    required_item_quantity,
    1,
    TRUE
FROM clinic_room_problem_templates;

CREATE TABLE team_resource_reservations (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES session_teams(id) ON DELETE CASCADE,
    kanban_card_id BIGINT NOT NULL REFERENCES team_kanban_cards(id) ON DELETE CASCADE,
    solution_option_id BIGINT NOT NULL REFERENCES kanban_solution_options(id) ON DELETE RESTRICT,
    actor_participant_id BIGINT NULL REFERENCES session_participants(id) ON DELETE SET NULL,
    status VARCHAR(40) NOT NULL,
    budget_amount NUMERIC(12, 2) NOT NULL,
    time_units INTEGER NOT NULL,
    item_name VARCHAR(200) NULL,
    item_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    committed_at TIMESTAMP NULL,
    released_at TIMESTAMP NULL
);

CREATE INDEX idx_team_resource_reservations_card_status
    ON team_resource_reservations (kanban_card_id, status, updated_at DESC, id DESC);

CREATE INDEX idx_team_resource_reservations_team_status
    ON team_resource_reservations (team_id, status, created_at, id);
