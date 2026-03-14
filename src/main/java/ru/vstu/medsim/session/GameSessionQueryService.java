package ru.vstu.medsim.session;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.player.repository.GameSessionRepository;
import ru.vstu.medsim.player.repository.SessionParticipantRepository;
import ru.vstu.medsim.session.dto.GameSessionParticipantItem;
import ru.vstu.medsim.session.dto.GameSessionParticipantsResponse;
import ru.vstu.medsim.session.dto.GameSessionSummaryResponse;

import java.util.List;

@Service
public class GameSessionQueryService {

    private final GameSessionRepository gameSessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;

    public GameSessionQueryService(
            GameSessionRepository gameSessionRepository,
            SessionParticipantRepository sessionParticipantRepository
    ) {
        this.gameSessionRepository = gameSessionRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
    }

    @Transactional(readOnly = true)
    public List<GameSessionSummaryResponse> getAllSessions() {
        return gameSessionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public GameSessionParticipantsResponse getParticipants(String sessionCode) {
        GameSession session = getSessionOrThrow(sessionCode);

        List<GameSessionParticipantItem> participants = sessionParticipantRepository
                .findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId())
                .stream()
                .map(this::toParticipantItem)
                .toList();

        return new GameSessionParticipantsResponse(
                session.getId(),
                session.getCode(),
                session.getName(),
                session.getStatus().name(),
                participants
        );
    }

    GameSession getSessionOrThrow(String sessionCode) {
        String normalizedCode = normalizeCode(sessionCode);

        return gameSessionRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена."));
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

    private GameSessionParticipantItem toParticipantItem(SessionParticipant participant) {
        return new GameSessionParticipantItem(
                participant.getId(),
                participant.getPlayer().getId(),
                participant.getPlayer().getDisplayName(),
                participant.getPlayer().getHospitalPosition(),
                participant.getGameRole(),
                participant.getJoinedAt()
        );
    }

    String normalizeCode(String sessionCode) {
        return sessionCode.trim().toUpperCase();
    }
}
