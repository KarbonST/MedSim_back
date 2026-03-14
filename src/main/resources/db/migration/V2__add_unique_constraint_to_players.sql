ALTER TABLE players
    ADD CONSTRAINT uq_players_display_name_position UNIQUE (display_name, hospital_position);
