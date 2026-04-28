package ru.vstu.medsim.analytics.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GameSessionAnalyticsResponse(
        Long sessionId,
        String sessionCode,
        String sessionName,
        String sessionStatus,
        String finalStageCrisisType,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer teamCount,
        Integer participantCount,
        Integer totalProblemCount,
        Integer resolvedProblemCount,
        Integer unresolvedProblemCount,
        Integer totalReturnCount,
        Integer totalHoldCount,
        Integer totalEscalatedProblemCount,
        List<SessionAnalyticsStageItem> stages,
        List<TeamAnalyticsItem> teams
) {
}
