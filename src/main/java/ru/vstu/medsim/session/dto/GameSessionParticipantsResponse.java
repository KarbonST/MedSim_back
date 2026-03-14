package ru.vstu.medsim.session.dto;

import java.util.List;

public record GameSessionParticipantsResponse(
        Long sessionId,
        String sessionCode,
        String sessionName,
        String sessionStatus,
        List<GameSessionParticipantItem> participants,
        List<SessionStageSettingItem> stages
) {
}
