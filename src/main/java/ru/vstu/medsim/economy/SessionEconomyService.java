package ru.vstu.medsim.economy;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.economy.domain.ClinicRoomProblemTemplate;
import ru.vstu.medsim.economy.domain.ClinicRoomTemplate;
import ru.vstu.medsim.economy.domain.ProblemSeverity;
import ru.vstu.medsim.economy.domain.SessionEconomySettings;
import ru.vstu.medsim.economy.domain.SessionProblemStatus;
import ru.vstu.medsim.economy.domain.TeamEconomyEvent;
import ru.vstu.medsim.economy.domain.TeamEconomyEventType;
import ru.vstu.medsim.economy.domain.TeamEconomyState;
import ru.vstu.medsim.economy.domain.TeamProblemState;
import ru.vstu.medsim.economy.domain.TeamRoomState;
import ru.vstu.medsim.economy.dto.GameSessionEconomyResponse;
import ru.vstu.medsim.economy.dto.SessionEconomySettingsItem;
import ru.vstu.medsim.economy.dto.TeamEconomyEventItem;
import ru.vstu.medsim.economy.dto.TeamEconomyItem;
import ru.vstu.medsim.economy.dto.TeamProblemEconomyItem;
import ru.vstu.medsim.economy.dto.TeamRoomEconomyItem;
import ru.vstu.medsim.economy.repository.ClinicRoomProblemTemplateRepository;
import ru.vstu.medsim.economy.repository.ClinicRoomTemplateRepository;
import ru.vstu.medsim.economy.repository.SessionEconomySettingsRepository;
import ru.vstu.medsim.economy.repository.TeamEconomyEventRepository;
import ru.vstu.medsim.economy.repository.TeamEconomyStateRepository;
import ru.vstu.medsim.economy.repository.TeamProblemStateRepository;
import ru.vstu.medsim.economy.repository.TeamRoomStateRepository;
import ru.vstu.medsim.kanban.domain.TeamKanbanCard;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.GameSessionStatus;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.session.GameSessionQueryService;
import ru.vstu.medsim.session.domain.SessionTeam;
import ru.vstu.medsim.session.domain.TeamInventoryItem;
import ru.vstu.medsim.session.dto.SessionEconomySettingsUpdateRequest;
import ru.vstu.medsim.session.repository.SessionTeamRepository;
import ru.vstu.medsim.session.repository.TeamInventoryItemRepository;

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
    private final TeamEconomyEventRepository teamEconomyEventRepository;
    private final TeamRoomStateRepository teamRoomStateRepository;
    private final TeamProblemStateRepository teamProblemStateRepository;
    private final TeamInventoryItemRepository teamInventoryItemRepository;

    public SessionEconomyService(
            GameSessionQueryService gameSessionQueryService,
            SessionTeamRepository sessionTeamRepository,
            ClinicRoomTemplateRepository clinicRoomTemplateRepository,
            ClinicRoomProblemTemplateRepository clinicRoomProblemTemplateRepository,
            SessionEconomySettingsRepository sessionEconomySettingsRepository,
            TeamEconomyStateRepository teamEconomyStateRepository,
            TeamEconomyEventRepository teamEconomyEventRepository,
            TeamRoomStateRepository teamRoomStateRepository,
            TeamProblemStateRepository teamProblemStateRepository,
            TeamInventoryItemRepository teamInventoryItemRepository
    ) {
        this.gameSessionQueryService = gameSessionQueryService;
        this.sessionTeamRepository = sessionTeamRepository;
        this.clinicRoomTemplateRepository = clinicRoomTemplateRepository;
        this.clinicRoomProblemTemplateRepository = clinicRoomProblemTemplateRepository;
        this.sessionEconomySettingsRepository = sessionEconomySettingsRepository;
        this.teamEconomyStateRepository = teamEconomyStateRepository;
        this.teamEconomyEventRepository = teamEconomyEventRepository;
        this.teamRoomStateRepository = teamRoomStateRepository;
        this.teamProblemStateRepository = teamProblemStateRepository;
        this.teamInventoryItemRepository = teamInventoryItemRepository;
    }

    @Transactional
    public List<TeamProblemState> initializeForSession(
            GameSession session,
            List<SessionTeam> teams,
            BigDecimal startingBudget,
            Integer stageTimeUnits
    ) {
        BigDecimal resolvedStartingBudget = resolveStartingBudget(startingBudget);
        int resolvedStageTimeUnits = resolveStageTimeUnits(stageTimeUnits);

        sessionEconomySettingsRepository.save(
                new SessionEconomySettings(session, resolvedStartingBudget, resolvedStageTimeUnits)
        );

        if (teams.isEmpty()) {
            return List.of();
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

        List<TeamProblemState> savedProblemStates = teamProblemStateRepository.saveAll(createdProblemStates);
        return savedProblemStates;
    }

    @Transactional
    public void resetStageTimeForSession(Long gameSessionId) {
        SessionEconomySettings settings = sessionEconomySettingsRepository.findByGameSessionId(gameSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Настройки экономики сессии не найдены."));

        teamEconomyStateRepository.findAllByTeamGameSessionIdOrderByTeamSortOrderAscIdAsc(gameSessionId)
                .forEach(teamEconomyState -> teamEconomyState.resetStageTime(settings.getStageTimeUnits()));
    }

    @Transactional
    public void spendResourcesForTask(SessionParticipant actor, TeamKanbanCard card) {
        if (card.getResourcesSpentAt() != null) {
            return;
        }

        ClinicRoomProblemTemplate problemTemplate = card.getProblemState().getProblemTemplate();
        BigDecimal budgetCost = problemTemplate.getBudgetCost();
        int timeCost = problemTemplate.getTimeCost();
        String requiredItemName = problemTemplate.getRequiredItemName();
        int requiredItemQuantity = problemTemplate.getRequiredItemQuantity() != null
                ? problemTemplate.getRequiredItemQuantity()
                : 0;

        TeamEconomyState teamState = teamEconomyStateRepository.findByTeamId(card.getTeam().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Экономика команды не найдена."));

        if (teamState.getCurrentBalance().compareTo(budgetCost) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У команды недостаточно бюджета для старта задачи.");
        }

        if (teamState.getCurrentStageTimeUnits() < timeCost) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У команды недостаточно времени этапа для старта задачи.");
        }

        TeamInventoryItem inventoryItem = null;
        if (requiredItemName != null && !requiredItemName.isBlank() && requiredItemQuantity > 0) {
            inventoryItem = teamInventoryItemRepository
                    .findByTeamIdAndItemNameIgnoreCase(card.getTeam().getId(), requiredItemName)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "На складе команды нет нужного предмета: %s.".formatted(requiredItemName)
                    ));

            if (inventoryItem.getQuantity() < requiredItemQuantity) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Недостаточно предметов '%s' на складе команды.".formatted(requiredItemName)
                );
            }
        }

        teamState.spendResources(budgetCost, timeCost);
        if (inventoryItem != null) {
            inventoryItem.consume(requiredItemQuantity);
        }
        card.markResourcesSpent();

        teamEconomyEventRepository.save(new TeamEconomyEvent(
                card.getTeam(),
                actor,
                card,
                TeamEconomyEventType.TASK_RESOURCES_SPENT,
                problemTemplate.getStageNumber(),
                budgetCost.negate(),
                -timeCost,
                inventoryItem != null ? inventoryItem.getItemName() : null,
                inventoryItem != null ? -requiredItemQuantity : 0,
                "Старт задачи: %s. Списано бюджет %.2f и время %d."
                        .formatted(problemTemplate.getTitle(), budgetCost, timeCost)
        ));
    }

    @Transactional
    public void settleStageForSession(GameSession session, Integer stageNumber) {
        if (stageNumber == null) {
            return;
        }

        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());

        for (SessionTeam team : teams) {
            if (teamEconomyEventRepository.existsByTeamIdAndStageNumberAndEventType(
                    team.getId(),
                    stageNumber,
                    TeamEconomyEventType.STAGE_SETTLED
            )) {
                continue;
            }

            settleStageForTeam(team, stageNumber);
        }
    }

    @Transactional
    public GameSessionEconomyResponse updateEconomySettings(
            String sessionCode,
            SessionEconomySettingsUpdateRequest request
    ) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);

        if (session.getStatus() != GameSessionStatus.LOBBY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Экономические настройки можно менять только до старта игры.");
        }

        BigDecimal resolvedStartingBudget = resolveStartingBudget(request.startingBudget());
        int resolvedStageTimeUnits = resolveStageTimeUnits(request.stageTimeUnits());

        SessionEconomySettings settings = sessionEconomySettingsRepository.findByGameSessionId(session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Настройки экономики сессии не найдены."));
        settings.update(resolvedStartingBudget, resolvedStageTimeUnits);

        teamEconomyStateRepository.findAllByTeamGameSessionIdOrderByTeamSortOrderAscIdAsc(session.getId())
                .forEach(teamEconomyState -> teamEconomyState.resetForLobby(resolvedStartingBudget, resolvedStageTimeUnits));

        return getEconomyOverview(sessionCode);
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

    @Transactional(readOnly = true)
    public TeamEconomyItem getTeamEconomy(SessionTeam team) {
        TeamEconomyState teamState = teamEconomyStateRepository.findByTeamId(team.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Экономика команды '%s' не найдена.".formatted(team.getName())));

        List<TeamRoomState> roomStates = teamRoomStateRepository.findAllByTeamIdInOrderByTeamSortOrderAscClinicRoomSortOrderAscIdAsc(
                List.of(team.getId())
        );
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

        return toTeamItem(team, teamState, roomStates, problemStatesByRoomStateId);
    }

    private void settleStageForTeam(SessionTeam team, Integer stageNumber) {
        TeamEconomyState state = teamEconomyStateRepository.findByTeamId(team.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Экономика команды не найдена."));
        List<TeamRoomState> roomStates = teamRoomStateRepository.findAllByTeamIdInOrderByTeamSortOrderAscClinicRoomSortOrderAscIdAsc(
                List.of(team.getId())
        );
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

        BigDecimal income = roomStates.stream()
                .map(roomState -> roomState.getClinicRoom().getBaseIncome().multiply(
                        resolveRoomStateCoefficient(problemStatesByRoomStateId.getOrDefault(roomState.getId(), List.of()), stageNumber)
                ))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal penalties = problemStates.stream()
                .filter(problemState -> isReleasedByStage(problemState, stageNumber))
                .filter(problemState -> problemState.getStatus() != SessionProblemStatus.RESOLVED)
                .map(problemState -> problemState.getProblemTemplate().getIgnorePenalty())
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<TeamProblemState> currentStageProblems = problemStates.stream()
                .filter(problemState -> problemState.getProblemTemplate().getStageNumber().equals(stageNumber))
                .toList();

        BigDecimal bonus = !currentStageProblems.isEmpty() && currentStageProblems.stream()
                .allMatch(problemState -> problemState.getStatus() == SessionProblemStatus.RESOLVED)
                ? new BigDecimal("2.00")
                : BigDecimal.ZERO.setScale(2);

        state.applyStageSettlement(income, penalties, bonus);
        teamEconomyEventRepository.save(new TeamEconomyEvent(
                team,
                null,
                null,
                TeamEconomyEventType.STAGE_SETTLED,
                stageNumber,
                income.add(bonus).subtract(penalties),
                0,
                null,
                0,
                "Итог этапа %d: доход %.2f, штрафы %.2f, бонус %.2f."
                        .formatted(stageNumber, income, penalties, bonus)
        ));
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
                roomItems,
                teamEconomyEventRepository.findRecentForTeam(team.getId(), PageRequest.of(0, 8))
                        .stream()
                        .map(this::toEconomyEventItem)
                        .toList()
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
                        problemState.getProblemTemplate().getStageNumber(),
                        problemState.getProblemTemplate().getTitle(),
                        problemState.getProblemTemplate().getSeverity().name(),
                        problemState.getProblemTemplate().getBudgetCost(),
                        problemState.getProblemTemplate().getTimeCost(),
                        problemState.getProblemTemplate().getRequiredItemName(),
                        problemState.getProblemTemplate().getRequiredItemQuantity(),
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

    private BigDecimal resolveRoomStateCoefficient(List<TeamProblemState> problemStates, Integer stageNumber) {
        return problemStates.stream()
                .filter(problemState -> isReleasedByStage(problemState, stageNumber))
                .filter(problemState -> problemState.getStatus() != SessionProblemStatus.RESOLVED)
                .map(problemState -> problemState.getProblemTemplate().getSeverity())
                .max(Comparator.comparingInt(Enum::ordinal))
                .map(ProblemSeverity::getStateCoefficient)
                .orElse(BigDecimal.ONE.setScale(2));
    }

    private boolean isReleasedByStage(TeamProblemState problemState, Integer stageNumber) {
        Integer problemStageNumber = problemState.getProblemTemplate().getStageNumber();
        return stageNumber == null || problemStageNumber == null || problemStageNumber <= stageNumber;
    }

    private TeamEconomyEventItem toEconomyEventItem(TeamEconomyEvent event) {
        return new TeamEconomyEventItem(
                event.getId(),
                event.getEventType().name(),
                event.getStageNumber(),
                event.getAmountDelta(),
                event.getTimeDelta(),
                event.getItemName(),
                event.getItemQuantityDelta(),
                event.getMessage(),
                event.getCreatedAt()
        );
    }

    private BigDecimal resolveStartingBudget(BigDecimal startingBudget) {
        return startingBudget != null
                ? startingBudget.setScale(2, RoundingMode.HALF_UP)
                : DEFAULT_STARTING_BUDGET;
    }

    private int resolveStageTimeUnits(Integer stageTimeUnits) {
        return stageTimeUnits != null
                ? stageTimeUnits
                : DEFAULT_STAGE_TIME_UNITS;
    }
}
