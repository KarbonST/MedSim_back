UPDATE team_problem_states
SET status = 'ACTIVE',
    resolved_at = NULL
WHERE id IN (
    SELECT problem_state_id
    FROM team_kanban_cards
    WHERE responsible_department IS NULL
      AND status NOT IN ('REGISTERED', 'DONE')
);

UPDATE team_kanban_cards
SET status = 'REGISTERED',
    assignee_participant_id = NULL,
    completed_at = NULL
WHERE responsible_department IS NULL
  AND status NOT IN ('REGISTERED', 'DONE');
