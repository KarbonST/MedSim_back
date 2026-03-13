package ru.vstu.medsim.player.dto;

import java.time.LocalDateTime;

public record PlayerSessionJoinResponse(
        Long participantId,
        Long playerId,
        Long sessionId,
        String sessionCode,
        String sessionName,
        String sessionStatus,
        String displayName,
        String hospitalPosition,
        String gameRole,
        LocalDateTime joinedAt
) {
}
