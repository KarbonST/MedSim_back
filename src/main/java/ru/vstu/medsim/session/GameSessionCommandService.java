package ru.vstu.medsim.session;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.Player;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.player.repository.GameSessionRepository;
import ru.vstu.medsim.player.repository.PlayerRepository;
import ru.vstu.medsim.player.repository.SessionParticipantRepository;
import ru.vstu.medsim.session.dto.GameSessionSummaryResponse;

import java.util.List;
import java.util.Set;

@Service
public class GameSessionCommandService {

    private final GameSessionQueryService gameSessionQueryService;
    private final GameSessionRepository gameSessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final PlayerRepository playerRepository;

    public GameSessionCommandService(
            GameSessionQueryService gameSessionQueryService,
            GameSessionRepository gameSessionRepository,
            SessionParticipantRepository sessionParticipantRepository,
            PlayerRepository playerRepository
    ) {
        this.gameSessionQueryService = gameSessionQueryService;
        this.gameSessionRepository = gameSessionRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
        this.playerRepository = playerRepository;
    }

    @Transactional
    public GameSessionSummaryResponse startSession(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);

        try {
            session.start();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        GameSession savedSession = gameSessionRepository.save(session);
        return toSummary(savedSession);
    }

    @Transactional
    public GameSessionSummaryResponse finishSession(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);

        try {
            session.finish();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        GameSession savedSession = gameSessionRepository.save(session);
        return toSummary(savedSession);
    }

    @Transactional
    public void deleteSession(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);
        List<SessionParticipant> participants = sessionParticipantRepository
                .findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId());

        Set<Long> playerIds = participants.stream()
                .map(participant -> participant.getPlayer().getId())
                .collect(java.util.stream.Collectors.toSet());

        sessionParticipantRepository.deleteAllByGameSessionId(session.getId());
        gameSessionRepository.delete(session);

        if (playerIds.isEmpty()) {
            return;
        }

        List<Player> players = playerRepository.findAllByIdIn(playerIds);
        List<Player> exclusivePlayers = players.stream()
                .filter(player -> !sessionParticipantRepository.existsByPlayerId(player.getId()))
                .toList();

        if (!exclusivePlayers.isEmpty()) {
            playerRepository.deleteAll(exclusivePlayers);
        }
    }

    private GameSessionSummaryResponse toSummary(GameSession session) {
        return new GameSessionSummaryResponse(
                session.getId(),
                session.getCode(),
                session.getName(),
                session.getStatus().name(),
                sessionParticipantRepository.countByGameSessionId(session.getId())
        );
    }
}
