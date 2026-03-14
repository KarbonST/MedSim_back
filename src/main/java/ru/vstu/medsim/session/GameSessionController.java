package ru.vstu.medsim.session;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vstu.medsim.session.dto.GameSessionParticipantsResponse;
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

    @GetMapping("/{sessionCode}/participants")
    public GameSessionParticipantsResponse getParticipants(@PathVariable String sessionCode) {
        return gameSessionQueryService.getParticipants(sessionCode);
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
