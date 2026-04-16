package ru.vstu.medsim.kanban.dto;

import java.math.BigDecimal;

public record KanbanSolutionOptionItem(
        Long solutionOptionId,
        String title,
        String description,
        BigDecimal budgetCost,
        Integer timeCost,
        String requiredItemName,
        Integer requiredItemQuantity,
        boolean selectable,
        String unavailableReason
) {
}
