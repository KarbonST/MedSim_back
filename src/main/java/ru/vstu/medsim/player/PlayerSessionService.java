package ru.vstu.medsim.player;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.GameSessionStatus;
import ru.vstu.medsim.player.domain.Player;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.player.dto.PlayerSessionJoinRequest;
import ru.vstu.medsim.player.dto.PlayerSessionJoinResponse;
import ru.vstu.medsim.player.repository.GameSessionRepository;
import ru.vstu.medsim.player.repository.PlayerRepository;
import ru.vstu.medsim.player.repository.SessionParticipantRepository;

@Service
public class PlayerSessionService {

    private final PlayerRepository playerRepository;
    private final GameSessionRepository gameSessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;

    public PlayerSessionService(
            PlayerRepository playerRepository,
            GameSessionRepository gameSessionRepository,
            SessionParticipantRepository sessionParticipantRepository
    ) {
        this.playerRepository = playerRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
    }

    @Transactional
    public PlayerSessionJoinResponse join(PlayerSessionJoinRequest request) {
        String displayName = request.displayName().trim();
        String hospitalPosition = request.hospitalPosition().trim();
        String sessionCode = normalizeCode(request.sessionCode());

        GameSession session = gameSessionRepository.findByCode(sessionCode)
                .orElseGet(() -> gameSessionRepository.save(
                        new GameSession(sessionCode, "Сессия " + sessionCode, GameSessionStatus.LOBBY)
                ));

        Player player = playerRepository.findByDisplayNameIgnoreCaseAndHospitalPositionIgnoreCase(displayName, hospitalPosition)
                .orElseGet(() -> playerRepository.save(new Player(displayName, hospitalPosition)));

        SessionParticipant participant = sessionParticipantRepository.findByGameSessionIdAndPlayerId(session.getId(), player.getId())
                .orElseGet(() -> sessionParticipantRepository.save(new SessionParticipant(session, player)));

        return new PlayerSessionJoinResponse(
                participant.getId(),
                player.getId(),
                session.getId(),
                session.getCode(),
                session.getName(),
                session.getStatus().name(),
                player.getDisplayName(),
                player.getHospitalPosition(),
                participant.getGameRole(),
                participant.getJoinedAt()
        );
    }

    private String normalizeCode(String sessionCode) {
        return sessionCode.trim().toUpperCase();
    }
}
