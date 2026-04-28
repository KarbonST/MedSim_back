package ru.vstu.medsim.analytics.dto;

import java.math.BigDecimal;

public record TeamAnalyticsStageItem(
        Integer stageNumber,
        Integer durationMinutes,
        String interactionMode,
        Integer totalProblemCount,
        Integer resolvedProblemCount,
        Integer unresolvedProblemCount,
        Integer returnCount,
        Integer holdCount,
        Integer escalatedProblemCount,
        Integer activeEscalationCount,
        BigDecimal netAmount
) {
}
