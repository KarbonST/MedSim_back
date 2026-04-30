ALTER TABLE team_inventory_items
    ADD COLUMN initial_quantity INTEGER NOT NULL DEFAULT 0;

UPDATE team_inventory_items
SET initial_quantity = quantity;

ALTER TABLE team_inventory_items
    DROP CONSTRAINT IF EXISTS team_inventory_items_quantity_check;

ALTER TABLE team_inventory_items
    ADD CONSTRAINT team_inventory_items_quantity_check CHECK (quantity >= 0);

ALTER TABLE team_inventory_items
    ADD CONSTRAINT team_inventory_items_initial_quantity_check CHECK (initial_quantity >= 0);
