package ru.vstu.medsim.economy.dto;

import java.math.BigDecimal;

public record SessionEconomySettingsItem(
        BigDecimal startingBudget,
        Integer stageTimeUnits
) {
}
