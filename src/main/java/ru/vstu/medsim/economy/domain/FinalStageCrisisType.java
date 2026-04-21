package ru.vstu.medsim.economy.domain;

import java.math.BigDecimal;

public enum FinalStageCrisisType {
    REPUTATIONAL_PRESSURE(
            "Репутационное давление",
            "На финальном этапе санитарные и заметные пациентам проблемы сильнее влияют на доход команды."
    ),
    INSPECTION_PRESSURE(
            "Внеплановая проверка",
            "На финальном этапе технические и регламентные нарушения сильнее штрафуют команду."
    ),
    PEAK_LOAD(
            "Пиковая нагрузка",
            "На финальном этапе сбои в работе кабинетов сильнее бьют по выручке команды."
    );

    private final String title;
    private final String description;

    FinalStageCrisisType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getReputationPenaltyRate() {
        return this == REPUTATIONAL_PRESSURE
                ? new BigDecimal("0.15")
                : new BigDecimal("0.10");
    }

    public BigDecimal getInspectionFineAmount() {
        return this == INSPECTION_PRESSURE
                ? new BigDecimal("5.00")
                : new BigDecimal("3.00");
    }

    public BigDecimal getOperationsIncomeFactor() {
        return this == PEAK_LOAD
                ? BigDecimal.ZERO.setScale(2)
                : new BigDecimal("0.50");
    }
}
