package ru.vstu.medsim.session.dto;

import java.time.LocalDateTime;

public record SessionRuntimeItem(
        Integer activeStageNumber,
        Integer activeStageDurationMinutes,
        String activeStageInteractionMode,
        String timerStatus,
        Integer remainingSeconds,
        LocalDateTime timerEndsAt
) {
}
