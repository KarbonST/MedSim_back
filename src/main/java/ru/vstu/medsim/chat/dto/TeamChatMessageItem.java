package ru.vstu.medsim.chat.dto;

import java.time.LocalDateTime;

public record TeamChatMessageItem(
        Long id,
        Long teamId,
        String teamName,
        Long participantId,
        String authorName,
        String messageText,
        LocalDateTime createdAt
) {
}
