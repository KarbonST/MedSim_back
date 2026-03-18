package ru.vstu.medsim.economy.dto;

import java.math.BigDecimal;
import java.util.List;

public record TeamRoomEconomyItem(
        Long roomStateId,
        String roomCode,
        String roomName,
        BigDecimal baseIncome,
        int activeProblemCount,
        String worstProblemSeverity,
        BigDecimal stateCoefficient,
        List<TeamProblemEconomyItem> problems
) {
}
