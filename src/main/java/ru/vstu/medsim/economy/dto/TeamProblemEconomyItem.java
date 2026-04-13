package ru.vstu.medsim.economy.dto;

import java.math.BigDecimal;

public record TeamProblemEconomyItem(
        Long problemStateId,
        Integer problemNumber,
        Integer stageNumber,
        String title,
        String severity,
        BigDecimal budgetCost,
        Integer timeCost,
        String requiredItemName,
        Integer requiredItemQuantity,
        BigDecimal ignorePenalty,
        Integer penaltyWeight,
        String status
) {
}
