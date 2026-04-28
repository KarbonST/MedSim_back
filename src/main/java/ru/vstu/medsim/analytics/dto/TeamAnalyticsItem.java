package ru.vstu.medsim.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public record TeamAnalyticsItem(
        Long teamId,
        String teamName,
        Integer rank,
        Integer participantCount,
        Integer totalProblemCount,
        Integer resolvedProblemCount,
        Integer unresolvedProblemCount,
        Integer returnCount,
        Integer holdCount,
        Integer escalatedProblemCount,
        Integer activeEscalationCount,
        BigDecimal currentBalance,
        BigDecimal availableBalance,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal totalPenalties,
        BigDecimal totalBonuses,
        Long avgDistributionSeconds,
        Long avgReactionSeconds,
        Long avgWorkSeconds,
        Long avgDepartmentReviewSeconds,
        Long avgChiefReviewSeconds,
        Long avgFullCycleSeconds,
        String bottleneckLabel,
        List<TeamAnalyticsStageItem> stages,
        List<TeamParticipantAnalyticsItem> participants,
        List<TeamAnalyticsCardItem> cards
) {
}
