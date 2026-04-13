package ru.vstu.medsim.kanban.dto;

import java.time.LocalDateTime;

public record KanbanCardHistoryItem(
        Long eventId,
        String eventType,
        String message,
        String actorName,
        String actorRole,
        String targetName,
        String targetRole,
        String priority,
        String responsibleDepartment,
        LocalDateTime createdAt
) {
}
