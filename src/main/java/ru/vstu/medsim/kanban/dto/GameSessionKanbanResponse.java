package ru.vstu.medsim.kanban.dto;

import java.util.List;

public record GameSessionKanbanResponse(
        Long sessionId,
        String sessionCode,
        String sessionName,
        String sessionStatus,
        List<TeamKanbanOverviewItem> teams
) {
}
