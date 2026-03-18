package ru.vstu.medsim.economy;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.economy.domain.ClinicRoomProblemTemplate;
import ru.vstu.medsim.economy.domain.ClinicRoomTemplate;
import ru.vstu.medsim.economy.domain.ProblemSeverity;
import ru.vstu.medsim.economy.domain.SessionEconomySettings;
import ru.vstu.medsim.economy.domain.SessionProblemStatus;
import ru.vstu.medsim.economy.domain.TeamEconomyState;
import ru.vstu.medsim.economy.domain.TeamProblemState;
import ru.vstu.medsim.economy.domain.TeamRoomState;
import ru.vstu.medsim.economy.dto.GameSessionEconomyResponse;
import ru.vstu.medsim.economy.dto.SessionEconomySettingsItem;
import ru.vstu.medsim.economy.dto.TeamEconomyItem;
import ru.vstu.medsim.economy.dto.TeamProblemEconomyItem;
import ru.vstu.medsim.economy.dto.TeamRoomEconomyItem;
import ru.vstu.medsim.economy.repository.ClinicRoomProblemTemplateRepository;
import ru.vstu.medsim.economy.repository.ClinicRoomTemplateRepository;
import ru.vstu.medsim.economy.repository.SessionEconomySettingsRepository;
import ru.vstu.medsim.economy.repository.TeamEconomyStateRepository;
import ru.vstu.medsim.economy.repository.TeamProblemStateRepository;
import ru.vstu.medsim.economy.repository.TeamRoomStateRepository;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.session.GameSessionQueryService;
import ru.vstu.medsim.session.domain.SessionTeam;
import ru.vstu.medsim.session.repository.SessionTeamRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SessionEconomyService {

    public static final BigDecimal DEFAULT_STARTING_BUDGET = new BigDecimal("15.00");
    public static final int DEFAULT_STAGE_TIME_UNITS = 15;

    private final GameSessionQueryService gameSessionQueryService;
    private final SessionTeamRepository sessionTeamRepository;
    private final ClinicRoomTemplateRepository clinicRoomTemplateRepository;
    private final ClinicRoomProblemTemplateRepository clinicRoomProblemTemplateRepository;
    private final SessionEconomySettingsRepository sessionEconomySettingsRepository;
    private final TeamEconomyStateRepository teamEconomyStateRepository;
    private final TeamRoomStateRepository teamRoomStateRepository;
    private final TeamProblemStateRepository teamProblemStateRepository;

    public SessionEconomyService(
            GameSessionQueryService gameSessionQueryService,
            SessionTeamRepository sessionTeamRepository,
            ClinicRoomTemplateRepository clinicRoomTemplateRepository,
            ClinicRoomProblemTemplateRepository clinicRoomProblemTemplateRepository,
            SessionEconomySettingsRepository sessionEconomySettingsRepository,
            TeamEconomyStateRepository teamEconomyStateRepository,
            TeamRoomStateRepository teamRoomStateRepository,
            TeamProblemStateRepository teamProblemStateRepository
    ) {
        this.gameSessionQueryService = gameSessionQueryService;
        this.sessionTeamRepository = sessionTeamRepository;
        this.clinicRoomTemplateRepository = clinicRoomTemplateRepository;
        this.clinicRoomProblemTemplateRepository = clinicRoomProblemTemplateRepository;
        this.sessionEconomySettingsRepository = sessionEconomySettingsRepository;
        this.teamEconomyStateRepository = teamEconomyStateRepository;
        this.teamRoomStateRepository = teamRoomStateRepository;
        this.teamProblemStateRepository = teamProblemStateRepository;
    }

    @Transactional
    public void initializeForSession(
            GameSession session,
            List<SessionTeam> teams,
            BigDecimal startingBudget,
            Integer stageTimeUnits
    ) {
        BigDecimal resolvedStartingBudget = startingBudget != null
                ? startingBudget.setScale(2, RoundingMode.HALF_UP)
                : DEFAULT_STARTING_BUDGET;
        int resolvedStageTimeUnits = stageTimeUnits != null
                ? stageTimeUnits
                : DEFAULT_STAGE_TIME_UNITS;

        sessionEconomySettingsRepository.save(
                new SessionEconomySettings(session, resolvedStartingBudget, resolvedStageTimeUnits)
        );

        if (teams.isEmpty()) {
            return;
        }

        List<ClinicRoomTemplate> roomTemplates = clinicRoomTemplateRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<ClinicRoomProblemTemplate>> roomProblemTemplates = clinicRoomProblemTemplateRepository
                .findAllByOrderByClinicRoomSortOrderAscProblemNumberAscIdAsc()
                .stream()
                .collect(Collectors.groupingBy(
                        template -> template.getClinicRoom().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        teamEconomyStateRepository.saveAll(
                teams.stream()
                        .map(team -> new TeamEconomyState(team, resolvedStartingBudget, resolvedStageTimeUnits))
                        .toList()
        );

        List<TeamRoomState> createdRoomStates = teamRoomStateRepository.saveAll(
                teams.stream()
                        .flatMap(team -> roomTemplates.stream().map(roomTemplate -> new TeamRoomState(team, roomTemplate)))
                        .toList()
        );

        List<TeamProblemState> createdProblemStates = new ArrayList<>();
        for (TeamRoomState roomState : createdRoomStates) {
            List<ClinicRoomProblemTemplate> templates = roomProblemTemplates.getOrDefault(
                    roomState.getClinicRoom().getId(),
                    List.of()
            );
            for (ClinicRoomProblemTemplate problemTemplate : templates) {
                createdProblemStates.add(new TeamProblemState(roomState, problemTemplate, SessionProblemStatus.ACTIVE));
            }
        }

        teamProblemStateRepository.saveAll(createdProblemStates);
    }

    @Transactional
    public void resetStageTimeForSession(Long gameSessionId) {
        SessionEconomySettings settings = sessionEconomySettingsRepository.findByGameSessionId(gameSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Настройки экономики сессии не найдены."));

        teamEconomyStateRepository.findAllByTeamGameSessionIdOrderByTeamSortOrderAscIdAsc(gameSessionId)
                .forEach(teamEconomyState -> teamEconomyState.resetStageTime(settings.getStageTimeUnits()));
    }

    @Transactional(readOnly = true)
    public GameSessionEconomyResponse getEconomyOverview(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);
        SessionEconomySettings settings = sessionEconomySettingsRepository.findByGameSessionId(session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Настройки экономики сессии не найдены."));

        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());
        List<Long> teamIds = teams.stream().map(SessionTeam::getId).toList();

        Map<Long, TeamEconomyState> teamStateByTeamId = teamEconomyStateRepository
                .findAllByTeamGameSessionIdOrderByTeamSortOrderAscIdAsc(session.getId())
                .stream()
                .collect(Collectors.toMap(state -> state.getTeam().getId(), Function.identity()));

        List<TeamRoomState> roomStates = teamIds.isEmpty()
                ? List.of()
                : teamRoomStateRepository.findAllByTeamIdInOrderByTeamSortOrderAscClinicRoomSortOrderAscIdAsc(teamIds);

        Map<Long, List<TeamRoomState>> roomStatesByTeamId = roomStates.stream()
                .collect(Collectors.groupingBy(
                        roomState -> roomState.getTeam().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Long> teamRoomStateIds = roomStates.stream().map(TeamRoomState::getId).toList();
        List<TeamProblemState> problemStates = teamRoomStateIds.isEmpty()
                ? List.of()
                : teamProblemStateRepository.findAllByTeamRoomStateIdInOrderByTeamRoomStateClinicRoomSortOrderAscProblemTemplateProblemNumberAscIdAsc(teamRoomStateIds);

        Map<Long, List<TeamProblemState>> problemStatesByRoomStateId = problemStates.stream()
                .collect(Collectors.groupingBy(
                        problemState -> problemState.getTeamRoomState().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<TeamEconomyItem> teamItems = teams.stream()
                .map(team -> toTeamItem(
                        team,
                        teamStateByTeamId.get(team.getId()),
                        roomStatesByTeamId.getOrDefault(team.getId(), List.of()),
                        problemStatesByRoomStateId
                ))
                .toList();

        return new GameSessionEconomyResponse(
                session.getId(),
                session.getCode(),
                session.getName(),
                session.getStatus().name(),
                new SessionEconomySettingsItem(settings.getStartingBudget(), settings.getStageTimeUnits()),
                teamItems
        );
    }

    private TeamEconomyItem toTeamItem(
            SessionTeam team,
            TeamEconomyState state,
            List<TeamRoomState> roomStates,
            Map<Long, List<TeamProblemState>> problemStatesByRoomStateId
    ) {
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Экономика команды '%s' не найдена.".formatted(team.getName()));
        }

        List<TeamRoomEconomyItem> roomItems = roomStates.stream()
                .map(roomState -> toRoomItem(roomState, problemStatesByRoomStateId.getOrDefault(roomState.getId(), List.of())))
                .toList();

        return new TeamEconomyItem(
                team.getId(),
                team.getName(),
                state.getCurrentBalance(),
                state.getCurrentStageTimeUnits(),
                state.getTotalIncome(),
                state.getTotalExpenses(),
                state.getTotalPenalties(),
                state.getTotalBonuses(),
                roomItems
        );
    }

    private TeamRoomEconomyItem toRoomItem(TeamRoomState roomState, List<TeamProblemState> problemStates) {
        List<TeamProblemState> activeProblemStates = problemStates.stream()
                .filter(problemState -> problemState.getStatus() != SessionProblemStatus.RESOLVED)
                .toList();

        ProblemSeverity worstSeverity = activeProblemStates.stream()
                .map(problemState -> problemState.getProblemTemplate().getSeverity())
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(null);

        BigDecimal stateCoefficient = worstSeverity != null
                ? worstSeverity.getStateCoefficient()
                : BigDecimal.ONE.setScale(2);

        List<TeamProblemEconomyItem> problemItems = problemStates.stream()
                .map(problemState -> new TeamProblemEconomyItem(
                        problemState.getId(),
                        problemState.getProblemTemplate().getProblemNumber(),
                        problemState.getProblemTemplate().getTitle(),
                        problemState.getProblemTemplate().getSeverity().name(),
                        problemState.getProblemTemplate().getIgnorePenalty(),
                        problemState.getProblemTemplate().getSeverity().getPenaltyWeight(),
                        problemState.getStatus().name()
                ))
                .toList();

        return new TeamRoomEconomyItem(
                roomState.getId(),
                roomState.getClinicRoom().getCode(),
                roomState.getClinicRoom().getName(),
                roomState.getClinicRoom().getBaseIncome(),
                activeProblemStates.size(),
                worstSeverity != null ? worstSeverity.name() : null,
                stateCoefficient,
                problemItems
        );
    }
}
