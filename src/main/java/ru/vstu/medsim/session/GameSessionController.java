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
import ru.vstu.medsim.session.dto.GameSessionCreateRequest;
import ru.vstu.medsim.session.dto.GameSessionParticipantItem;
import ru.vstu.medsim.session.dto.GameSessionRenameRequest;
import ru.vstu.medsim.session.dto.GameSessionParticipantsResponse;
import ru.vstu.medsim.session.dto.GameSessionRoleAssignmentRequest;
import ru.vstu.medsim.session.dto.GameSessionStageSettingsRequest;
import ru.vstu.medsim.session.dto.GameSessionSummaryResponse;

import java.util.List;

@RestController
@RequestMapping("/api/game-sessions")
public class GameSessionController {

    private final GameSessionQueryService gameSessionQueryService;
    private final GameSessionCommandService gameSessionCommandService;

    public GameSessionController(
            GameSessionQueryService gameSessionQueryService,
            GameSessionCommandService gameSessionCommandService
    ) {
        this.gameSessionQueryService = gameSessionQueryService;
        this.gameSessionCommandService = gameSessionCommandService;
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

    @PatchMapping("/{sessionCode}/name")
    public GameSessionSummaryResponse renameSession(
            @PathVariable String sessionCode,
            @Valid @RequestBody GameSessionRenameRequest request
    ) {
        return gameSessionCommandService.renameSession(sessionCode, request);
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

    @PatchMapping("/{sessionCode}/start")
    public GameSessionSummaryResponse startSession(@PathVariable String sessionCode) {
        return gameSessionCommandService.startSession(sessionCode);
    }

    @PatchMapping("/{sessionCode}/finish")
    public GameSessionSummaryResponse finishSession(@PathVariable String sessionCode) {
        return gameSessionCommandService.finishSession(sessionCode);
    }

    @DeleteMapping("/{sessionCode}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionCode) {
        gameSessionCommandService.deleteSession(sessionCode);
        return ResponseEntity.noContent().build();
    }
}
