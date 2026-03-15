CREATE TABLE team_chat_messages (
    id BIGSERIAL PRIMARY KEY,
    game_session_id BIGINT NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL REFERENCES session_teams(id) ON DELETE CASCADE,
    participant_id BIGINT NOT NULL REFERENCES session_participants(id) ON DELETE CASCADE,
    message_text VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_team_chat_messages_session_team_created_at
    ON team_chat_messages (game_session_id, team_id, created_at, id);
