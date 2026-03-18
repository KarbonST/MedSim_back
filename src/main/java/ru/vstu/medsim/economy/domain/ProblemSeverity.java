package ru.vstu.medsim.economy.domain;

import java.math.BigDecimal;

public enum ProblemSeverity {
    MINOR(new BigDecimal("0.75"), 1),
    SERIOUS(new BigDecimal("0.40"), 2),
    CRITICAL(new BigDecimal("0.00"), 3);

    private final BigDecimal stateCoefficient;
    private final int penaltyWeight;

    ProblemSeverity(BigDecimal stateCoefficient, int penaltyWeight) {
        this.stateCoefficient = stateCoefficient;
        this.penaltyWeight = penaltyWeight;
    }

    public BigDecimal getStateCoefficient() {
        return stateCoefficient;
    }

    public int getPenaltyWeight() {
        return penaltyWeight;
    }
}
