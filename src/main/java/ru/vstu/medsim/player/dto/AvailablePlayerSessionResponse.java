package ru.vstu.medsim.player.dto;

public record AvailablePlayerSessionResponse(
        Long sessionId,
        String sessionCode,
        String sessionName,
        long participantCount
) {
}
