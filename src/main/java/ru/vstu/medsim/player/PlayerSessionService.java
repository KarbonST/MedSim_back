package ru.vstu.medsim.player;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.GameSessionStatus;
import ru.vstu.medsim.player.domain.Player;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.player.dto.AvailablePlayerSessionResponse;
import ru.vstu.medsim.player.dto.PlayerSessionJoinRequest;
import ru.vstu.medsim.player.dto.PlayerSessionJoinResponse;
import ru.vstu.medsim.player.dto.PlayerTeamWorkspaceMemberResponse;
import ru.vstu.medsim.player.dto.PlayerTeamWorkspaceResponse;
import ru.vstu.medsim.player.repository.GameSessionRepository;
import ru.vstu.medsim.player.repository.PlayerRepository;
import ru.vstu.medsim.player.repository.SessionParticipantRepository;
import ru.vstu.medsim.session.SessionRuntimeSnapshotService;
import ru.vstu.medsim.session.dto.SessionStageSettingItem;
import ru.vstu.medsim.session.repository.SessionStageSettingRepository;

import java.util.List;

@Service
public class PlayerSessionService {

    private final PlayerRepository playerRepository;
    private final GameSessionRepository gameSessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final SessionStageSettingRepository sessionStageSettingRepository;
    private final SessionRuntimeSnapshotService sessionRuntimeSnapshotService;

    public PlayerSessionService(
            PlayerRepository playerRepository,
            GameSessionRepository gameSessionRepository,
            SessionParticipantRepository sessionParticipantRepository,
            SessionStageSettingRepository sessionStageSettingRepository,
            SessionRuntimeSnapshotService sessionRuntimeSnapshotService
    ) {
        this.playerRepository = playerRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
        this.sessionStageSettingRepository = sessionStageSettingRepository;
        this.sessionRuntimeSnapshotService = sessionRuntimeSnapshotService;
    }

    @Transactional(readOnly = true)
    public List<AvailablePlayerSessionResponse> getAvailableSessions() {
        return gameSessionRepository.findAllByStatusOrderByCreatedAtDescIdDesc(GameSessionStatus.LOBBY)
                .stream()
                .map(session -> new AvailablePlayerSessionResponse(
                        session.getId(),
                        session.getCode(),
                        session.getName(),
                        sessionParticipantRepository.countByGameSessionId(session.getId())
                ))
                .toList();
    }

    @Transactional
    public PlayerSessionJoinResponse join(PlayerSessionJoinRequest request) {
        String displayName = request.displayName().trim();
        String hospitalPosition = request.hospitalPosition().trim();
        String sessionCode = normalizeCode(request.sessionCode());

        GameSession session = gameSessionRepository.findByCode(sessionCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Сессия с таким кодом не найдена. Выберите существующую сессию."
                ));

        if (session.getStatus() != GameSessionStatus.LOBBY) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Подключение возможно только к сессии в статусе ожидания."
            );
        }

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

    @Transactional(readOnly = true)
    public PlayerTeamWorkspaceResponse getWorkspace(String sessionCode, Long participantId) {
        String normalizedCode = normalizeCode(sessionCode);

        GameSession session = gameSessionRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Сессия с таким кодом не найдена."
                ));

        SessionParticipant participant = sessionParticipantRepository.findByIdAndGameSessionId(participantId, session.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Участник не найден в указанной сессии."
                ));

        List<PlayerTeamWorkspaceMemberResponse> teammates = participant.getTeam() == null
                ? List.of()
                : sessionParticipantRepository.findAllByGameSessionIdAndTeamIdOrderByJoinedAtAscIdAsc(
                                session.getId(),
                                participant.getTeam().getId()
                        ).stream()
                        .map(teammate -> new PlayerTeamWorkspaceMemberResponse(
                                teammate.getId(),
                                teammate.getPlayer().getDisplayName(),
                                teammate.getPlayer().getHospitalPosition(),
                                teammate.getGameRole(),
                                teammate.getId().equals(participant.getId())
                        ))
                        .toList();

        List<ru.vstu.medsim.session.domain.SessionStageSetting> stageEntities = sessionStageSettingRepository
                .findAllByGameSessionIdOrderByStageNumberAsc(session.getId());

        List<SessionStageSettingItem> stages = stageEntities.stream()
                .map(stage -> new SessionStageSettingItem(
                        stage.getStageNumber(),
                        stage.getDurationMinutes(),
                        stage.getInteractionMode().name()
                ))
                .toList();

        return new PlayerTeamWorkspaceResponse(
                participant.getId(),
                participant.getPlayer().getId(),
                session.getId(),
                session.getCode(),
                session.getName(),
                session.getStatus().name(),
                participant.getPlayer().getDisplayName(),
                participant.getPlayer().getHospitalPosition(),
                participant.getGameRole(),
                participant.getTeam() != null ? participant.getTeam().getId() : null,
                participant.getTeam() != null ? participant.getTeam().getName() : null,
                teammates,
                stages,
                sessionRuntimeSnapshotService.buildRuntime(session, stageEntities)
        );
    }

    private String normalizeCode(String sessionCode) {
        return sessionCode.trim().toUpperCase();
    }
}
