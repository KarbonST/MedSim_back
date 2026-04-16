package ru.vstu.medsim.economy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TeamStageEconomySummaryItem(
        Integer stageNumber,
        BigDecimal netAmount,
        String message,
        LocalDateTime settledAt
) {
}
