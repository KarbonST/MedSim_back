package ru.vstu.medsim.kanban.dto;

import java.util.List;

public record TeamKanbanBoardItem(
        List<TeamKanbanCardItem> cards
) {
}
