package ru.vstu.medsim.kanban.dto;

public record TeamKanbanOverviewItem(
        Long teamId,
        String teamName,
        TeamKanbanBoardItem teamKanbanBoard
) {
}
