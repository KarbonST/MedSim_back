package ru.vstu.medsim.analytics.dto;

public record TeamParticipantAnalyticsItem(
        Long participantId,
        String displayName,
        String gameRole,
        Integer tasksAssignedCount,
        Integer tasksStartedCount,
        Integer tasksSentToReviewCount,
        Integer tasksClosedAsExecutorCount,
        Integer departmentApprovalsCount,
        Integer finalApprovalsCount,
        Integer returnsTriggeredCount,
        Integer holdsTriggeredCount
) {
}
