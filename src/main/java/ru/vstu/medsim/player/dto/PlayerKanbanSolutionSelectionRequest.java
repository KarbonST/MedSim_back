package ru.vstu.medsim.player.dto;

import jakarta.validation.constraints.NotNull;

public record PlayerKanbanSolutionSelectionRequest(
        @NotNull Long solutionOptionId
) {
}
