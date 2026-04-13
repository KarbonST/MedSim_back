UPDATE session_stage_settings
SET interaction_mode = 'CHAT_WITH_PROBLEMS'
WHERE interaction_mode = 'CHAT_ONLY';
