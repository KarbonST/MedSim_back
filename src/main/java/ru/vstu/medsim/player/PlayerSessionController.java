package ru.vstu.medsim.player;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vstu.medsim.player.dto.AvailablePlayerSessionResponse;
import ru.vstu.medsim.player.dto.PlayerKanbanCardStatusUpdateRequest;
import ru.vstu.medsim.player.dto.PlayerKanbanSolutionSelectionRequest;
import ru.vstu.medsim.player.dto.PlayerSessionJoinRequest;
import ru.vstu.medsim.player.dto.PlayerSessionJoinResponse;
import ru.vstu.medsim.player.dto.PlayerTeamWorkspaceResponse;

import java.util.List;

@RestController
@RequestMapping("/api/player-sessions")
public class PlayerSessionController {

    private final PlayerSessionService playerSessionService;

    public PlayerSessionController(PlayerSessionService playerSessionService) {
        this.playerSessionService = playerSessionService;
    }

    @GetMapping("/available")
    public List<AvailablePlayerSessionResponse> getAvailableSessions() {
        return playerSessionService.getAvailableSessions();
    }

    @PostMapping("/join")
    public PlayerSessionJoinResponse join(@Valid @RequestBody PlayerSessionJoinRequest request) {
        return playerSessionService.join(request);
    }

    @GetMapping("/{sessionCode}/participants/{participantId}/workspace")
    public PlayerTeamWorkspaceResponse getWorkspace(
            @PathVariable String sessionCode,
            @PathVariable Long participantId
    ) {
        return playerSessionService.getWorkspace(sessionCode, participantId);
    }

    @PatchMapping("/{sessionCode}/participants/{participantId}/kanban/cards/{cardId}/status")
    public PlayerTeamWorkspaceResponse updateKanbanCardStatus(
            @PathVariable String sessionCode,
            @PathVariable Long participantId,
            @PathVariable Long cardId,
            @Valid @RequestBody PlayerKanbanCardStatusUpdateRequest request
    ) {
        return playerSessionService.updateKanbanCardStatus(sessionCode, participantId, cardId, request);
    }

    @PatchMapping("/{sessionCode}/participants/{participantId}/kanban/cards/{cardId}/solution")
    public PlayerTeamWorkspaceResponse selectKanbanCardSolution(
            @PathVariable String sessionCode,
            @PathVariable Long participantId,
            @PathVariable Long cardId,
            @Valid @RequestBody PlayerKanbanSolutionSelectionRequest request
    ) {
        return playerSessionService.selectKanbanCardSolution(sessionCode, participantId, cardId, request);
    }
}
