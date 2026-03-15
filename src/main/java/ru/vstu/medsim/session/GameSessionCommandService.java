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
import ru.vstu.medsim.session.domain.SessionTeam;
import ru.vstu.medsim.session.dto.GameSessionCreateRequest;
import ru.vstu.medsim.session.dto.GameSessionParticipantItem;
import ru.vstu.medsim.session.dto.GameSessionParticipantsResponse;
import ru.vstu.medsim.session.dto.GameSessionRenameRequest;
import ru.vstu.medsim.session.dto.GameSessionRoleAssignmentRequest;
import ru.vstu.medsim.session.dto.GameSessionStageSettingsRequest;
import ru.vstu.medsim.session.dto.GameSessionSummaryResponse;
import ru.vstu.medsim.session.dto.GameSessionTeamAssignmentRequest;
import ru.vstu.medsim.session.dto.GameSessionTeamRenameRequest;
import ru.vstu.medsim.session.repository.SessionStageSettingRepository;
import ru.vstu.medsim.session.repository.SessionTeamRepository;

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

    private static final String SESSION_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int SESSION_CODE_PREFIX_LENGTH = 4;
    private static final int SESSION_CODE_MAX_ATTEMPTS = 200;
    private static final SecureRandom SESSION_CODE_RANDOM = new SecureRandom();

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
    private static final int MAX_UNIQUE_TEAM_ROLE_COUNT = MANDATORY_LEADERSHIP_ROLES.size() + EXECUTOR_ROLES.size();

    private final GameSessionQueryService gameSessionQueryService;
    private final GameSessionRepository gameSessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final PlayerRepository playerRepository;
    private final SessionStageSettingRepository sessionStageSettingRepository;
    private final SessionTeamRepository sessionTeamRepository;

    public GameSessionCommandService(
            GameSessionQueryService gameSessionQueryService,
            GameSessionRepository gameSessionRepository,
            SessionParticipantRepository sessionParticipantRepository,
            PlayerRepository playerRepository,
            SessionStageSettingRepository sessionStageSettingRepository,
            SessionTeamRepository sessionTeamRepository
    ) {
        this.gameSessionQueryService = gameSessionQueryService;
        this.gameSessionRepository = gameSessionRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
        this.playerRepository = playerRepository;
        this.sessionStageSettingRepository = sessionStageSettingRepository;
        this.sessionTeamRepository = sessionTeamRepository;
    }

    @Transactional
    public GameSessionSummaryResponse createSession(GameSessionCreateRequest request) {
        String sessionName = request.sessionName().trim();
        String sessionCode = generateUniqueSessionCode();

        GameSession session = gameSessionRepository.save(
                new GameSession(sessionCode, sessionName, GameSessionStatus.LOBBY)
        );

        List<SessionTeam> teams = generateFunnyTeamNames(request.teamCount()).stream()
                .map(name -> new SessionTeam(session, name.name(), name.sortOrder()))
                .toList();
        sessionTeamRepository.saveAll(teams);

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
                .findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId())
                .stream()
                .filter(participant -> participant.getTeam() != null)
                .toList();

        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());

        if (participants.size() < MANDATORY_LEADERSHIP_ROLES.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Для автоматического распределения ролей нужно минимум 3 участника, уже распределённых по командам."
            );
        }

        for (SessionTeam team : teams) {
            List<SessionParticipant> teamParticipants = participants.stream()
                    .filter(participant -> participant.getTeam() != null && participant.getTeam().getId().equals(team.getId()))
                    .toList();

            validateTeamRoleAssignment(team, teamParticipants);
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
    public GameSessionSummaryResponse startSession(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);

        if (!sessionStageSettingRepository.existsByGameSessionId(session.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Перед стартом игры настройте и сохраните хотя бы один этап сессии."
            );
        }

        List<SessionParticipant> participants = sessionParticipantRepository
                .findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId());
        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());

        validateSessionReadyForStart(participants, teams);

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
        sessionTeamRepository.deleteAll(sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId()));
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

            List<String> missingLeadershipRoles = MANDATORY_LEADERSHIP_ROLES.stream()
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


    private void validateTeamRoleAssignment(SessionTeam team, List<SessionParticipant> teamParticipants) {
        if (teamParticipants.size() < MANDATORY_LEADERSHIP_ROLES.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "В команде '%s' недостаточно участников для обязательных руководящих ролей.".formatted(team.getName())
            );
        }

        if (teamParticipants.size() > MAX_UNIQUE_TEAM_ROLE_COUNT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "В команде '%s' слишком много участников для уникального распределения ролей без повторов.".formatted(team.getName())
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

        List<Assignment> executorAssignments = new ArrayList<>();
        if (!assignUniqueExecutorRoles(remainingParticipants, 0, new ArrayList<>(EXECUTOR_ROLES), executorAssignments)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Невозможно назначить уникальные исполнительские роли в команде '%s' без совпадения с реальными должностями.".formatted(team.getName())
            );
        }

        executorAssignments.forEach(assignment -> assignment.participant().assignGameRole(assignment.role()));
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
