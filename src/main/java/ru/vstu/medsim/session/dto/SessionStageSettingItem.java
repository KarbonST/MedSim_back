package ru.vstu.medsim.session.dto;

public record SessionStageSettingItem(
        Integer stageNumber,
        Integer durationMinutes,
        String interactionMode
) {
}
