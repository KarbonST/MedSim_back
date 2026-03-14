package ru.vstu.medsim.session.dto;

import java.time.LocalDateTime;

public record GameSessionParticipantItem(
        Long participantId,
        Long playerId,
        String displayName,
        String hospitalPosition,
        String gameRole,
        LocalDateTime joinedAt
) {
}
