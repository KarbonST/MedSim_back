package ru.vstu.medsim.economy.dto;

import java.util.List;

public record GameSessionEconomyResponse(
        Long sessionId,
        String sessionCode,
        String sessionName,
        String sessionStatus,
        SessionEconomySettingsItem settings,
        List<TeamEconomyItem> teams
) {
}
