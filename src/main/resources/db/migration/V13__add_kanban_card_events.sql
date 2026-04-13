CREATE TABLE team_kanban_card_events (
    id BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL REFERENCES team_kanban_cards(id) ON DELETE CASCADE,
    actor_participant_id BIGINT NULL REFERENCES session_participants(id) ON DELETE SET NULL,
    target_participant_id BIGINT NULL REFERENCES session_participants(id) ON DELETE SET NULL,
    event_type VARCHAR(60) NOT NULL,
    from_status VARCHAR(40) NULL,
    to_status VARCHAR(40) NULL,
    priority VARCHAR(20) NULL,
    responsible_department VARCHAR(40) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_team_kanban_card_events_card
    ON team_kanban_card_events (card_id, created_at, id);

CREATE INDEX idx_team_kanban_card_events_actor
    ON team_kanban_card_events (actor_participant_id);

CREATE INDEX idx_team_kanban_card_events_target
    ON team_kanban_card_events (target_participant_id, created_at DESC, id DESC);
