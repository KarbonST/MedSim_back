package ru.vstu.medsim.session.dto;

public record GameSessionSummaryResponse(
        Long sessionId,
        String sessionCode,
        String sessionName,
        String sessionStatus,
        long participantCount,
        long stageCount
) {
}
