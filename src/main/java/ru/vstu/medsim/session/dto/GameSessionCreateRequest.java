package ru.vstu.medsim.session.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GameSessionCreateRequest(
        @NotBlank
        @Size(max = 150)
        String sessionName,
        @Min(2)
        @Max(12)
        int teamCount,
        @DecimalMin(value = "0.01")
        @Digits(integer = 8, fraction = 2)
        BigDecimal startingBudget,
        @Min(1)
        @Max(100)
        Integer stageTimeUnits
) {
}
