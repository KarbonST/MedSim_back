package ru.vstu.medsim.session.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SessionEconomySettingsUpdateRequest(
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 8, fraction = 2)
        BigDecimal startingBudget,
        @NotNull
        @Min(1)
        @Max(100)
        Integer stageTimeUnits
) {
}
