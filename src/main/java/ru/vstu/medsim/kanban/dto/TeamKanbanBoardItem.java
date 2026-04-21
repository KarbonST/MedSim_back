package ru.vstu.medsim.kanban.dto;

import java.util.List;

public record TeamKanbanBoardItem(
        String finalStageCrisisType,
        String finalStageCrisisTitle,
        String finalStageCrisisDescription,
        Integer activeEscalationCount,
        List<TeamKanbanCardItem> cards
) {
}
