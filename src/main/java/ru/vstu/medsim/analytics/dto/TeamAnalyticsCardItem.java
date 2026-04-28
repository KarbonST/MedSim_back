package ru.vstu.medsim.analytics.dto;

public record TeamAnalyticsCardItem(
        Long cardId,
        Long problemStateId,
        Integer problemNumber,
        String title,
        String roomCode,
        String roomName,
        Integer stageNumber,
        String status,
        String priority,
        String responsibleDepartment,
        String assigneeName,
        Boolean resolved,
        Boolean escalated,
        Integer returnCount,
        Integer holdCount,
        Long distributionSeconds,
        Long reactionSeconds,
        Long workSeconds,
        Long departmentReviewSeconds,
        Long chiefReviewSeconds,
        Long fullCycleSeconds
) {
}
