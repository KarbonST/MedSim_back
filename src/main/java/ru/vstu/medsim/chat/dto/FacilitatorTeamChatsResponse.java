package ru.vstu.medsim.chat.dto;

import java.util.List;

public record FacilitatorTeamChatsResponse(
        String sessionCode,
        String sessionName,
        List<FacilitatorTeamChatThread> teamChats
) {
}
