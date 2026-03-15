package ru.vstu.medsim.session.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SessionRuntimeStageRequest(
        @NotNull(message = "Номер этапа обязателен.")
        @Min(value = 1, message = "Номер этапа должен быть положительным.")
        Integer stageNumber
) {
}
