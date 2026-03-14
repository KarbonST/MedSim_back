package ru.vstu.medsim.player;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vstu.medsim.player.dto.AvailablePlayerSessionResponse;
import ru.vstu.medsim.player.dto.PlayerSessionJoinRequest;
import ru.vstu.medsim.player.dto.PlayerSessionJoinResponse;

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
}
