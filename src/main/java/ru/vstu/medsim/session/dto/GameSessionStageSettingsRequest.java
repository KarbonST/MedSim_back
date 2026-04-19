package ru.vstu.medsim.session.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ru.vstu.medsim.session.domain.StageInteractionMode;

import java.util.List;

public record GameSessionStageSettingsRequest(
        @NotEmpty List<@Valid StageItem> stages
) {
    public record StageItem(
            @NotNull @Min(1) Integer stageNumber,
            @NotNull @Min(1) Integer durationMinutes,
            @NotNull StageInteractionMode interactionMode,
            @Min(0) Integer problemCount
    ) {
    }
}
