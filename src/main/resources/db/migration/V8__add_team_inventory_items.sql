CREATE TABLE team_inventory_items (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES session_teams(id) ON DELETE CASCADE,
    item_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_team_inventory_items_team_item UNIQUE (team_id, item_name)
);

CREATE INDEX idx_team_inventory_items_team_item_name
    ON team_inventory_items (team_id, item_name);
