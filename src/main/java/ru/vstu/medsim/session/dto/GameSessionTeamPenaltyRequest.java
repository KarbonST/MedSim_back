package ru.vstu.medsim.session.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GameSessionTeamPenaltyRequest(
        @NotNull
        @DecimalMin(value = "0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal budgetPenalty,
        @NotNull
        @Min(0)
        @Max(100)
        Integer timePenalty,
        @Size(max = 300)
        String reason
) {
}
