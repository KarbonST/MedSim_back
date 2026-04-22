package ru.vstu.medsim.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.economy.SessionEconomyService;
import ru.vstu.medsim.economy.domain.TeamProblemState;
import ru.vstu.medsim.kanban.KanbanService;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.GameSessionStatus;
import ru.vstu.medsim.player.domain.Player;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.player.repository.GameSessionRepository;
import ru.vstu.medsim.player.repository.PlayerRepository;
import ru.vstu.medsim.player.repository.SessionParticipantRepository;
import ru.vstu.medsim.session.domain.SessionStageSetting;
import ru.vstu.medsim.session.domain.StageInteractionMode;
import ru.vstu.medsim.session.domain.SessionTeam;
import ru.vstu.medsim.session.domain.TeamInventoryItem;
import ru.vstu.medsim.session.dto.GameSessionCreateRequest;
import ru.vstu.medsim.session.dto.GameSessionInventorySettingsRequest;
import ru.vstu.medsim.session.dto.GameSessionParticipantItem;
import ru.vstu.medsim.session.dto.GameSessionParticipantsResponse;
import ru.vstu.medsim.session.dto.GameSessionRenameRequest;
import ru.vstu.medsim.session.dto.GameSessionRoleAssignmentRequest;
import ru.vstu.medsim.session.dto.GameSessionStageSettingsRequest;
import ru.vstu.medsim.session.dto.GameSessionSummaryResponse;
import ru.vstu.medsim.session.dto.SessionRuntimeStageRequest;
import ru.vstu.medsim.session.dto.GameSessionTeamAssignmentRequest;
import ru.vstu.medsim.session.dto.GameSessionTeamRenameRequest;
import ru.vstu.medsim.session.repository.SessionStageSettingRepository;
import ru.vstu.medsim.session.repository.SessionTeamRepository;
import ru.vstu.medsim.session.repository.TeamInventoryItemRepository;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class GameSessionCommandService {

    private static final Logger log = LoggerFactory.getLogger(GameSessionCommandService.class);

    private static final String SESSION_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int SESSION_CODE_PREFIX_LENGTH = 4;
    private static final int SESSION_CODE_MAX_ATTEMPTS = 200;
    private static final SecureRandom SESSION_CODE_RANDOM = new SecureRandom();
    private static final int DEFAULT_STAGE_COUNT = 3;
    private static final int DEFAULT_STAGE_DURATION_MINUTES = 15;

    private static final List<String> TEAM_NAME_ADJECTIVES = List.of(
            "Бодрые",
            "Лихие",
            "Срочные",
            "Ночные",
            "Шустрые",
            "Отважные",
            "Невозмутимые",
            "Улыбчивые",
            "Боевые",
            "Дежурные",
            "Летучие",
            "Хитрые"
    );
    private static final List<String> TEAM_NAME_NOUNS = List.of(
            "Капельницы",
            "Бинты",
            "Термометры",
            "Шприцы",
            "Таблетки",
            "Каталки",
            "Пипетки",
            "Ампулы",
            "Компрессы",
            "Градусники",
            "Халаты",
            "Пластыри"
    );

    private final GameSessionQueryService gameSessionQueryService;
    private final GameSessionRepository gameSessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final PlayerRepository playerRepository;
    private final SessionStageSettingRepository sessionStageSettingRepository;
    private final SessionTeamRepository sessionTeamRepository;
    private final TeamInventoryItemRepository teamInventoryItemRepository;
    private final TeamInventoryCatalog teamInventoryCatalog;
    private final SessionEconomyService sessionEconomyService;
    private final KanbanService kanbanService;

    public GameSessionCommandService(
            GameSessionQueryService gameSessionQueryService,
            GameSessionRepository gameSessionRepository,
            SessionParticipantRepository sessionParticipantRepository,
            PlayerRepository playerRepository,
            SessionStageSettingRepository sessionStageSettingRepository,
            SessionTeamRepository sessionTeamRepository,
            TeamInventoryItemRepository teamInventoryItemRepository,
            TeamInventoryCatalog teamInventoryCatalog,
            SessionEconomyService sessionEconomyService,
            KanbanService kanbanService
    ) {
        this.gameSessionQueryService = gameSessionQueryService;
        this.gameSessionRepository = gameSessionRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
        this.playerRepository = playerRepository;
        this.sessionStageSettingRepository = sessionStageSettingRepository;
        this.sessionTeamRepository = sessionTeamRepository;
        this.teamInventoryItemRepository = teamInventoryItemRepository;
        this.teamInventoryCatalog = teamInventoryCatalog;
        this.sessionEconomyService = sessionEconomyService;
        this.kanbanService = kanbanService;
    }

    @Transactional
    public GameSessionSummaryResponse createSession(GameSessionCreateRequest request) {
        String sessionName = request.sessionName().trim();
        String sessionCode = generateUniqueSessionCode();

        GameSession session = gameSessionRepository.save(
                new GameSession(sessionCode, sessionName, GameSessionStatus.LOBBY)
        );

        List<SessionTeam> teams = sessionTeamRepository.saveAll(
                generateFunnyTeamNames(request.teamCount()).stream()
                        .map(name -> new SessionTeam(session, name.name(), name.sortOrder()))
                        .toList()
        );
        initializeTeamInventory(teams);
        List<TeamProblemState> problemStates = sessionEconomyService.initializeForSession(
                session,
                teams,
                request.startingBudget(),
                request.stageTimeUnits()
        );
        kanbanService.initializeCardsForProblemStates(problemStates);
        List<SessionStageSetting> defaultStageSettings = sessionStageSettingRepository.saveAll(
                createDefaultStageSettings(session)
        );
        sessionEconomyService.redistributeProblemsForSession(
                session.getId(),
                sessionEconomyService.buildEvenProblemDistribution(DEFAULT_STAGE_COUNT)
        );
        SessionStageSetting firstStage = defaultStageSettings.get(0);
        session.initializeStageRuntime(firstStage.getStageNumber(), firstStage.getDurationMinutes());
        gameSessionRepository.save(session);
        log.info(
                "Game session created: sessionCode={}, sessionName={}, teamCount={}, stageCount={}, startingBudget={}, stageTimeUnits={}",
                sessionCode,
                sessionName,
                teams.size(),
                defaultStageSettings.size(),
                request.startingBudget(),
                request.stageTimeUnits()
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
    public GameSessionParticipantsResponse renameTeam(
            String sessionCode,
            Long teamId,
            GameSessionTeamRenameRequest request
    ) {
        GameSession session = getLobbySessionOrThrow(sessionCode);
        SessionTeam team = sessionTeamRepository.findByIdAndGameSessionId(teamId, session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Команда не найдена."));

        team.rename(request.teamName().trim());
        sessionTeamRepository.save(team);
        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse autoAssignTeams(String sessionCode) {
        GameSession session = getLobbySessionOrThrow(sessionCode);
        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());
        List<SessionParticipant> participants = sessionParticipantRepository
                .findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId());

        if (teams.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "В сессии нет доступных команд.");
        }

        clearRolesForParticipants(participants);

        Map<Long, Integer> teamMemberCounts = new HashMap<>();
        teams.forEach(team -> teamMemberCounts.put(team.getId(), 0));

        for (SessionParticipant participant : participants) {
            SessionTeam targetTeam = teams.stream()
                    .min(Comparator
                            .comparingInt((SessionTeam team) -> teamMemberCounts.getOrDefault(team.getId(), 0))
                            .thenComparingInt(SessionTeam::getSortOrder))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "В сессии нет доступных команд."));

            participant.assignTeam(targetTeam);
            teamMemberCounts.computeIfPresent(targetTeam.getId(), (teamId, count) -> count + 1);
        }

        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse assignParticipantTeam(
            String sessionCode,
            Long participantId,
            GameSessionTeamAssignmentRequest request
    ) {
        GameSession session = getLobbySessionOrThrow(sessionCode);
        SessionParticipant participant = sessionParticipantRepository
                .findByIdAndGameSessionId(participantId, session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Участник не найден."));
        if (request.teamId() == null) {
            boolean teamChanged = participant.getTeam() != null;
            participant.assignTeam(null);

            if (teamChanged) {
                clearRolesForParticipants(sessionParticipantRepository.findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId()));
            }

            return gameSessionQueryService.getParticipants(sessionCode);
        }

        SessionTeam team = sessionTeamRepository.findByIdAndGameSessionId(request.teamId(), session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Команда не найдена."));

        boolean teamChanged = participant.getTeam() == null || !participant.getTeam().getId().equals(team.getId());
        participant.assignTeam(team);

        if (teamChanged) {
            clearRolesForParticipants(sessionParticipantRepository.findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId()));
        }

        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse saveStageSettings(
            String sessionCode,
            GameSessionStageSettingsRequest request
    ) {
        GameSession session = getLobbySessionOrThrow(sessionCode);
        validateStageSettings(request.stages());
        List<GameSessionStageSettingsRequest.StageItem> orderedStages = request.stages().stream()
                .sorted(Comparator.comparing(GameSessionStageSettingsRequest.StageItem::stageNumber))
                .toList();
        List<Integer> problemDistribution = resolveProblemDistribution(orderedStages);

        sessionStageSettingRepository.deleteAllByGameSessionId(session.getId());
        sessionStageSettingRepository.flush();

        List<SessionStageSetting> stageSettings = orderedStages.stream()
                .map(stage -> new SessionStageSetting(
                        session,
                        stage.stageNumber(),
                        stage.durationMinutes(),
                        stage.interactionMode()
                ))
                .toList();

        sessionStageSettingRepository.saveAll(stageSettings);
        sessionEconomyService.redistributeProblemsForSession(session.getId(), problemDistribution);

        SessionStageSetting firstStage = stageSettings.get(0);
        session.initializeStageRuntime(firstStage.getStageNumber(), firstStage.getDurationMinutes());
        gameSessionRepository.save(session);
        sessionEconomyService.resetStageTimeForSession(session.getId());
        log.info(
                "Stage settings saved: sessionCode={}, stageNumbers={}, problemDistribution={}",
                session.getCode(),
                stageSettings.stream().map(SessionStageSetting::getStageNumber).toList(),
                problemDistribution
        );

        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse updateInventorySettings(
            String sessionCode,
            GameSessionInventorySettingsRequest request
    ) {
        GameSession session = getLobbySessionOrThrow(sessionCode);
        List<TeamInventoryCatalog.InventorySeed> inventory = normalizeInventoryItems(request.items());

        replaceTeamInventory(session, inventory);

        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse randomizeInventory(String sessionCode) {
        GameSession session = getLobbySessionOrThrow(sessionCode);

        replaceTeamInventory(session, teamInventoryCatalog.generateInitialInventory());

        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse assignRandomRoles(String sessionCode) {
        GameSession session = getLobbySessionOrThrow(sessionCode);
        List<SessionParticipant> participants = sessionParticipantRepository
                .findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId())
                .stream()
                .filter(participant -> participant.getTeam() != null)
                .toList();

        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());

        if (participants.size() < GameRoleCatalog.MANDATORY_LEADERSHIP_ROLES.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Для автоматического распределения ролей нужно минимум 3 участника, уже распределённых по командам."
            );
        }

        for (SessionTeam team : teams) {
            List<SessionParticipant> teamParticipants = participants.stream()
                    .filter(participant -> participant.getTeam() != null && participant.getTeam().getId().equals(team.getId()))
                    .toList();

            validateTeamHasRequiredLeadershipCapacity(team, teamParticipants);
            assignRolesForTeam(team, teamParticipants);
        }

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

        if (participant.getTeam() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Сначала распределите участника по команде, а затем назначайте игровую роль."
            );
        }

        participant.assignGameRole(request.gameRole().trim());
        return toParticipantItem(participant);
    }

    @Transactional
    public GameSessionParticipantsResponse selectRuntimeStage(String sessionCode, SessionRuntimeStageRequest request) {
        GameSession session = getRuntimeEditableSessionOrThrow(sessionCode);
        SessionStageSetting stage = getStageOrThrow(session, request.stageNumber());
        SessionStageSetting previousStage = session.getActiveStageNumber() != null
                ? getStageOrThrow(session, session.getActiveStageNumber())
                : null;
        boolean stageChanged = previousStage == null
                || !previousStage.getStageNumber().equals(stage.getStageNumber());

        if (previousStage != null
                && stageChanged
                && previousStage.getInteractionMode().hasProblemWorkflow()) {
            sessionEconomyService.settleStageForSession(session, previousStage.getStageNumber());
        }

        try {
            session.selectStage(stage.getStageNumber(), stage.getDurationMinutes());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        gameSessionRepository.save(session);
        sessionEconomyService.resetStageTimeForSession(session.getId());
        if (stageChanged) {
            kanbanService.releaseHeldCardsForStage(session, stage.getStageNumber());
            activateFinalStageCrisisIfNeeded(session, stage.getStageNumber());
            log.info(
                    "Active stage changed: sessionCode={}, previousStage={}, newStage={}, interactionMode={}, durationMinutes={}",
                    session.getCode(),
                    previousStage != null ? previousStage.getStageNumber() : null,
                    stage.getStageNumber(),
                    stage.getInteractionMode(),
                    stage.getDurationMinutes()
            );
        }
        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse startRuntimeTimer(String sessionCode) {
        GameSession session = getRuntimeEditableSessionOrThrow(sessionCode);

        try {
            session.startTimer();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        gameSessionRepository.save(session);
        log.info("Runtime timer started: sessionCode={}, stageNumber={}", session.getCode(), session.getActiveStageNumber());
        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse pauseRuntimeTimer(String sessionCode) {
        GameSession session = getRuntimeEditableSessionOrThrow(sessionCode);

        try {
            session.pauseTimer();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        gameSessionRepository.save(session);
        log.info("Runtime timer paused: sessionCode={}, stageNumber={}", session.getCode(), session.getActiveStageNumber());
        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse resetRuntimeTimer(String sessionCode) {
        GameSession session = getRuntimeEditableSessionOrThrow(sessionCode);
        SessionStageSetting stage = getActiveStageOrThrow(session);

        try {
            session.resetTimer(stage.getDurationMinutes());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        gameSessionRepository.save(session);
        log.info("Runtime timer reset: sessionCode={}, stageNumber={}", session.getCode(), stage.getStageNumber());
        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse startSession(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);
        boolean startingFromLobby = session.getStatus() == GameSessionStatus.LOBBY;
        List<SessionStageSetting> stages = sessionStageSettingRepository.findAllByGameSessionIdOrderByStageNumberAsc(session.getId());

        if (stages.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Перед стартом игры настройте и сохраните хотя бы один этап сессии."
            );
        }

        List<SessionParticipant> participants = sessionParticipantRepository
                .findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId());
        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());

        validateSessionReadyForStart(participants, teams);

        if (session.getActiveStageNumber() == null) {
            SessionStageSetting firstStage = stages.get(0);
            session.initializeStageRuntime(firstStage.getStageNumber(), firstStage.getDurationMinutes());
        }

        try {
            session.start();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        gameSessionRepository.save(session);

        if (startingFromLobby) {
            sessionEconomyService.resetStageTimeForSession(session.getId());
        }

        activateFinalStageCrisisIfNeeded(session, session.getActiveStageNumber());
        log.info(
                "Game session started: sessionCode={}, activeStageNumber={}, participantCount={}, teamCount={}",
                session.getCode(),
                session.getActiveStageNumber(),
                participants.size(),
                teams.size()
        );

        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse pauseSession(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);

        try {
            session.pause();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        gameSessionRepository.save(session);
        log.info("Game session paused: sessionCode={}, activeStageNumber={}", session.getCode(), session.getActiveStageNumber());
        return gameSessionQueryService.getParticipants(sessionCode);
    }

    @Transactional
    public GameSessionParticipantsResponse finishSession(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);
        SessionStageSetting activeStage = session.getActiveStageNumber() != null
                ? getStageOrThrow(session, session.getActiveStageNumber())
                : null;

        activateFinalStageCrisisIfNeeded(session, session.getActiveStageNumber());

        if (activeStage != null && activeStage.getInteractionMode().hasProblemWorkflow()) {
            sessionEconomyService.settleStageForSession(session, activeStage.getStageNumber());
        }

        try {
            session.finish();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        gameSessionRepository.save(session);
        log.info("Game session finished: sessionCode={}, activeStageNumber={}", session.getCode(), session.getActiveStageNumber());
        return gameSessionQueryService.getParticipants(sessionCode);
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
        sessionTeamRepository.deleteAll(sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId()));
        gameSessionRepository.delete(session);

        if (playerIds.isEmpty()) {
            log.info(
                    "Game session deleted: sessionCode={}, sessionId={}, participantCount={}, deletedPlayerCount=0",
                    session.getCode(),
                    session.getId(),
                    participants.size()
            );
            return;
        }

        List<Player> players = playerRepository.findAllByIdIn(playerIds);
        List<Player> exclusivePlayers = players.stream()
                .filter(player -> !sessionParticipantRepository.existsByPlayerId(player.getId()))
                .toList();

        if (!exclusivePlayers.isEmpty()) {
            playerRepository.deleteAll(exclusivePlayers);
        }

        log.info(
                "Game session deleted: sessionCode={}, sessionId={}, participantCount={}, deletedPlayerCount={}",
                session.getCode(),
                session.getId(),
                participants.size(),
                exclusivePlayers.size()
        );
    }

    private GameSession getRuntimeEditableSessionOrThrow(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);

        if (session.getStatus() == GameSessionStatus.FINISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Управление этапами и таймером недоступно после завершения игры.");
        }

        return session;
    }

    private SessionStageSetting getStageOrThrow(GameSession session, Integer stageNumber) {
        return sessionStageSettingRepository.findAllByGameSessionIdOrderByStageNumberAsc(session.getId())
                .stream()
                .filter(stage -> stage.getStageNumber() == stageNumber)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Этап с таким номером не найден."));
    }

    private SessionStageSetting getActiveStageOrThrow(GameSession session) {
        if (session.getActiveStageNumber() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Сначала выберите этап сессии.");
        }

        return getStageOrThrow(session, session.getActiveStageNumber());
    }

    private void clearRolesForParticipants(List<SessionParticipant> participants) {
        participants.forEach(SessionParticipant::clearGameRole);
    }

    private void validateSessionReadyForStart(List<SessionParticipant> participants, List<SessionTeam> teams) {
        long unassignedParticipantsCount = participants.stream()
                .filter(participant -> participant.getTeam() == null)
                .count();

        if (unassignedParticipantsCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Перед стартом игры распределите по командам всех подключившихся участников."
            );
        }

        for (SessionTeam team : teams) {
            List<String> teamRoles = participants.stream()
                    .filter(participant -> participant.getTeam() != null && participant.getTeam().getId().equals(team.getId()))
                    .map(SessionParticipant::getGameRole)
                    .filter(role -> role != null && !role.isBlank())
                    .toList();

            List<String> missingLeadershipRoles = GameRoleCatalog.MANDATORY_LEADERSHIP_ROLES.stream()
                    .filter(role -> teamRoles.stream().noneMatch(role::equalsIgnoreCase))
                    .toList();

            if (!missingLeadershipRoles.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Перед стартом игры в команде '%s' должны быть назначены все руководящие роли: %s."
                                .formatted(team.getName(), String.join(", ", missingLeadershipRoles))
                );
            }
        }
    }


    private void validateTeamHasRequiredLeadershipCapacity(SessionTeam team, List<SessionParticipant> teamParticipants) {
        if (teamParticipants.size() < GameRoleCatalog.MANDATORY_LEADERSHIP_ROLES.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "В команде '%s' недостаточно участников для обязательных руководящих ролей.".formatted(team.getName())
            );
        }
    }

    private void assignRolesForTeam(SessionTeam team, List<SessionParticipant> teamParticipants) {
        List<SessionParticipant> shuffledParticipants = new ArrayList<>(teamParticipants);
        Collections.shuffle(shuffledParticipants, ThreadLocalRandom.current());

        List<Assignment> leadershipAssignments = new ArrayList<>();
        if (!assignLeadershipRoles(shuffledParticipants, 0, leadershipAssignments)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Невозможно назначить руководящие роли в команде '%s' без совпадения с реальными должностями.".formatted(team.getName())
            );
        }

        leadershipAssignments.forEach(assignment -> assignment.participant().assignGameRole(assignment.role()));

        Set<Long> leadershipParticipantIds = leadershipAssignments.stream()
                .map(assignment -> assignment.participant().getId())
                .collect(Collectors.toSet());

        List<SessionParticipant> remainingParticipants = shuffledParticipants.stream()
                .filter(participant -> !leadershipParticipantIds.contains(participant.getId()))
                .toList();

        if (remainingParticipants.size() <= GameRoleCatalog.EXECUTOR_ROLES.size()) {
            List<Assignment> executorAssignments = new ArrayList<>();
            if (!assignUniqueExecutorRoles(remainingParticipants, 0, new ArrayList<>(GameRoleCatalog.EXECUTOR_ROLES), executorAssignments)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Невозможно назначить уникальные исполнительские роли в команде '%s' без совпадения с реальными должностями.".formatted(team.getName())
                );
            }

            executorAssignments.forEach(assignment -> assignment.participant().assignGameRole(assignment.role()));
        } else {
            assignRepeatingExecutorRoles(remainingParticipants);
        }
    }

    private boolean assignLeadershipRoles(
            List<SessionParticipant> participants,
            int roleIndex,
            List<Assignment> assignments
    ) {
        if (roleIndex >= GameRoleCatalog.MANDATORY_LEADERSHIP_ROLES.size()) {
            return true;
        }

        String role = GameRoleCatalog.MANDATORY_LEADERSHIP_ROLES.get(roleIndex);
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

    private void assignRepeatingExecutorRoles(List<SessionParticipant> participants) {
        Map<String, Integer> roleUseCounts = new HashMap<>();
        GameRoleCatalog.EXECUTOR_ROLES.forEach(role -> roleUseCounts.put(role, 0));

        for (SessionParticipant participant : participants) {
            List<String> candidateRoles = GameRoleCatalog.EXECUTOR_ROLES.stream()
                    .filter(role -> !participant.getPlayer().getHospitalPosition().equalsIgnoreCase(role))
                    .toList();

            if (candidateRoles.isEmpty()) {
                candidateRoles = GameRoleCatalog.EXECUTOR_ROLES;
            }

            int minimumUseCount = candidateRoles.stream()
                    .mapToInt(role -> roleUseCounts.getOrDefault(role, 0))
                    .min()
                    .orElse(0);

            List<String> leastUsedRoles = candidateRoles.stream()
                    .filter(role -> roleUseCounts.getOrDefault(role, 0) == minimumUseCount)
                    .toList();

            String selectedRole = leastUsedRoles.get(ThreadLocalRandom.current().nextInt(leastUsedRoles.size()));

            participant.assignGameRole(selectedRole);
            roleUseCounts.computeIfPresent(selectedRole, (role, count) -> count + 1);
        }
    }

    private boolean assignUniqueExecutorRoles(
            List<SessionParticipant> participants,
            int participantIndex,
            List<String> availableRoles,
            List<Assignment> assignments
    ) {
        if (participantIndex >= participants.size()) {
            return true;
        }

        SessionParticipant participant = participants.get(participantIndex);
        List<String> shuffledRoles = new ArrayList<>(availableRoles);
        Collections.shuffle(shuffledRoles, ThreadLocalRandom.current());

        for (String role : shuffledRoles) {
            boolean matchesHospitalPosition = participant.getPlayer().getHospitalPosition().equalsIgnoreCase(role);

            if (matchesHospitalPosition) {
                continue;
            }

            assignments.add(new Assignment(participant, role));
            List<String> nextAvailableRoles = new ArrayList<>(availableRoles);
            nextAvailableRoles.remove(role);

            if (assignUniqueExecutorRoles(participants, participantIndex + 1, nextAvailableRoles, assignments)) {
                return true;
            }

            assignments.removeLast();
        }

        return false;
    }

    private void validateStageSettings(List<GameSessionStageSettingsRequest.StageItem> stages) {
        if (stages == null || stages.size() != DEFAULT_STAGE_COUNT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "В этой версии игры всегда используется ровно 3 этапа."
            );
        }

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

    private void activateFinalStageCrisisIfNeeded(GameSession session, Integer stageNumber) {
        if (stageNumber == null || stageNumber != DEFAULT_STAGE_COUNT) {
            return;
        }

        List<Long> activatedProblemStateIds = sessionEconomyService.activateFinalStageCrisisIfNeeded(session);
        kanbanService.recordStageCrisisEscalations(session, activatedProblemStateIds);
    }

    private List<Integer> resolveProblemDistribution(List<GameSessionStageSettingsRequest.StageItem> orderedStages) {
        boolean hasManualProblemCounts = orderedStages.stream()
                .anyMatch(stage -> stage.problemCount() != null);

        if (!hasManualProblemCounts) {
            return sessionEconomyService.buildEvenProblemDistribution(orderedStages.size());
        }

        boolean hasIncompleteManualCounts = orderedStages.stream()
                .anyMatch(stage -> stage.problemCount() == null);

        if (hasIncompleteManualCounts) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Для ручного распределения укажите количество задач для каждого этапа."
            );
        }

        return orderedStages.stream()
                .map(GameSessionStageSettingsRequest.StageItem::problemCount)
                .toList();
    }

    private GameSession getLobbySessionOrThrow(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);

        if (session.getStatus() != GameSessionStatus.LOBBY) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Настройки этапов, команд и ролей можно менять только до старта игры."
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
                participant.getTeam() != null ? participant.getTeam().getId() : null,
                participant.getTeam() != null ? participant.getTeam().getName() : null,
                participant.getGameRole(),
                participant.getJoinedAt()
        );
    }

    private void initializeTeamInventory(List<SessionTeam> teams) {
        saveInventoryForTeams(teams, teamInventoryCatalog.generateInitialInventory());
    }

    private void replaceTeamInventory(GameSession session, List<TeamInventoryCatalog.InventorySeed> inventory) {
        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());
        teamInventoryItemRepository.deleteAllByTeamGameSessionId(session.getId());
        saveInventoryForTeams(teams, inventory);
    }

    private void saveInventoryForTeams(
            List<SessionTeam> teams,
            List<TeamInventoryCatalog.InventorySeed> inventory
    ) {
        List<TeamInventoryItem> inventoryItems = teams.stream()
                .flatMap(team -> inventory.stream()
                        .map(seed -> new TeamInventoryItem(team, seed.itemName(), seed.quantity())))
                .toList();

        teamInventoryItemRepository.saveAll(inventoryItems);
    }

    private List<TeamInventoryCatalog.InventorySeed> normalizeInventoryItems(
            List<GameSessionInventorySettingsRequest.InventoryItem> items
    ) {
        Map<String, Integer> requestedQuantitiesByName = new HashMap<>();

        for (GameSessionInventorySettingsRequest.InventoryItem item : items) {
            String normalizedItemName = item.itemName().trim().toLowerCase();
            if (requestedQuantitiesByName.put(normalizedItemName, item.quantity()) != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Позиция склада '%s' указана несколько раз.".formatted(item.itemName().trim())
                );
            }
        }

        List<TeamInventoryCatalog.InventorySeed> normalizedInventory = new ArrayList<>();
        List<String> knownItemNames = teamInventoryCatalog.getAllItemNames();

        for (String knownItemName : knownItemNames) {
            Integer quantity = requestedQuantitiesByName.remove(knownItemName.toLowerCase());
            if (quantity == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Укажите количество для позиции склада: %s.".formatted(knownItemName)
                );
            }

            normalizedInventory.add(new TeamInventoryCatalog.InventorySeed(knownItemName, quantity));
        }

        if (!requestedQuantitiesByName.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "В настройках склада есть неизвестные позиции: %s."
                            .formatted(String.join(", ", requestedQuantitiesByName.keySet()))
            );
        }

        return normalizedInventory;
    }

    private GameSessionSummaryResponse toSummary(GameSession session) {
        return new GameSessionSummaryResponse(
                session.getId(),
                session.getCode(),
                session.getName(),
                session.getStatus().name(),
                sessionParticipantRepository.countByGameSessionId(session.getId()),
                sessionTeamRepository.countByGameSessionId(session.getId()),
                sessionStageSettingRepository.countByGameSessionId(session.getId())
        );
    }

    private List<SessionStageSetting> createDefaultStageSettings(GameSession session) {
        return List.of(
                new SessionStageSetting(
                        session,
                        1,
                        DEFAULT_STAGE_DURATION_MINUTES,
                        StageInteractionMode.CHAT_WITH_PROBLEMS
                ),
                new SessionStageSetting(
                        session,
                        2,
                        DEFAULT_STAGE_DURATION_MINUTES,
                        StageInteractionMode.CHAT_AND_KANBAN
                ),
                new SessionStageSetting(
                        session,
                        3,
                        DEFAULT_STAGE_DURATION_MINUTES,
                        StageInteractionMode.CHAT_AND_KANBAN
                )
        );
    }

    private List<TeamNameDraft> generateFunnyTeamNames(int teamCount) {
        List<TeamNameDraft> generatedNames = new ArrayList<>();
        List<String> combinations = new ArrayList<>();

        for (String adjective : TEAM_NAME_ADJECTIVES) {
            for (String noun : TEAM_NAME_NOUNS) {
                combinations.add(adjective + " " + noun);
            }
        }

        Collections.shuffle(combinations, SESSION_CODE_RANDOM);

        for (int index = 0; index < teamCount; index++) {
            String name = index < combinations.size()
                    ? combinations.get(index)
                    : combinations.get(index % combinations.size()) + " " + (index + 1);
            generatedNames.add(new TeamNameDraft(name, index + 1));
        }

        return generatedNames;
    }

    private String generateUniqueSessionCode() {
        for (int attempt = 0; attempt < SESSION_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = generateSessionCodeCandidate();

            if (!gameSessionRepository.existsByCode(candidate)) {
                return candidate;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Не удалось сгенерировать уникальный код сессии. Попробуйте ещё раз."
        );
    }

    private String generateSessionCodeCandidate() {
        StringBuilder prefix = new StringBuilder(SESSION_CODE_PREFIX_LENGTH);

        for (int index = 0; index < SESSION_CODE_PREFIX_LENGTH; index++) {
            int randomIndex = SESSION_CODE_RANDOM.nextInt(SESSION_CODE_ALPHABET.length());
            prefix.append(SESSION_CODE_ALPHABET.charAt(randomIndex));
        }

        int number = SESSION_CODE_RANDOM.nextInt(100);
        return "%s-%02d".formatted(prefix, number);
    }

    private record Assignment(SessionParticipant participant, String role) {
    }

    private record TeamNameDraft(String name, int sortOrder) {
    }
}
