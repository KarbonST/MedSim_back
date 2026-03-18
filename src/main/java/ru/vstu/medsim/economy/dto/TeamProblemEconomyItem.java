package ru.vstu.medsim.economy.dto;

import java.math.BigDecimal;

public record TeamProblemEconomyItem(
        Long problemStateId,
        Integer problemNumber,
        String title,
        String severity,
        BigDecimal ignorePenalty,
        Integer penaltyWeight,
        String status
) {
}
