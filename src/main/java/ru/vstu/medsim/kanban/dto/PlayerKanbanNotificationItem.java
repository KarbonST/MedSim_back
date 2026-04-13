package ru.vstu.medsim.kanban.dto;

import java.time.LocalDateTime;

public record PlayerKanbanNotificationItem(
        Long notificationId,
        Long cardId,
        String type,
        String title,
        String message,
        LocalDateTime createdAt
) {
}
