package ru.vstu.medsim.economy.domain;

public enum ProblemEscalationType {
    REPUTATION_INCIDENT(
            "Репутационный инцидент",
            "Проблему уже замечают пациенты. Если не закрыть её до конца 3 этапа, команда потеряет часть дохода."
    ),
    INSPECTION_RISK(
            "Риск проверки",
            "Проблема может привести к претензии или проверке. Если не закрыть её до конца 3 этапа, команда получит дополнительный штраф."
    ),
    OPERATIONS_DISRUPTION(
            "Срыв работы кабинета",
            "Проблема мешает нормальной работе кабинета. Если не закрыть её до конца 3 этапа, доход кабинета просядет сильнее обычного."
    );

    private final String title;
    private final String description;

    ProblemEscalationType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public FinalStageCrisisType toFinalStageCrisisType() {
        return switch (this) {
            case REPUTATION_INCIDENT -> FinalStageCrisisType.REPUTATIONAL_PRESSURE;
            case INSPECTION_RISK -> FinalStageCrisisType.INSPECTION_PRESSURE;
            case OPERATIONS_DISRUPTION -> FinalStageCrisisType.PEAK_LOAD;
        };
    }

    public String getPenaltyHint(FinalStageCrisisType crisisType) {
        if (crisisType == null) {
            crisisType = toFinalStageCrisisType();
        }

        return switch (this) {
            case REPUTATION_INCIDENT -> "Если задача не будет закрыта к концу этапа, команда потеряет %d%% дохода за этап."
                    .formatted(crisisType.getReputationPenaltyRate().movePointRight(2).intValue());
            case INSPECTION_RISK -> "Если задача не будет закрыта к концу этапа, команда получит штраф %.2f."
                    .formatted(crisisType.getInspectionFineAmount());
            case OPERATIONS_DISRUPTION -> crisisType.getOperationsIncomeFactor().compareTo(java.math.BigDecimal.ZERO) == 0
                    ? "Если задача не будет закрыта к концу этапа, кабинет не принесёт доход в этом этапе."
                    : "Если задача не будет закрыта к концу этапа, доход кабинета упадёт до 50% от обычного.";
        };
    }
}
