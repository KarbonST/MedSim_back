package ru.vstu.medsim.kanban.dto;

import java.util.List;

public record TeamKanbanCardItem(
        Long cardId,
        Long problemStateId,
        Integer problemNumber,
        Integer stageNumber,
        String title,
        String roomCode,
        String roomName,
        String severity,
        String priority,
        java.math.BigDecimal budgetCost,
        Integer timeCost,
        String requiredItemName,
        Integer requiredItemQuantity,
        List<KanbanSolutionOptionItem> solutionOptions,
        Long selectedSolutionOptionId,
        String selectedSolutionTitle,
        String reservationStatus,
        java.math.BigDecimal reservedBudgetAmount,
        Integer reservedTimeUnits,
        String reservedItemName,
        Integer reservedItemQuantity,
        boolean resourcesSpent,
        String responsibleDepartment,
        String status,
        Long assigneeParticipantId,
        String assigneeName,
        List<KanbanCardHistoryItem> history
) {
}
