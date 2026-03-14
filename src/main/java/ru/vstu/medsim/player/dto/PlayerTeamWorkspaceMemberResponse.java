package ru.vstu.medsim.player.dto;

public record PlayerTeamWorkspaceMemberResponse(
        Long participantId,
        String displayName,
        String hospitalPosition,
        String gameRole,
        boolean currentParticipant
) {
}
