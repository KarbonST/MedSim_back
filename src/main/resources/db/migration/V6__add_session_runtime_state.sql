ALTER TABLE game_sessions ADD COLUMN active_stage_number INTEGER;
ALTER TABLE game_sessions ADD COLUMN timer_status VARCHAR(30) NOT NULL DEFAULT 'STOPPED';
ALTER TABLE game_sessions ADD COLUMN timer_remaining_seconds INTEGER;
ALTER TABLE game_sessions ADD COLUMN timer_updated_at TIMESTAMP;
