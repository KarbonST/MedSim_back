package ru.vstu.medsim.chat.dto;

import java.util.List;

public record FacilitatorTeamChatThread(
        Long teamId,
        String teamName,
        int sortOrder,
        List<TeamChatMessageItem> messages
) {
}
