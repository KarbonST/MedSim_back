package ru.vstu.medsim.session.dto;

public record SessionTeamItem(
        Long teamId,
        String teamName,
        int memberCount,
        int sortOrder
) {
}
