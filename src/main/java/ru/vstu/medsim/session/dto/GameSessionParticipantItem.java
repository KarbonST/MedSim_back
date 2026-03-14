package ru.vstu.medsim.session.dto;

import java.time.LocalDateTime;

public record GameSessionParticipantItem(
        Long participantId,
        Long playerId,
        String displayName,
        String hospitalPosition,
        Long teamId,
        String teamName,
        String gameRole,
        LocalDateTime joinedAt
) {
}
