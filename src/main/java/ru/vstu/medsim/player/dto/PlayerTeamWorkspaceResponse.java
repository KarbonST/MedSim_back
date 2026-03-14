package ru.vstu.medsim.player.dto;

import ru.vstu.medsim.session.dto.SessionStageSettingItem;

import java.util.List;

public record PlayerTeamWorkspaceResponse(
        Long participantId,
        Long playerId,
        Long sessionId,
        String sessionCode,
        String sessionName,
        String sessionStatus,
        String displayName,
        String hospitalPosition,
        String gameRole,
        Long teamId,
        String teamName,
        List<PlayerTeamWorkspaceMemberResponse> teammates,
        List<SessionStageSettingItem> stages
) {
}
