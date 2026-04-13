package ru.vstu.medsim.economy.dto;

import java.math.BigDecimal;
import java.util.List;

public record TeamEconomyItem(
        Long teamId,
        String teamName,
        BigDecimal currentBalance,
        Integer currentStageTimeUnits,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal totalPenalties,
        BigDecimal totalBonuses,
        List<TeamRoomEconomyItem> rooms,
        List<TeamEconomyEventItem> recentEvents
) {
}
