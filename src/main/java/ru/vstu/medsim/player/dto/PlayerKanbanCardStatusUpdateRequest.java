package ru.vstu.medsim.player.dto;

import jakarta.validation.constraints.NotNull;
import ru.vstu.medsim.kanban.domain.KanbanCardStatus;
import ru.vstu.medsim.kanban.domain.KanbanCardPriority;
import ru.vstu.medsim.kanban.domain.KanbanResponsibleDepartment;

public record PlayerKanbanCardStatusUpdateRequest(
        @NotNull KanbanCardStatus status,
        KanbanCardPriority priority,
        KanbanResponsibleDepartment responsibleDepartment,
        Long assigneeParticipantId
) {
}
