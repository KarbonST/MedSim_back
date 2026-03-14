package ru.vstu.medsim.session;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.GameSessionStatus;
import ru.vstu.medsim.player.domain.Player;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.player.repository.GameSessionRepository;
import ru.vstu.medsim.player.repository.PlayerRepository;
import ru.vstu.medsim.player.repository.SessionParticipantRepository;
import ru.vstu.medsim.session.domain.SessionStageSetting;
import ru.vstu.medsim.session.dto.GameSessionCreateRequest;
import ru.vstu.medsim.session.dto.GameSessionParticipantItem;
import ru.vstu.medsim.session.dto.GameSessionRenameRequest;
import ru.vstu.medsim.session.dto.GameSessionParticipantsResponse;
import ru.vstu.medsim.session.dto.GameSessionRoleAssignmentRequest;
import ru.vstu.medsim.session.dto.GameSessionStageSettingsRequest;
import ru.vstu.medsim.session.dto.GameSessionSummaryResponse;
import ru.vstu.medsim.session.repository.SessionStageSettingRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class GameSessionCommandService {

    private static final List<String> MANDATORY_LEADERSHIP_ROLES = List.of(
            "Главный врач",
            "Главная медсестра",
            "Главный инженер"
    );
    private static final List<String> EXECUTOR_ROLES = List.of(
            "Сестра поликлинического отделения",
            "Сестра диагностического отделения",
            "Заместитель главного инженера по медтехнике",
            "Заместитель главного инженера по АХЧ"
    );

    private final GameSessionQueryService gameSessionQueryService;
    private final GameSessionRepository gameSessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final PlayerRepository playerRepository;
    private final SessionStageSettingRepository sessionStageSettingRepository;

    public GameSessionCommandService(
            GameSessionQueryService gameSessionQueryService,
            GameSessionRepository gameSessionRepository,
            SessionParticipantRepository sessionParticipantRepository,
            PlayerRepository playerRepository,
            SessionStageSettingRepository sessionStageSettingRepository
    ) {
        this.gameSessionQueryService = gameSessionQueryService;
        this.gameSessionRepository = gameSessionRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
        this.playerRepository = playerRepository;
        this.sessionStageSettingRepository = sessionStageSettingRepository;
    }

    @Transactional
    public GameSessionSummaryResponse createSession(GameSessionCreateRequest request) {
        String sessionCode = normalizeCode(request.sessionCode());
        String sessionName = request.sessionName().trim();

        if (gameSessionRepository.findByCode(sessionCode).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Сессия с таким кодом уже существует."
            );
        }

        GameSession session = gameSessionRepository.save(
                new GameSession(sessionCode, sessionName, GameSessionStatus.LOBBY)
        );

        return toSummary(session);
    }

    @Transactional
    public GameSessionSummaryResponse renameSession(String sessionCode, GameSessionRenameRequest request) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);
        session.rename(request.sessionName().trim());
        return toSummary(gameSessionRepository.save(session));
    }

    @Transactional
    public GameSessionParticipantsResponse saveStageSettings(
            String sessionCode,
            GameSessionStageSettingsRequest request
    ) {
        GameSession session = getLobbySessionOrThrow(sessionCode);
        validateStageSettings(request.stages());

        sessionStageSettingRepository.deleteAllByGameSessionId(session.getId());

        List<SessionStageSetting> stageSettings = request.stages().stream()
                .map(stage -> new SessionStageSetting(
                        session,
                        stage.stageNumber(),
                        stage.durationMinutes(),
                        stage.interactionMode()
                ))
                .toList();

        sessionStageSettingRepository.saveAll(stageSettings);
        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse assignRandomRoles(String sessionCode) {
        GameSession session = getLobbySessionOrThrow(sessionCode);
        List<SessionParticipant> participants = sessionParticipantRepository
                .findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId());

        if (participants.size() < MANDATORY_LEADERSHIP_ROLES.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Для автоматического распределения ролей нужно минимум 3 участника."
            );
        }

        List<SessionParticipant> shuffledParticipants = new ArrayList<>(participants);
        Collections.shuffle(shuffledParticipants, ThreadLocalRandom.current());
        List<Assignment> assignments = new ArrayList<>();

        if (!assignLeadershipRoles(shuffledParticipants, 0, assignments)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Невозможно выполнить распределение без совпадения должностей и ролей."
            );
        }

        for (Assignment assignment : assignments) {
            assignment.participant().assignGameRole(assignment.role());
        }

        Set<Long> leadershipParticipantIds = assignments.stream()
                .map(assignment -> assignment.participant().getId())
                .collect(Collectors.toSet());

        List<SessionParticipant> remainingParticipants = shuffledParticipants.stream()
                .filter(participant -> !leadershipParticipantIds.contains(participant.getId()))
                .toList();

        assignExecutorRoles(remainingParticipants);
        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantItem assignManualRole(
            String sessionCode,
            Long participantId,
            GameSessionRoleAssignmentRequest request
    ) {
        GameSession session = getLobbySessionOrThrow(sessionCode);
        SessionParticipant participant = sessionParticipantRepository
                .findByIdAndGameSessionId(participantId, session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Участник не найден."));

        participant.assignGameRole(request.gameRole().trim());
        return toParticipantItem(participant);
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
                .collect(Collectors.toSet());

        sessionStageSettingRepository.deleteAllByGameSessionId(session.getId());
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

    private boolean assignLeadershipRoles(
            List<SessionParticipant> participants,
            int roleIndex,
            List<Assignment> assignments
    ) {
        if (roleIndex >= MANDATORY_LEADERSHIP_ROLES.size()) {
            return true;
        }

        String role = MANDATORY_LEADERSHIP_ROLES.get(roleIndex);
        List<SessionParticipant> shuffledParticipants = new ArrayList<>(participants);
        Collections.shuffle(shuffledParticipants, ThreadLocalRandom.current());

        for (SessionParticipant participant : shuffledParticipants) {
            boolean alreadyAssigned = assignments.stream()
                    .anyMatch(assignment -> assignment.participant().getId().equals(participant.getId()));
            boolean matchesHospitalPosition = participant.getPlayer().getHospitalPosition().equalsIgnoreCase(role);

            if (alreadyAssigned || matchesHospitalPosition) {
                continue;
            }

            assignments.add(new Assignment(participant, role));
            if (assignLeadershipRoles(participants, roleIndex + 1, assignments)) {
                return true;
            }
            assignments.removeLast();
        }

        return false;
    }

    private void assignExecutorRoles(List<SessionParticipant> participants) {
        for (int participantIndex = 0; participantIndex < participants.size(); participantIndex++) {
            SessionParticipant participant = participants.get(participantIndex);
            participant.assignGameRole(selectExecutorRole(participant, participantIndex));
        }
    }

    private String selectExecutorRole(SessionParticipant participant, int offset) {
        List<String> shuffledRoles = new ArrayList<>(EXECUTOR_ROLES);
        Collections.shuffle(shuffledRoles, ThreadLocalRandom.current());

        for (int index = 0; index < shuffledRoles.size(); index++) {
            String role = shuffledRoles.get((offset + index) % shuffledRoles.size());
            boolean matchesHospitalPosition = participant.getPlayer().getHospitalPosition().equalsIgnoreCase(role);

            if (!matchesHospitalPosition) {
                return role;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Невозможно назначить исполнительскую роль без совпадения с реальной должностью."
        );
    }

    private void validateStageSettings(List<GameSessionStageSettingsRequest.StageItem> stages) {
        List<Integer> stageNumbers = stages.stream()
                .map(GameSessionStageSettingsRequest.StageItem::stageNumber)
                .sorted()
                .toList();

        for (int index = 0; index < stageNumbers.size(); index++) {
            int expectedStageNumber = index + 1;
            if (stageNumbers.get(index) != expectedStageNumber) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Этапы должны идти подряд, начиная с 1."
                );
            }
        }
    }

    private GameSession getLobbySessionOrThrow(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);

        if (session.getStatus() != GameSessionStatus.LOBBY) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Настройки этапов и ролей можно менять только до старта игры."
            );
        }

        return session;
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

    private GameSessionSummaryResponse toSummary(GameSession session) {
        return new GameSessionSummaryResponse(
                session.getId(),
                session.getCode(),
                session.getName(),
                session.getStatus().name(),
                sessionParticipantRepository.countByGameSessionId(session.getId()),
                sessionStageSettingRepository.countByGameSessionId(session.getId())
        );
    }

    private String normalizeCode(String sessionCode) {
        return sessionCode.trim().toUpperCase();
    }

    private record Assignment(SessionParticipant participant, String role) {
    }
}
