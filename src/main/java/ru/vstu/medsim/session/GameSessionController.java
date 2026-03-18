package ru.vstu.medsim.session;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vstu.medsim.economy.SessionEconomyService;
import ru.vstu.medsim.economy.dto.GameSessionEconomyResponse;
import ru.vstu.medsim.session.dto.GameSessionCreateRequest;
import ru.vstu.medsim.session.dto.GameSessionParticipantItem;
import ru.vstu.medsim.session.dto.GameSessionParticipantsResponse;
import ru.vstu.medsim.session.dto.GameSessionRenameRequest;
import ru.vstu.medsim.session.dto.GameSessionRoleAssignmentRequest;
import ru.vstu.medsim.session.dto.GameSessionStageSettingsRequest;
import ru.vstu.medsim.session.dto.GameSessionSummaryResponse;
import ru.vstu.medsim.session.dto.GameSessionTeamAssignmentRequest;
import ru.vstu.medsim.session.dto.GameSessionTeamRenameRequest;
import ru.vstu.medsim.session.dto.SessionEconomySettingsUpdateRequest;
import ru.vstu.medsim.session.dto.SessionRuntimeStageRequest;

import java.util.List;

@RestController
@RequestMapping("/api/game-sessions")
public class GameSessionController {

    private final GameSessionQueryService gameSessionQueryService;
    private final GameSessionCommandService gameSessionCommandService;
    private final SessionEconomyService sessionEconomyService;

    public GameSessionController(
            GameSessionQueryService gameSessionQueryService,
            GameSessionCommandService gameSessionCommandService,
            SessionEconomyService sessionEconomyService
    ) {
        this.gameSessionQueryService = gameSessionQueryService;
        this.gameSessionCommandService = gameSessionCommandService;
        this.sessionEconomyService = sessionEconomyService;
    }

    @GetMapping
    public List<GameSessionSummaryResponse> getSessions() {
        return gameSessionQueryService.getAllSessions();
    }

    @PostMapping
    public GameSessionSummaryResponse createSession(@Valid @RequestBody GameSessionCreateRequest request) {
        return gameSessionCommandService.createSession(request);
    }

    @GetMapping("/{sessionCode}/participants")
    public GameSessionParticipantsResponse getParticipants(@PathVariable String sessionCode) {
        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @GetMapping("/{sessionCode}/economy")
    public GameSessionEconomyResponse getEconomy(@PathVariable String sessionCode) {
        return sessionEconomyService.getEconomyOverview(sessionCode);
    }

    @PutMapping("/{sessionCode}/economy/settings")
    public GameSessionEconomyResponse updateEconomySettings(
            @PathVariable String sessionCode,
            @Valid @RequestBody SessionEconomySettingsUpdateRequest request
    ) {
        return sessionEconomyService.updateEconomySettings(sessionCode, request);
    }

    @PatchMapping("/{sessionCode}/name")
    public GameSessionSummaryResponse renameSession(
            @PathVariable String sessionCode,
            @Valid @RequestBody GameSessionRenameRequest request
    ) {
        return gameSessionCommandService.renameSession(sessionCode, request);
    }

    @PatchMapping("/{sessionCode}/teams/{teamId}/name")
    public GameSessionParticipantsResponse renameTeam(
            @PathVariable String sessionCode,
            @PathVariable Long teamId,
            @Valid @RequestBody GameSessionTeamRenameRequest request
    ) {
        return gameSessionCommandService.renameTeam(sessionCode, teamId, request);
    }

    @PostMapping("/{sessionCode}/teams/auto-assign")
    public GameSessionParticipantsResponse autoAssignTeams(@PathVariable String sessionCode) {
        return gameSessionCommandService.autoAssignTeams(sessionCode);
    }

    @PatchMapping("/{sessionCode}/participants/{participantId}/team")
    public GameSessionParticipantsResponse assignParticipantTeam(
            @PathVariable String sessionCode,
            @PathVariable Long participantId,
            @Valid @RequestBody GameSessionTeamAssignmentRequest request
    ) {
        return gameSessionCommandService.assignParticipantTeam(sessionCode, participantId, request);
    }

    @PutMapping("/{sessionCode}/stages")
    public GameSessionParticipantsResponse saveStageSettings(
            @PathVariable String sessionCode,
            @Valid @RequestBody GameSessionStageSettingsRequest request
    ) {
        return gameSessionCommandService.saveStageSettings(sessionCode, request);
    }

    @PostMapping("/{sessionCode}/roles/random")
    public GameSessionParticipantsResponse assignRandomRoles(@PathVariable String sessionCode) {
        return gameSessionCommandService.assignRandomRoles(sessionCode);
    }

    @PatchMapping("/{sessionCode}/participants/{participantId}/role")
    public GameSessionParticipantItem assignManualRole(
            @PathVariable String sessionCode,
            @PathVariable Long participantId,
            @Valid @RequestBody GameSessionRoleAssignmentRequest request
    ) {
        return gameSessionCommandService.assignManualRole(sessionCode, participantId, request);
    }

    @PatchMapping("/{sessionCode}/runtime/stage")
    public GameSessionParticipantsResponse selectRuntimeStage(
            @PathVariable String sessionCode,
            @Valid @RequestBody SessionRuntimeStageRequest request
    ) {
        return gameSessionCommandService.selectRuntimeStage(sessionCode, request);
    }

    @PatchMapping("/{sessionCode}/runtime/timer/start")
    public GameSessionParticipantsResponse startRuntimeTimer(@PathVariable String sessionCode) {
        return gameSessionCommandService.startRuntimeTimer(sessionCode);
    }

    @PatchMapping("/{sessionCode}/runtime/timer/pause")
    public GameSessionParticipantsResponse pauseRuntimeTimer(@PathVariable String sessionCode) {
        return gameSessionCommandService.pauseRuntimeTimer(sessionCode);
    }

    @PatchMapping("/{sessionCode}/runtime/timer/reset")
    public GameSessionParticipantsResponse resetRuntimeTimer(@PathVariable String sessionCode) {
        return gameSessionCommandService.resetRuntimeTimer(sessionCode);
    }

    @PatchMapping("/{sessionCode}/start")
    public GameSessionParticipantsResponse startSession(@PathVariable String sessionCode) {
        return gameSessionCommandService.startSession(sessionCode);
    }

    @PatchMapping("/{sessionCode}/pause")
    public GameSessionParticipantsResponse pauseSession(@PathVariable String sessionCode) {
        return gameSessionCommandService.pauseSession(sessionCode);
    }

    @PatchMapping("/{sessionCode}/finish")
    public GameSessionParticipantsResponse finishSession(@PathVariable String sessionCode) {
        return gameSessionCommandService.finishSession(sessionCode);
    }

    @DeleteMapping("/{sessionCode}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionCode) {
        gameSessionCommandService.deleteSession(sessionCode);
        return ResponseEntity.noContent().build();
    }
}
