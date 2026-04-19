package ru.vstu.medsim.session.dto;

import java.util.List;

public record GameSessionParticipantsResponse(
        Long sessionId,
        String sessionCode,
        String sessionName,
        String sessionStatus,
        List<SessionTeamItem> teams,
        List<GameSessionParticipantItem> participants,
        List<SessionStageSettingItem> stages,
        SessionRuntimeItem sessionRuntime,
        Integer totalProblemCount,
        List<SessionInventoryItem> inventoryItems
) {
}
