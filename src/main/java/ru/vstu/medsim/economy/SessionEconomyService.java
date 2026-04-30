package ru.vstu.medsim.economy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.economy.domain.ClinicRoomProblemTemplate;
import ru.vstu.medsim.economy.domain.ClinicRoomTemplate;
import ru.vstu.medsim.economy.domain.FinalStageCrisisType;
import ru.vstu.medsim.economy.domain.ProblemSeverity;
import ru.vstu.medsim.economy.domain.ProblemEscalationType;
import ru.vstu.medsim.economy.domain.ResourceReservationStatus;
import ru.vstu.medsim.economy.domain.SessionEconomySettings;
import ru.vstu.medsim.economy.domain.SessionProblemStatus;
import ru.vstu.medsim.economy.domain.TeamEconomyEvent;
import ru.vstu.medsim.economy.domain.TeamEconomyEventType;
import ru.vstu.medsim.economy.domain.TeamEconomyState;
import ru.vstu.medsim.economy.domain.TeamProblemState;
import ru.vstu.medsim.economy.domain.TeamResourceReservation;
import ru.vstu.medsim.economy.domain.TeamRoomState;
import ru.vstu.medsim.economy.dto.GameSessionEconomyResponse;
import ru.vstu.medsim.economy.dto.SessionEconomySettingsItem;
import ru.vstu.medsim.economy.dto.TeamEconomyEventItem;
import ru.vstu.medsim.economy.dto.TeamEconomyItem;
import ru.vstu.medsim.economy.dto.TeamProblemEconomyItem;
import ru.vstu.medsim.economy.dto.TeamEconomyReservedItem;
import ru.vstu.medsim.economy.dto.TeamRoomEconomyItem;
import ru.vstu.medsim.economy.dto.TeamStageEconomySummaryItem;
import ru.vstu.medsim.economy.repository.ClinicRoomProblemTemplateRepository;
import ru.vstu.medsim.economy.repository.ClinicRoomTemplateRepository;
import ru.vstu.medsim.economy.repository.SessionEconomySettingsRepository;
import ru.vstu.medsim.economy.repository.TeamEconomyEventRepository;
import ru.vstu.medsim.economy.repository.TeamEconomyStateRepository;
import ru.vstu.medsim.economy.repository.TeamProblemStateRepository;
import ru.vstu.medsim.economy.repository.TeamResourceReservationRepository;
import ru.vstu.medsim.economy.repository.TeamRoomStateRepository;
import ru.vstu.medsim.kanban.domain.KanbanSolutionOption;
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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SessionEconomyService {

    private static final Logger log = LoggerFactory.getLogger(SessionEconomyService.class);

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
    private final TeamResourceReservationRepository teamResourceReservationRepository;

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
            TeamInventoryItemRepository teamInventoryItemRepository,
            TeamResourceReservationRepository teamResourceReservationRepository
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
        this.teamResourceReservationRepository = teamResourceReservationRepository;
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

        teamEconomyStateRepository.saveAll(
                teams.stream()
                        .map(team -> new TeamEconomyState(team, resolvedStartingBudget, resolvedStageTimeUnits))
                        .toList()
        );

        return createProblemStatesForTeams(teams);
    }

    @Transactional
    public List<TeamProblemState> restartSessionProgress(
            GameSession session,
            List<SessionTeam> teams,
            List<Integer> stageProblemCounts
    ) {
        SessionEconomySettings settings = sessionEconomySettingsRepository.findByGameSessionId(session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Настройки экономики сессии не найдены."));

        teamEconomyEventRepository.deleteAllByTeamGameSessionId(session.getId());
        teamEconomyStateRepository.findAllByTeamGameSessionIdOrderByTeamSortOrderAscIdAsc(session.getId())
                .forEach(teamEconomyState -> teamEconomyState.resetForLobby(
                        settings.getStartingBudget(),
                        settings.getStageTimeUnits()
                ));

        teams.forEach(team -> teamInventoryItemRepository.findAllByTeamIdOrderByItemNameAsc(team.getId())
                .forEach(TeamInventoryItem::restoreInitialQuantity));

        teamRoomStateRepository.deleteAllByTeamGameSessionId(session.getId());

        if (teams.isEmpty()) {
            return List.of();
        }

        List<TeamProblemState> problemStates = createProblemStatesForTeams(teams);
        redistributeProblemsForSession(session.getId(), stageProblemCounts);
        resetStageTimeForSession(session.getId());
        return problemStates;
    }

    private List<TeamProblemState> createProblemStatesForTeams(List<SessionTeam> teams) {
        List<ClinicRoomTemplate> roomTemplates = clinicRoomTemplateRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<ClinicRoomProblemTemplate>> roomProblemTemplates = clinicRoomProblemTemplateRepository
                .findAllByOrderByClinicRoomSortOrderAscProblemNumberAscIdAsc()
                .stream()
                .collect(Collectors.groupingBy(
                        template -> template.getClinicRoom().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

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
    public void redistributeProblemsForSession(Long gameSessionId, List<Integer> stageProblemCounts) {
        validateProblemDistribution(stageProblemCounts);

        int totalProblemCount = getTotalProblemTemplateCount();
        int requestedProblemCount = stageProblemCounts.stream()
                .mapToInt(Integer::intValue)
                .sum();

        if (requestedProblemCount != totalProblemCount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Распределите все задачи по этапам: указано %d из %d."
                            .formatted(requestedProblemCount, totalProblemCount)
            );
        }

        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(gameSessionId);

        for (SessionTeam team : teams) {
            List<TeamProblemState> problemStates = teamProblemStateRepository
                    .findAllByTeamRoomStateTeamIdOrderByTeamRoomStateClinicRoomSortOrderAscProblemTemplateProblemNumberAscIdAsc(team.getId());

            if (problemStates.size() != totalProblemCount) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Количество задач команды '%s' не совпадает с шаблоном игры.".formatted(team.getName())
                );
            }

            int problemIndex = 0;
            for (int stageIndex = 0; stageIndex < stageProblemCounts.size(); stageIndex++) {
                int stageNumber = stageIndex + 1;
                int problemCount = stageProblemCounts.get(stageIndex);
                for (int index = 0; index < problemCount; index++) {
                    problemStates.get(problemIndex).assignStageNumber(stageNumber);
                    problemIndex++;
                }
            }

            teamProblemStateRepository.saveAll(problemStates);
        }
    }

    @Transactional
    public List<Long> activateFinalStageCrisisIfNeeded(GameSession session) {
        if (session == null
                || session.getStatus() == GameSessionStatus.LOBBY
                || session.getActiveStageNumber() == null
                || session.getActiveStageNumber() < 3
                || session.getFinalStageCrisisType() != null) {
            return List.of();
        }

        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());
        Map<Long, List<TeamProblemState>> backlogProblemsByTeamId = new LinkedHashMap<>();
        List<TeamProblemState> allBacklogProblems = new ArrayList<>();

        for (SessionTeam team : teams) {
            List<TeamProblemState> backlogProblems = teamProblemStateRepository
                    .findAllByTeamRoomStateTeamIdOrderByTeamRoomStateClinicRoomSortOrderAscProblemTemplateProblemNumberAscIdAsc(team.getId())
                    .stream()
                    .filter(this::isBacklogProblemForFinalStage)
                    .toList();

            backlogProblemsByTeamId.put(team.getId(), backlogProblems);
            allBacklogProblems.addAll(backlogProblems);
        }

        FinalStageCrisisType crisisType = resolveFinalStageCrisisType(allBacklogProblems);
        session.activateFinalStageCrisis(crisisType);

        List<TeamProblemState> activatedProblems = new ArrayList<>();

        for (List<TeamProblemState> backlogProblems : backlogProblemsByTeamId.values()) {
            List<TeamProblemState> orderedProblems = backlogProblems.stream()
                    .filter(problemState -> !problemState.hasActiveEscalation())
                    .sorted(Comparator
                            .comparingInt(this::buildEscalationScore)
                            .reversed()
                            .thenComparing(problemState -> problemState.getTeamRoomState().getClinicRoom().getSortOrder())
                            .thenComparing(problemState -> problemState.getProblemTemplate().getProblemNumber())
                            .thenComparing(TeamProblemState::getId))
                    .toList();

            Set<Long> affectedRoomStateIds = new HashSet<>();
            int activatedCount = 0;

            for (TeamProblemState problemState : orderedProblems) {
                if (activatedCount >= 2) {
                    break;
                }

                Long roomStateId = problemState.getTeamRoomState().getId();
                if (affectedRoomStateIds.contains(roomStateId)) {
                    continue;
                }

                problemState.activateEscalation(classifyEscalationType(problemState));
                activatedProblems.add(problemState);
                affectedRoomStateIds.add(roomStateId);
                activatedCount++;
            }
        }

        if (!activatedProblems.isEmpty()) {
            teamProblemStateRepository.saveAll(activatedProblems);
        }

        log.info(
                "Final stage crisis activated: sessionCode={}, crisisType={}, activatedEscalations={}, backlogProblemCount={}",
                session.getCode(),
                crisisType,
                activatedProblems.size(),
                allBacklogProblems.size()
        );

        return activatedProblems.stream()
                .map(TeamProblemState::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Integer> buildEvenProblemDistribution(int stageCount) {
        if (stageCount < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Количество этапов должно быть больше нуля.");
        }

        int totalProblemCount = getTotalProblemTemplateCount();
        int baseProblemCount = totalProblemCount / stageCount;
        int remainder = totalProblemCount % stageCount;
        List<Integer> distribution = new ArrayList<>();

        for (int index = 0; index < stageCount; index++) {
            distribution.add(baseProblemCount + (index < remainder ? 1 : 0));
        }

        return distribution;
    }

    @Transactional(readOnly = true)
    public int getTotalProblemTemplateCount() {
        return Math.toIntExact(clinicRoomProblemTemplateRepository.count());
    }

    @Transactional(readOnly = true)
    public Optional<TeamResourceReservation> getCurrentReservation(TeamKanbanCard card) {
        return teamResourceReservationRepository.findFirstByKanbanCardIdAndStatusInOrderByUpdatedAtDescIdDesc(
                card.getId(),
                List.of(ResourceReservationStatus.RESERVED, ResourceReservationStatus.COMMITTED)
        );
    }

    @Transactional(readOnly = true)
    public Optional<TeamResourceReservation> getActiveReservation(TeamKanbanCard card) {
        return teamResourceReservationRepository.findFirstByKanbanCardIdAndStatusOrderByUpdatedAtDescIdDesc(
                card.getId(),
                ResourceReservationStatus.RESERVED
        );
    }

    @Transactional(readOnly = true)
    public Optional<String> getSolutionUnavailableReason(SessionTeam team, KanbanSolutionOption option) {
        return resolveResourceBlocker(team, option.getBudgetCost(), option.getTimeCost(),
                option.getRequiredItemName(), resolveRequiredItemQuantity(option));
    }

    @Transactional
    public TeamResourceReservation reserveResourcesForTask(
            SessionParticipant actor,
            TeamKanbanCard card,
            KanbanSolutionOption option
    ) {
        Optional<TeamResourceReservation> existingReservation = getActiveReservation(card);

        if (existingReservation.isPresent()
                && existingReservation.get().getSolutionOption().getId().equals(option.getId())) {
            return existingReservation.get();
        }

        resolveResourceBlocker(card.getTeam(), option.getBudgetCost(), option.getTimeCost(),
                option.getRequiredItemName(), resolveRequiredItemQuantity(option))
                .ifPresent(reason -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
                });

        existingReservation.ifPresent(reservation -> releaseReservation(actor, reservation, card, "Резерв заменён другим способом решения."));

        TeamResourceReservation reservation = teamResourceReservationRepository.save(new TeamResourceReservation(
                card.getTeam(),
                card,
                option,
                actor
        ));

        teamEconomyEventRepository.save(new TeamEconomyEvent(
                card.getTeam(),
                actor,
                card,
                TeamEconomyEventType.TASK_RESOURCES_RESERVED,
                card.getProblemState().getStageNumber(),
                BigDecimal.ZERO.setScale(2),
                0,
                option.getRequiredItemName(),
                0,
                "Выбран способ '%s': зарезервировано бюджет %.2f и время %d%s."
                        .formatted(
                                option.getTitle(),
                                option.getBudgetCost(),
                                option.getTimeCost(),
                                formatReservedItemSuffix(option.getRequiredItemName(), resolveRequiredItemQuantity(option))
                        )
        ));

        return reservation;
    }

    @Transactional
    public void releaseReservedResources(SessionParticipant actor, TeamKanbanCard card) {
        getActiveReservation(card).ifPresent(reservation -> releaseReservation(
                actor,
                reservation,
                card,
                "Задача возвращена: резерв ресурсов снят."
        ));
    }

    @Transactional
    public void commitReservedResources(SessionParticipant actor, TeamKanbanCard card) {
        if (card.getResourcesSpentAt() != null) {
            return;
        }

        TeamResourceReservation reservation = requireActiveReservationForCommit(card);
        validateReservedResourcesCanBeCommitted(card, reservation);

        TeamEconomyState teamState = teamEconomyStateRepository.findByTeamId(card.getTeam().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Экономика команды не найдена."));

        TeamInventoryItem inventoryItem = null;
        if (hasRequiredItem(reservation.getItemName(), reservation.getItemQuantity())) {
            inventoryItem = teamInventoryItemRepository
                    .findByTeamIdAndItemNameIgnoreCase(card.getTeam().getId(), reservation.getItemName())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Нельзя согласовать задачу: на складе команды нет нужного предмета: %s."
                                    .formatted(reservation.getItemName())
                    ));
            inventoryItem.consume(reservation.getItemQuantity());
        }

        teamState.spendResources(reservation.getBudgetAmount(), reservation.getTimeUnits());
        card.markResourcesSpent();
        reservation.commit();

        teamEconomyEventRepository.save(new TeamEconomyEvent(
                card.getTeam(),
                actor,
                card,
                TeamEconomyEventType.TASK_RESERVATION_COMMITTED,
                card.getProblemState().getStageNumber(),
                reservation.getBudgetAmount().negate(),
                -reservation.getTimeUnits(),
                inventoryItem != null ? inventoryItem.getItemName() : null,
                inventoryItem != null ? -reservation.getItemQuantity() : 0,
                "Финальное согласование: списано бюджет %.2f и время %d%s."
                        .formatted(
                                reservation.getBudgetAmount(),
                                reservation.getTimeUnits(),
                                formatReservedItemSuffix(reservation.getItemName(), reservation.getItemQuantity())
                        )
        ));
    }

    @Transactional(readOnly = true)
    public TeamResourceReservation requireActiveReservationForCommit(TeamKanbanCard card) {
        return getActiveReservation(card)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Перед финальным согласованием нужно выбрать способ решения и создать резерв ресурсов."
                ));
    }

    @Transactional(readOnly = true)
    public void validateReservedResourcesCanBeCommitted(
            TeamKanbanCard card,
            TeamResourceReservation reservation
    ) {
        resolveResourceBlocker(card.getTeam(), reservation.getBudgetAmount(), reservation.getTimeUnits(),
                reservation.getItemName(), reservation.getItemQuantity())
                .ifPresent(reason -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Нельзя согласовать задачу: %s".formatted(reason)
                    );
                });
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

        FinalStageCrisisType crisisType = stageNumber != null && stageNumber == 3
                ? team.getGameSession().getFinalStageCrisisType()
                : null;

        BigDecimal income = roomStates.stream()
                .map(roomState -> roomState.getClinicRoom().getBaseIncome().multiply(
                        resolveRoomIncomeCoefficient(
                                problemStatesByRoomStateId.getOrDefault(roomState.getId(), List.of()),
                                stageNumber,
                                crisisType
                        )
                ))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal basePenalties = problemStates.stream()
                .filter(problemState -> isReleasedByStage(problemState, stageNumber))
                .filter(problemState -> problemState.getStatus() != SessionProblemStatus.RESOLVED)
                .map(problemState -> problemState.getProblemTemplate().getIgnorePenalty())
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        long reputationEscalationCount = countActiveEscalations(problemStates, stageNumber, ProblemEscalationType.REPUTATION_INCIDENT);
        long inspectionEscalationCount = countActiveEscalations(problemStates, stageNumber, ProblemEscalationType.INSPECTION_RISK);

        BigDecimal reputationPenalties = crisisType != null && reputationEscalationCount > 0
                ? income.multiply(crisisType.getReputationPenaltyRate())
                        .multiply(BigDecimal.valueOf(reputationEscalationCount))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal inspectionPenalties = crisisType != null && inspectionEscalationCount > 0
                ? crisisType.getInspectionFineAmount()
                        .multiply(BigDecimal.valueOf(inspectionEscalationCount))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal penalties = basePenalties
                .add(reputationPenalties)
                .add(inspectionPenalties)
                .setScale(2, RoundingMode.HALF_UP);

        List<TeamProblemState> currentStageProblems = problemStates.stream()
                .filter(problemState -> problemState.getStageNumber().equals(stageNumber))
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
                buildStageSettlementMessage(
                        stageNumber,
                        income,
                        basePenalties,
                        reputationPenalties,
                        inspectionPenalties,
                        bonus
                )
        ));
        log.info(
                "Stage settled: sessionCode={}, teamId={}, teamName={}, stageNumber={}, income={}, penalties={}, bonus={}, netAmount={}",
                team.getGameSession().getCode(),
                team.getId(),
                team.getName(),
                stageNumber,
                income,
                penalties,
                bonus,
                income.add(bonus).subtract(penalties).setScale(2, RoundingMode.HALF_UP)
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
        List<TeamResourceReservation> activeReservations = teamResourceReservationRepository
                .findAllByTeamIdAndStatusOrderByCreatedAtAscIdAsc(team.getId(), ResourceReservationStatus.RESERVED);
        BigDecimal reservedBudget = activeReservations.stream()
                .map(TeamResourceReservation::getBudgetAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        int reservedStageTimeUnits = activeReservations.stream()
                .mapToInt(TeamResourceReservation::getTimeUnits)
                .sum();
        List<TeamEconomyReservedItem> reservedItems = activeReservations.stream()
                .filter(reservation -> hasRequiredItem(reservation.getItemName(), reservation.getItemQuantity()))
                .collect(Collectors.groupingBy(
                        TeamResourceReservation::getItemName,
                        LinkedHashMap::new,
                        Collectors.summingInt(TeamResourceReservation::getItemQuantity)
                ))
                .entrySet()
                .stream()
                .map(entry -> new TeamEconomyReservedItem(entry.getKey(), entry.getValue()))
                .toList();

        return new TeamEconomyItem(
                team.getId(),
                team.getName(),
                state.getCurrentBalance(),
                state.getCurrentStageTimeUnits(),
                reservedBudget,
                reservedStageTimeUnits,
                state.getCurrentBalance().subtract(reservedBudget),
                state.getCurrentStageTimeUnits() - reservedStageTimeUnits,
                state.getTotalIncome(),
                state.getTotalExpenses(),
                state.getTotalPenalties(),
                state.getTotalBonuses(),
                reservedItems,
                roomItems,
                teamEconomyEventRepository.findAllByTeamIdAndEventTypeOrderByStageNumberDescCreatedAtDescIdDesc(
                                team.getId(),
                                TeamEconomyEventType.STAGE_SETTLED
                        )
                        .stream()
                        .map(this::toStageEconomySummaryItem)
                        .toList(),
                teamEconomyEventRepository.findRecentForTeam(team.getId(), PageRequest.of(0, 8))
                        .stream()
                        .map(this::toEconomyEventItem)
                        .toList()
        );
    }

    private TeamRoomEconomyItem toRoomItem(TeamRoomState roomState, List<TeamProblemState> problemStates) {
        FinalStageCrisisType crisisType = roomState.getTeam().getGameSession().getFinalStageCrisisType();
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
                        problemState.getStageNumber(),
                        problemState.getProblemTemplate().getTitle(),
                        problemState.getProblemTemplate().getSeverity().name(),
                        problemState.getProblemTemplate().getBudgetCost(),
                        problemState.getProblemTemplate().getTimeCost(),
                        problemState.getProblemTemplate().getRequiredItemName(),
                        problemState.getProblemTemplate().getRequiredItemQuantity(),
                        problemState.getProblemTemplate().getIgnorePenalty(),
                        problemState.getProblemTemplate().getSeverity().getPenaltyWeight(),
                        problemState.hasActiveEscalation(),
                        problemState.hasActiveEscalation() ? problemState.getEscalationType().name() : null,
                        problemState.hasActiveEscalation() ? problemState.getEscalationType().getTitle() : null,
                        problemState.hasActiveEscalation() ? problemState.getEscalationType().getDescription() : null,
                        problemState.hasActiveEscalation()
                                ? problemState.getEscalationType().getPenaltyHint(crisisType)
                                : null,
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

    private BigDecimal resolveRoomIncomeCoefficient(
            List<TeamProblemState> problemStates,
            Integer stageNumber,
            FinalStageCrisisType crisisType
    ) {
        BigDecimal baseCoefficient = resolveRoomStateCoefficient(problemStates, stageNumber);

        if (stageNumber == null || stageNumber != 3 || crisisType == null) {
            return baseCoefficient;
        }

        boolean operationsEscalationActive = problemStates.stream()
                .filter(problemState -> isReleasedByStage(problemState, stageNumber))
                .filter(problemState -> problemState.getStatus() != SessionProblemStatus.RESOLVED)
                .anyMatch(problemState -> problemState.hasActiveEscalation()
                        && problemState.getEscalationType() == ProblemEscalationType.OPERATIONS_DISRUPTION);

        if (!operationsEscalationActive) {
            return baseCoefficient;
        }

        BigDecimal operationsFactor = crisisType.getOperationsIncomeFactor();
        return baseCoefficient.compareTo(operationsFactor) < 0 ? baseCoefficient : operationsFactor;
    }

    private boolean isReleasedByStage(TeamProblemState problemState, Integer stageNumber) {
        Integer problemStageNumber = problemState.getStageNumber();
        return stageNumber == null || problemStageNumber == null || problemStageNumber <= stageNumber;
    }

    private boolean isBacklogProblemForFinalStage(TeamProblemState problemState) {
        return problemState.getStageNumber() != null
                && problemState.getStageNumber() < 3
                && problemState.getStatus() != SessionProblemStatus.RESOLVED;
    }

    private FinalStageCrisisType resolveFinalStageCrisisType(List<TeamProblemState> problemStates) {
        if (problemStates.isEmpty()) {
            return FinalStageCrisisType.PEAK_LOAD;
        }

        Map<FinalStageCrisisType, Integer> weights = new EnumMap<>(FinalStageCrisisType.class);
        for (FinalStageCrisisType crisisType : FinalStageCrisisType.values()) {
            weights.put(crisisType, 0);
        }

        for (TeamProblemState problemState : problemStates) {
            ProblemEscalationType escalationType = classifyEscalationType(problemState);
            FinalStageCrisisType crisisType = escalationType.toFinalStageCrisisType();
            weights.computeIfPresent(crisisType, (key, currentWeight) -> currentWeight + buildEscalationScore(problemState));
        }

        FinalStageCrisisType dominantCrisisType = FinalStageCrisisType.REPUTATIONAL_PRESSURE;
        int dominantWeight = -1;

        for (FinalStageCrisisType crisisType : FinalStageCrisisType.values()) {
            int currentWeight = weights.getOrDefault(crisisType, 0);
            if (currentWeight > dominantWeight) {
                dominantWeight = currentWeight;
                dominantCrisisType = crisisType;
            }
        }

        return dominantCrisisType;
    }

    private int buildEscalationScore(TeamProblemState problemState) {
        int severityWeight = switch (problemState.getProblemTemplate().getSeverity()) {
            case CRITICAL -> 300;
            case SERIOUS -> 200;
            case MINOR -> 100;
        };

        int stageBacklogWeight = problemState.getStageNumber() != null && problemState.getStageNumber() == 1 ? 40 : 0;
        int ignorePenaltyWeight = problemState.getProblemTemplate().getIgnorePenalty()
                .movePointRight(2)
                .intValue();

        return severityWeight + stageBacklogWeight + ignorePenaltyWeight;
    }

    private ProblemEscalationType classifyEscalationType(TeamProblemState problemState) {
        String normalizedTitle = problemState.getProblemTemplate().getTitle().toLowerCase(Locale.ROOT);

        if (containsAny(
                normalizedTitle,
                "мыш",
                "крыса",
                "мыло",
                "туалетная бумага",
                "антисептик",
                "простын",
                "ведр",
                "хлам",
                "личные вещи",
                "санитар"
        )) {
            return ProblemEscalationType.REPUTATION_INCIDENT;
        }

        if (containsAny(
                normalizedTitle,
                "трещ",
                "искр",
                "развод",
                "протека",
                "кран",
                "пожар",
                "рециркулятор",
                "проверк",
                "рольстав",
                "электронн",
                "уф"
        )) {
            return ProblemEscalationType.INSPECTION_RISK;
        }

        return ProblemEscalationType.OPERATIONS_DISRUPTION;
    }

    private boolean containsAny(String source, String... fragments) {
        for (String fragment : fragments) {
            if (source.contains(fragment)) {
                return true;
            }
        }

        return false;
    }

    private long countActiveEscalations(
            List<TeamProblemState> problemStates,
            Integer stageNumber,
            ProblemEscalationType escalationType
    ) {
        return problemStates.stream()
                .filter(problemState -> isReleasedByStage(problemState, stageNumber))
                .filter(problemState -> problemState.getStatus() != SessionProblemStatus.RESOLVED)
                .filter(TeamProblemState::hasActiveEscalation)
                .filter(problemState -> problemState.getEscalationType() == escalationType)
                .count();
    }

    private String buildStageSettlementMessage(
            Integer stageNumber,
            BigDecimal income,
            BigDecimal basePenalties,
            BigDecimal reputationPenalties,
            BigDecimal inspectionPenalties,
            BigDecimal bonus
    ) {
        if ((reputationPenalties.compareTo(BigDecimal.ZERO) == 0 && inspectionPenalties.compareTo(BigDecimal.ZERO) == 0)
                || stageNumber == null
                || stageNumber != 3) {
            return "Итог этапа %d: доход %.2f, штрафы %.2f, бонус %.2f."
                    .formatted(stageNumber, income, basePenalties, bonus);
        }

        return "Итог этапа %d: доход %.2f, базовые штрафы %.2f, кризисные штрафы %.2f, бонус %.2f."
                .formatted(
                        stageNumber,
                        income,
                        basePenalties,
                        reputationPenalties.add(inspectionPenalties).setScale(2, RoundingMode.HALF_UP),
                        bonus
                );
    }

    private void validateProblemDistribution(List<Integer> stageProblemCounts) {
        if (stageProblemCounts == null || stageProblemCounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите распределение задач по этапам.");
        }

        boolean hasNegativeCount = stageProblemCounts.stream()
                .anyMatch(problemCount -> problemCount == null || problemCount < 0);

        if (hasNegativeCount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Количество задач на этапе не может быть отрицательным.");
        }
    }

    private Optional<String> resolveResourceBlocker(
            SessionTeam team,
            BigDecimal budgetCost,
            int timeCost,
            String requiredItemName,
            int requiredItemQuantity
    ) {
        TeamEconomyState teamState = teamEconomyStateRepository.findByTeamId(team.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Экономика команды не найдена."));

        if (teamState.getCurrentBalance().compareTo(budgetCost) < 0) {
            return Optional.of("у команды недостаточно бюджета для выбранного решения.");
        }

        if (teamState.getCurrentStageTimeUnits() < timeCost) {
            return Optional.of("у команды недостаточно времени этапа для выбранного решения.");
        }

        if (hasRequiredItem(requiredItemName, requiredItemQuantity)) {
            Optional<TeamInventoryItem> inventoryItem = teamInventoryItemRepository
                    .findByTeamIdAndItemNameIgnoreCase(team.getId(), requiredItemName);

            if (inventoryItem.isEmpty()) {
                return Optional.of("на складе команды нет нужного предмета: %s.".formatted(requiredItemName));
            }

            if (inventoryItem.get().getQuantity() < requiredItemQuantity) {
                return Optional.of("недостаточно предметов '%s' на складе команды.".formatted(requiredItemName));
            }
        }

        return Optional.empty();
    }

    private void releaseReservation(
            SessionParticipant actor,
            TeamResourceReservation reservation,
            TeamKanbanCard card,
            String message
    ) {
        reservation.release();
        teamEconomyEventRepository.save(new TeamEconomyEvent(
                card.getTeam(),
                actor,
                card,
                TeamEconomyEventType.TASK_RESERVATION_RELEASED,
                card.getProblemState().getStageNumber(),
                BigDecimal.ZERO.setScale(2),
                0,
                reservation.getItemName(),
                0,
                message
        ));
    }

    private boolean hasRequiredItem(String requiredItemName, Integer requiredItemQuantity) {
        return requiredItemName != null
                && !requiredItemName.isBlank()
                && requiredItemQuantity != null
                && requiredItemQuantity > 0;
    }

    private int resolveRequiredItemQuantity(KanbanSolutionOption option) {
        return option.getRequiredItemQuantity() != null ? option.getRequiredItemQuantity() : 0;
    }

    private String formatReservedItemSuffix(String itemName, Integer quantity) {
        if (!hasRequiredItem(itemName, quantity)) {
            return "";
        }

        return ", предмет %s %d шт.".formatted(itemName, quantity);
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

    private TeamStageEconomySummaryItem toStageEconomySummaryItem(TeamEconomyEvent event) {
        return new TeamStageEconomySummaryItem(
                event.getStageNumber(),
                event.getAmountDelta(),
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
