package ru.vstu.medsim.chat.dto;

import java.util.List;

public record PlayerTeamChatResponse(
        Long teamId,
        String teamName,
        List<TeamChatMessageItem> messages
) {
}
