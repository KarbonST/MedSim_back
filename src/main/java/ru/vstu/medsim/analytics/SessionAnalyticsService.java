package ru.vstu.medsim.analytics;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.analytics.dto.GameSessionAnalyticsResponse;
import ru.vstu.medsim.analytics.dto.SessionAnalyticsStageItem;
import ru.vstu.medsim.analytics.dto.TeamAnalyticsCardItem;
import ru.vstu.medsim.analytics.dto.TeamAnalyticsItem;
import ru.vstu.medsim.analytics.dto.TeamAnalyticsStageItem;
import ru.vstu.medsim.analytics.dto.TeamParticipantAnalyticsItem;
import ru.vstu.medsim.economy.domain.ResourceReservationStatus;
import ru.vstu.medsim.economy.domain.TeamEconomyEvent;
import ru.vstu.medsim.economy.domain.TeamEconomyEventType;
import ru.vstu.medsim.economy.domain.TeamEconomyState;
import ru.vstu.medsim.economy.domain.TeamProblemState;
import ru.vstu.medsim.economy.domain.TeamResourceReservation;
import ru.vstu.medsim.economy.repository.TeamEconomyEventRepository;
import ru.vstu.medsim.economy.repository.TeamEconomyStateRepository;
import ru.vstu.medsim.economy.repository.TeamResourceReservationRepository;
import ru.vstu.medsim.kanban.domain.KanbanCardEventType;
import ru.vstu.medsim.kanban.domain.KanbanCardStatus;
import ru.vstu.medsim.kanban.domain.TeamKanbanCard;
import ru.vstu.medsim.kanban.domain.TeamKanbanCardEvent;
import ru.vstu.medsim.kanban.repository.TeamKanbanCardEventRepository;
import ru.vstu.medsim.kanban.repository.TeamKanbanCardRepository;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.GameSessionStatus;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.player.repository.SessionParticipantRepository;
import ru.vstu.medsim.session.GameSessionQueryService;
import ru.vstu.medsim.session.domain.SessionStageSetting;
import ru.vstu.medsim.session.domain.SessionTeam;
import ru.vstu.medsim.session.repository.SessionStageSettingRepository;
import ru.vstu.medsim.session.repository.SessionTeamRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SessionAnalyticsService {

    private static final List<String> ROLE_ORDER = List.of(
            "Главный врач",
            "Главная медсестра",
            "Главный инженер",
            "Старшая медсестра",
            "Старший инженер",
            "Медсестра",
            "Инженер"
    );

    private final GameSessionQueryService gameSessionQueryService;
    private final SessionTeamRepository sessionTeamRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final SessionStageSettingRepository sessionStageSettingRepository;
    private final TeamEconomyStateRepository teamEconomyStateRepository;
    private final TeamEconomyEventRepository teamEconomyEventRepository;
    private final TeamResourceReservationRepository teamResourceReservationRepository;
    private final TeamKanbanCardRepository teamKanbanCardRepository;
    private final TeamKanbanCardEventRepository teamKanbanCardEventRepository;

    public SessionAnalyticsService(
            GameSessionQueryService gameSessionQueryService,
            SessionTeamRepository sessionTeamRepository,
            SessionParticipantRepository sessionParticipantRepository,
            SessionStageSettingRepository sessionStageSettingRepository,
            TeamEconomyStateRepository teamEconomyStateRepository,
            TeamEconomyEventRepository teamEconomyEventRepository,
            TeamResourceReservationRepository teamResourceReservationRepository,
            TeamKanbanCardRepository teamKanbanCardRepository,
            TeamKanbanCardEventRepository teamKanbanCardEventRepository
    ) {
        this.gameSessionQueryService = gameSessionQueryService;
        this.sessionTeamRepository = sessionTeamRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
        this.sessionStageSettingRepository = sessionStageSettingRepository;
        this.teamEconomyStateRepository = teamEconomyStateRepository;
        this.teamEconomyEventRepository = teamEconomyEventRepository;
        this.teamResourceReservationRepository = teamResourceReservationRepository;
        this.teamKanbanCardRepository = teamKanbanCardRepository;
        this.teamKanbanCardEventRepository = teamKanbanCardEventRepository;
    }

    @Transactional(readOnly = true)
    public GameSessionAnalyticsResponse getSessionAnalytics(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);
        ensureFinished(session);

        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());
        List<SessionParticipant> participants = sessionParticipantRepository.findAllByGameSessionIdOrderByJoinedAtAscIdAsc(session.getId());
        List<SessionStageSetting> stageSettings = sessionStageSettingRepository.findAllByGameSessionIdOrderByStageNumberAsc(session.getId());
        List<TeamEconomyState> economyStates = teamEconomyStateRepository.findAllByTeamGameSessionIdOrderByTeamSortOrderAscIdAsc(session.getId());
        List<TeamResourceReservation> activeReservations = teamResourceReservationRepository
                .findAllByTeamGameSessionIdAndStatusOrderByTeamSortOrderAscCreatedAtAscIdAsc(
                        session.getId(),
                        ResourceReservationStatus.RESERVED
                );
        List<TeamKanbanCard> cards = teamKanbanCardRepository.findAllByGameSessionId(session.getId());
        List<TeamKanbanCardEvent> cardEvents = teamKanbanCardEventRepository.findAllByGameSessionId(session.getId());
        List<TeamEconomyEvent> economyEvents = teamEconomyEventRepository.findAllByGameSessionId(session.getId());

        Map<Long, TeamEconomyState> economyStatesByTeamId = economyStates.stream()
                .collect(Collectors.toMap(state -> state.getTeam().getId(), Function.identity()));
        Map<Long, BigDecimal> reservedBudgetByTeamId = activeReservations.stream()
                .collect(Collectors.groupingBy(
                        reservation -> reservation.getTeam().getId(),
                        Collectors.mapping(TeamResourceReservation::getBudgetAmount, Collectors.reducing(zeroAmount(), BigDecimal::add))
                ));
        Map<Long, TeamAggregate> teamAggregates = buildTeamAggregates(
                teams,
                participants,
                stageSettings,
                economyStatesByTeamId,
                reservedBudgetByTeamId
        );

        for (TeamEconomyEvent event : economyEvents) {
            TeamAggregate aggregate = teamAggregates.get(event.getTeam().getId());
            if (aggregate == null || event.getStageNumber() == null) {
                continue;
            }

            if (event.getEventType() == TeamEconomyEventType.STAGE_SETTLED) {
                aggregate.stage(event.getStageNumber()).addNetAmount(event.getAmountDelta());
            }
        }

        Map<Long, List<TeamKanbanCardEvent>> eventsByCardId = cardEvents.stream()
                .collect(Collectors.groupingBy(
                        event -> event.getCard().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (TeamKanbanCard card : cards) {
            TeamAggregate aggregate = teamAggregates.get(card.getTeam().getId());
            if (aggregate == null) {
                continue;
            }

            CardComputation computation = buildCardComputation(card, eventsByCardId.getOrDefault(card.getId(), List.of()));
            aggregate.registerCard(computation);
        }

        for (TeamKanbanCardEvent event : cardEvents) {
            TeamAggregate aggregate = teamAggregates.get(event.getCard().getTeam().getId());
            if (aggregate == null) {
                continue;
            }

            switch (event.getEventType()) {
                case EXECUTOR_ASSIGNED -> aggregate.markAssigned(event.getTargetParticipant(), event.getCard().getId());
                case WORK_STARTED -> aggregate.markWorkStarted(event.getActor(), event.getCard().getId());
                case SENT_TO_DEPARTMENT_REVIEW -> aggregate.markSentToReview(event.getActor(), event.getCard().getId());
                case DEPARTMENT_APPROVED -> aggregate.markDepartmentApproved(event.getActor(), event.getCard().getId());
                case CHIEF_DOCTOR_APPROVED -> aggregate.markChiefApproved(event.getActor(), event.getCard().getId());
                case RETURNED_TO_STAGE, SOLUTION_FAILED -> aggregate.markReturned(event.getActor(), event.getCard().getId());
                case TASK_HELD -> aggregate.markHeld(event.getActor(), event.getCard().getId());
                default -> {
                }
            }
        }

        List<TeamAggregate> rankedAggregates = new ArrayList<>(teamAggregates.values());
        rankedAggregates.sort(teamRankingComparator());

        Map<Long, Integer> rankByTeamId = new HashMap<>();
        for (int index = 0; index < rankedAggregates.size(); index += 1) {
            rankByTeamId.put(rankedAggregates.get(index).teamId(), index + 1);
        }

        List<TeamAnalyticsItem> teamItems = rankedAggregates.stream()
                .map(aggregate -> aggregate.toItem(rankByTeamId.getOrDefault(aggregate.teamId(), rankedAggregates.size())))
                .toList();

        List<SessionAnalyticsStageItem> sessionStageItems = stageSettings.stream()
                .map(stage -> buildSessionStageItem(stage, rankedAggregates))
                .toList();

        return new GameSessionAnalyticsResponse(
                session.getId(),
                session.getCode(),
                session.getName(),
                session.getStatus().name(),
                session.getFinalStageCrisisType() != null ? session.getFinalStageCrisisType().name() : null,
                session.getStartedAt(),
                session.getFinishedAt(),
                teams.size(),
                participants.size(),
                teamItems.stream().mapToInt(TeamAnalyticsItem::totalProblemCount).sum(),
                teamItems.stream().mapToInt(TeamAnalyticsItem::resolvedProblemCount).sum(),
                teamItems.stream().mapToInt(TeamAnalyticsItem::unresolvedProblemCount).sum(),
                teamItems.stream().mapToInt(TeamAnalyticsItem::returnCount).sum(),
                teamItems.stream().mapToInt(TeamAnalyticsItem::holdCount).sum(),
                teamItems.stream().mapToInt(TeamAnalyticsItem::escalatedProblemCount).sum(),
                sessionStageItems,
                teamItems
        );
    }

    private Map<Long, TeamAggregate> buildTeamAggregates(
            List<SessionTeam> teams,
            List<SessionParticipant> participants,
            List<SessionStageSetting> stageSettings,
            Map<Long, TeamEconomyState> economyStatesByTeamId,
            Map<Long, BigDecimal> reservedBudgetByTeamId
    ) {
        Map<Long, List<SessionParticipant>> participantsByTeamId = participants.stream()
                .filter(participant -> participant.getTeam() != null)
                .collect(Collectors.groupingBy(participant -> participant.getTeam().getId(), LinkedHashMap::new, Collectors.toList()));

        Map<Long, TeamAggregate> aggregates = new LinkedHashMap<>();
        for (SessionTeam team : teams) {
            aggregates.put(
                    team.getId(),
                    new TeamAggregate(
                            team,
                            economyStatesByTeamId.get(team.getId()),
                            reservedBudgetByTeamId.getOrDefault(team.getId(), zeroAmount()),
                            stageSettings,
                            participantsByTeamId.getOrDefault(team.getId(), List.of())
                    )
            );
        }
        return aggregates;
    }

    private CardComputation buildCardComputation(TeamKanbanCard card, List<TeamKanbanCardEvent> events) {
        LocalDateTime distributionStartedAt = null;
        LocalDateTime reactionStartedAt = null;
        LocalDateTime workStartedAt = null;
        LocalDateTime departmentReviewStartedAt = null;
        LocalDateTime chiefReviewStartedAt = null;

        long distributionSeconds = 0L;
        long reactionSeconds = 0L;
        long workSeconds = 0L;
        long departmentReviewSeconds = 0L;
        long chiefReviewSeconds = 0L;

        int distributionSegments = 0;
        int reactionSegments = 0;
        int workSegments = 0;
        int departmentReviewSegments = 0;
        int chiefReviewSegments = 0;

        int returnCount = 0;
        int holdCount = 0;

        for (TeamKanbanCardEvent event : events) {
            switch (event.getEventType()) {
                case DEPARTMENT_ASSIGNED -> distributionStartedAt = event.getCreatedAt();
                case EXECUTOR_ASSIGNED -> {
                    if (distributionStartedAt != null) {
                        distributionSeconds += secondsBetween(distributionStartedAt, event.getCreatedAt());
                        distributionSegments += 1;
                        distributionStartedAt = null;
                    }
                    reactionStartedAt = event.getCreatedAt();
                }
                case WORK_STARTED -> {
                    if (reactionStartedAt != null) {
                        reactionSeconds += secondsBetween(reactionStartedAt, event.getCreatedAt());
                        reactionSegments += 1;
                        reactionStartedAt = null;
                    }
                    workStartedAt = event.getCreatedAt();
                }
                case SENT_TO_DEPARTMENT_REVIEW -> {
                    if (workStartedAt != null) {
                        workSeconds += secondsBetween(workStartedAt, event.getCreatedAt());
                        workSegments += 1;
                        workStartedAt = null;
                    }
                    departmentReviewStartedAt = event.getCreatedAt();
                }
                case DEPARTMENT_APPROVED -> {
                    if (departmentReviewStartedAt != null) {
                        departmentReviewSeconds += secondsBetween(departmentReviewStartedAt, event.getCreatedAt());
                        departmentReviewSegments += 1;
                        departmentReviewStartedAt = null;
                    }
                    chiefReviewStartedAt = event.getCreatedAt();
                }
                case CHIEF_DOCTOR_APPROVED -> {
                    if (chiefReviewStartedAt != null) {
                        chiefReviewSeconds += secondsBetween(chiefReviewStartedAt, event.getCreatedAt());
                        chiefReviewSegments += 1;
                        chiefReviewStartedAt = null;
                    }
                }
                case RETURNED_TO_STAGE -> {
                    returnCount += 1;
                    if (event.getFromStatus() == KanbanCardStatus.DEPARTMENT_REVIEW && departmentReviewStartedAt != null) {
                        departmentReviewSeconds += secondsBetween(departmentReviewStartedAt, event.getCreatedAt());
                        departmentReviewSegments += 1;
                        departmentReviewStartedAt = null;
                    }
                    if (event.getFromStatus() == KanbanCardStatus.CHIEF_DOCTOR_REVIEW && chiefReviewStartedAt != null) {
                        chiefReviewSeconds += secondsBetween(chiefReviewStartedAt, event.getCreatedAt());
                        chiefReviewSegments += 1;
                        chiefReviewStartedAt = null;
                    }
                    distributionStartedAt = null;
                    reactionStartedAt = null;
                    workStartedAt = null;
                }
                case SOLUTION_FAILED -> {
                    returnCount += 1;
                    if (chiefReviewStartedAt != null) {
                        chiefReviewSeconds += secondsBetween(chiefReviewStartedAt, event.getCreatedAt());
                        chiefReviewSegments += 1;
                        chiefReviewStartedAt = null;
                    }
                }
                case TASK_HELD -> {
                    holdCount += 1;
                    distributionStartedAt = null;
                    reactionStartedAt = null;
                    workStartedAt = null;
                    departmentReviewStartedAt = null;
                    chiefReviewStartedAt = null;
                }
                default -> {
                }
            }
        }

        Long distributionValue = distributionSegments > 0 ? distributionSeconds : null;
        Long reactionValue = reactionSegments > 0 ? reactionSeconds : null;
        Long workValue = workSegments > 0 ? workSeconds : null;
        Long departmentReviewValue = departmentReviewSegments > 0 ? departmentReviewSeconds : null;
        Long chiefReviewValue = chiefReviewSegments > 0 ? chiefReviewSeconds : null;

        long fullCycleSeconds = 0L;
        int fullCycleSegments = 0;
        for (Long metric : List.of(distributionValue, reactionValue, workValue, departmentReviewValue, chiefReviewValue)) {
            if (metric != null) {
                fullCycleSeconds += metric;
                fullCycleSegments += 1;
            }
        }
        Long fullCycleValue = fullCycleSegments > 0 ? fullCycleSeconds : null;

        TeamProblemState problemState = card.getProblemState();
        TeamAnalyticsCardItem cardItem = new TeamAnalyticsCardItem(
                card.getId(),
                problemState.getId(),
                problemState.getProblemTemplate().getProblemNumber(),
                problemState.getProblemTemplate().getTitle(),
                problemState.getTeamRoomState().getClinicRoom().getCode(),
                problemState.getTeamRoomState().getClinicRoom().getName(),
                problemState.getStageNumber(),
                card.getStatus().name(),
                card.getPriority() != null ? card.getPriority().name() : null,
                card.getResponsibleDepartment() != null ? card.getResponsibleDepartment().name() : null,
                card.getAssignee() != null ? card.getAssignee().getPlayer().getDisplayName() : null,
                problemState.getStatus().name().equals("RESOLVED"),
                problemState.getEscalationType() != null,
                returnCount,
                holdCount,
                distributionValue,
                reactionValue,
                workValue,
                departmentReviewValue,
                chiefReviewValue,
                fullCycleValue
        );

        return new CardComputation(card, cardItem, distributionValue, reactionValue, workValue, departmentReviewValue, chiefReviewValue, fullCycleValue);
    }

    private SessionAnalyticsStageItem buildSessionStageItem(
            SessionStageSetting stageSetting,
            List<TeamAggregate> teamAggregates
    ) {
        int totalProblems = 0;
        int resolvedProblems = 0;
        int unresolvedProblems = 0;
        int returnCount = 0;
        int holdCount = 0;
        int escalatedProblemCount = 0;
        int activeEscalationCount = 0;
        BigDecimal netAmount = zeroAmount();

        for (TeamAggregate aggregate : teamAggregates) {
            StageAggregate stage = aggregate.stage(stageSetting.getStageNumber());
            totalProblems += stage.totalProblemCount;
            resolvedProblems += stage.resolvedProblemCount;
            unresolvedProblems += stage.unresolvedProblemCount;
            returnCount += stage.returnCount;
            holdCount += stage.holdCount;
            escalatedProblemCount += stage.escalatedProblemCount;
            activeEscalationCount += stage.activeEscalationCount;
            netAmount = netAmount.add(stage.netAmount);
        }

        return new SessionAnalyticsStageItem(
                stageSetting.getStageNumber(),
                stageSetting.getDurationMinutes(),
                stageSetting.getInteractionMode().name(),
                totalProblems,
                resolvedProblems,
                unresolvedProblems,
                returnCount,
                holdCount,
                escalatedProblemCount,
                activeEscalationCount,
                netAmount
        );
    }

    private Comparator<TeamAggregate> teamRankingComparator() {
        return Comparator
                .comparing(TeamAggregate::currentBalanceOrZero, Comparator.reverseOrder())
                .thenComparing(TeamAggregate::resolvedProblemCount, Comparator.reverseOrder())
                .thenComparing(TeamAggregate::returnCount)
                .thenComparing(TeamAggregate::holdCount)
                .thenComparing(TeamAggregate::sortOrder);
    }

    private void ensureFinished(GameSession session) {
        if (session.getStatus() != GameSessionStatus.FINISHED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Послеигровая аналитика станет доступна после завершения сессии."
            );
        }
    }

    private static long secondsBetween(LocalDateTime startedAt, LocalDateTime endedAt) {
        return Math.max(Duration.between(startedAt, endedAt).getSeconds(), 0L);
    }

    private static BigDecimal zeroAmount() {
        return BigDecimal.ZERO.setScale(2);
    }

    private static int roleOrder(String role) {
        if (role == null || role.isBlank()) {
            return ROLE_ORDER.size() + 1;
        }
        int index = ROLE_ORDER.indexOf(role);
        return index >= 0 ? index : ROLE_ORDER.size();
    }

    private record CardComputation(
            TeamKanbanCard card,
            TeamAnalyticsCardItem item,
            Long distributionSeconds,
            Long reactionSeconds,
            Long workSeconds,
            Long departmentReviewSeconds,
            Long chiefReviewSeconds,
            Long fullCycleSeconds
    ) {
    }

    private static final class DurationAggregate {
        private long totalSeconds;
        private int sampleCount;

        void add(Long value) {
            if (value == null) {
                return;
            }
            totalSeconds += value;
            sampleCount += 1;
        }

        Long average() {
            if (sampleCount == 0) {
                return null;
            }
            return Math.round((double) totalSeconds / sampleCount);
        }
    }

    private static final class ParticipantAggregate {
        private final SessionParticipant participant;
        private final Set<Long> assignedCards = new HashSet<>();
        private final Set<Long> startedCards = new HashSet<>();
        private final Set<Long> reviewCards = new HashSet<>();
        private final Set<Long> closedExecutorCards = new HashSet<>();
        private final Set<Long> departmentApprovalCards = new HashSet<>();
        private final Set<Long> chiefApprovalCards = new HashSet<>();
        private final Set<Long> returnedCards = new HashSet<>();
        private final Set<Long> heldCards = new HashSet<>();

        ParticipantAggregate(SessionParticipant participant) {
            this.participant = participant;
        }

        TeamParticipantAnalyticsItem toItem() {
            return new TeamParticipantAnalyticsItem(
                    participant.getId(),
                    participant.getPlayer().getDisplayName(),
                    participant.getGameRole(),
                    assignedCards.size(),
                    startedCards.size(),
                    reviewCards.size(),
                    closedExecutorCards.size(),
                    departmentApprovalCards.size(),
                    chiefApprovalCards.size(),
                    returnedCards.size(),
                    heldCards.size()
            );
        }
    }

    private static final class StageAggregate {
        private final SessionStageSetting stageSetting;
        private int totalProblemCount;
        private int resolvedProblemCount;
        private int unresolvedProblemCount;
        private int returnCount;
        private int holdCount;
        private int escalatedProblemCount;
        private int activeEscalationCount;
        private BigDecimal netAmount = zeroAmount();

        StageAggregate(SessionStageSetting stageSetting) {
            this.stageSetting = stageSetting;
        }

        void addCard(TeamAnalyticsCardItem cardItem) {
            totalProblemCount += 1;
            if (Boolean.TRUE.equals(cardItem.resolved())) {
                resolvedProblemCount += 1;
            } else {
                unresolvedProblemCount += 1;
            }
            returnCount += cardItem.returnCount();
            holdCount += cardItem.holdCount();
            if (Boolean.TRUE.equals(cardItem.escalated())) {
                escalatedProblemCount += 1;
            }
        }

        void addProblemState(TeamProblemState problemState) {
            if (problemState.hasActiveEscalation()) {
                activeEscalationCount += 1;
            }
        }

        void addNetAmount(BigDecimal amount) {
            if (amount != null) {
                netAmount = netAmount.add(amount);
            }
        }

        TeamAnalyticsStageItem toItem() {
            return new TeamAnalyticsStageItem(
                    stageSetting.getStageNumber(),
                    stageSetting.getDurationMinutes(),
                    stageSetting.getInteractionMode().name(),
                    totalProblemCount,
                    resolvedProblemCount,
                    unresolvedProblemCount,
                    returnCount,
                    holdCount,
                    escalatedProblemCount,
                    activeEscalationCount,
                    netAmount
            );
        }
    }

    private static final class TeamAggregate {
        private final SessionTeam team;
        private final TeamEconomyState economyState;
        private final BigDecimal reservedBudget;
        private final Map<Integer, StageAggregate> stageAggregates;
        private final Map<Long, ParticipantAggregate> participantAggregates;
        private final List<TeamAnalyticsCardItem> cards = new ArrayList<>();
        private final DurationAggregate distributionAggregate = new DurationAggregate();
        private final DurationAggregate reactionAggregate = new DurationAggregate();
        private final DurationAggregate workAggregate = new DurationAggregate();
        private final DurationAggregate departmentReviewAggregate = new DurationAggregate();
        private final DurationAggregate chiefReviewAggregate = new DurationAggregate();
        private final DurationAggregate fullCycleAggregate = new DurationAggregate();

        private int totalProblemCount;
        private int resolvedProblemCount;
        private int unresolvedProblemCount;
        private int returnCount;
        private int holdCount;
        private int escalatedProblemCount;
        private int activeEscalationCount;

        TeamAggregate(
                SessionTeam team,
                TeamEconomyState economyState,
                BigDecimal reservedBudget,
                List<SessionStageSetting> stageSettings,
                List<SessionParticipant> participants
        ) {
            this.team = team;
            this.economyState = economyState;
            this.reservedBudget = reservedBudget;
            this.stageAggregates = stageSettings.stream()
                    .collect(Collectors.toMap(
                            SessionStageSetting::getStageNumber,
                            StageAggregate::new,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            this.participantAggregates = participants.stream()
                    .collect(Collectors.toMap(
                            SessionParticipant::getId,
                            ParticipantAggregate::new,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
        }

        Long teamId() {
            return team.getId();
        }

        Integer sortOrder() {
            return team.getSortOrder();
        }

        int resolvedProblemCount() {
            return resolvedProblemCount;
        }

        int returnCount() {
            return returnCount;
        }

        int holdCount() {
            return holdCount;
        }

        BigDecimal currentBalanceOrZero() {
            return economyState != null && economyState.getCurrentBalance() != null
                    ? economyState.getCurrentBalance()
                    : zeroAmount();
        }

        StageAggregate stage(int stageNumber) {
            return stageAggregates.computeIfAbsent(
                    stageNumber,
                    ignored -> new StageAggregate(new SessionStageSetting(team.getGameSession(), stageNumber, 0, null))
            );
        }

        void registerCard(CardComputation computation) {
            TeamProblemState problemState = computation.card().getProblemState();
            StageAggregate stageAggregate = stage(problemState.getStageNumber());
            stageAggregate.addCard(computation.item());
            stageAggregate.addProblemState(problemState);

            totalProblemCount += 1;
            if (Boolean.TRUE.equals(computation.item().resolved())) {
                resolvedProblemCount += 1;
            } else {
                unresolvedProblemCount += 1;
            }
            if (Boolean.TRUE.equals(computation.item().escalated())) {
                escalatedProblemCount += 1;
            }
            if (problemState.hasActiveEscalation()) {
                activeEscalationCount += 1;
            }
            returnCount += computation.item().returnCount();
            holdCount += computation.item().holdCount();

            distributionAggregate.add(computation.distributionSeconds());
            reactionAggregate.add(computation.reactionSeconds());
            workAggregate.add(computation.workSeconds());
            departmentReviewAggregate.add(computation.departmentReviewSeconds());
            chiefReviewAggregate.add(computation.chiefReviewSeconds());
            if (Boolean.TRUE.equals(computation.item().resolved())) {
                fullCycleAggregate.add(computation.fullCycleSeconds());
            }

            cards.add(computation.item());

            if (Boolean.TRUE.equals(computation.item().resolved()) && computation.card().getAssignee() != null) {
                ParticipantAggregate aggregate = participantAggregates.get(computation.card().getAssignee().getId());
                if (aggregate != null) {
                    aggregate.closedExecutorCards.add(computation.card().getId());
                }
            }
        }

        void markAssigned(SessionParticipant participant, Long cardId) {
            if (participant == null) {
                return;
            }
            ParticipantAggregate aggregate = participantAggregates.get(participant.getId());
            if (aggregate != null) {
                aggregate.assignedCards.add(cardId);
            }
        }

        void markWorkStarted(SessionParticipant participant, Long cardId) {
            if (participant == null) {
                return;
            }
            ParticipantAggregate aggregate = participantAggregates.get(participant.getId());
            if (aggregate != null) {
                aggregate.startedCards.add(cardId);
            }
        }

        void markSentToReview(SessionParticipant participant, Long cardId) {
            if (participant == null) {
                return;
            }
            ParticipantAggregate aggregate = participantAggregates.get(participant.getId());
            if (aggregate != null) {
                aggregate.reviewCards.add(cardId);
            }
        }

        void markDepartmentApproved(SessionParticipant participant, Long cardId) {
            if (participant == null) {
                return;
            }
            ParticipantAggregate aggregate = participantAggregates.get(participant.getId());
            if (aggregate != null) {
                aggregate.departmentApprovalCards.add(cardId);
            }
        }

        void markChiefApproved(SessionParticipant participant, Long cardId) {
            if (participant == null) {
                return;
            }
            ParticipantAggregate aggregate = participantAggregates.get(participant.getId());
            if (aggregate != null) {
                aggregate.chiefApprovalCards.add(cardId);
            }
        }

        void markReturned(SessionParticipant participant, Long cardId) {
            if (participant == null) {
                return;
            }
            ParticipantAggregate aggregate = participantAggregates.get(participant.getId());
            if (aggregate != null) {
                aggregate.returnedCards.add(cardId);
            }
        }

        void markHeld(SessionParticipant participant, Long cardId) {
            if (participant == null) {
                return;
            }
            ParticipantAggregate aggregate = participantAggregates.get(participant.getId());
            if (aggregate != null) {
                aggregate.heldCards.add(cardId);
            }
        }

        TeamAnalyticsItem toItem(int rank) {
            List<TeamAnalyticsStageItem> stageItems = stageAggregates.values().stream()
                    .map(StageAggregate::toItem)
                    .toList();

            List<TeamParticipantAnalyticsItem> participantItems = participantAggregates.values().stream()
                    .map(ParticipantAggregate::toItem)
                    .sorted(Comparator
                            .comparing((TeamParticipantAnalyticsItem item) -> roleOrder(item.gameRole()))
                            .thenComparing(TeamParticipantAnalyticsItem::displayName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            List<TeamAnalyticsCardItem> cardItems = cards.stream()
                    .sorted(Comparator
                            .comparing((TeamAnalyticsCardItem item) -> Boolean.TRUE.equals(item.resolved()))
                            .thenComparing(TeamAnalyticsCardItem::returnCount, Comparator.reverseOrder())
                            .thenComparing(item -> item.fullCycleSeconds() != null ? item.fullCycleSeconds() : -1L, Comparator.reverseOrder())
                            .thenComparing(TeamAnalyticsCardItem::problemNumber))
                    .toList();

            BigDecimal currentBalance = economyState != null ? economyState.getCurrentBalance() : zeroAmount();
            BigDecimal availableBalance = currentBalance.subtract(reservedBudget);

            return new TeamAnalyticsItem(
                    team.getId(),
                    team.getName(),
                    rank,
                    participantAggregates.size(),
                    totalProblemCount,
                    resolvedProblemCount,
                    unresolvedProblemCount,
                    returnCount,
                    holdCount,
                    escalatedProblemCount,
                    activeEscalationCount,
                    currentBalance,
                    availableBalance,
                    economyState != null ? economyState.getTotalIncome() : zeroAmount(),
                    economyState != null ? economyState.getTotalExpenses() : zeroAmount(),
                    economyState != null ? economyState.getTotalPenalties() : zeroAmount(),
                    economyState != null ? economyState.getTotalBonuses() : zeroAmount(),
                    distributionAggregate.average(),
                    reactionAggregate.average(),
                    workAggregate.average(),
                    departmentReviewAggregate.average(),
                    chiefReviewAggregate.average(),
                    fullCycleAggregate.average(),
                    resolveBottleneckLabel(),
                    stageItems,
                    participantItems,
                    cardItems
            );
        }

        private String resolveBottleneckLabel() {
            Map<String, Long> metrics = Map.of(
                    "Распределение у руководителей", Objects.requireNonNullElse(distributionAggregate.average(), -1L),
                    "Реакция исполнителей", Objects.requireNonNullElse(reactionAggregate.average(), -1L),
                    "Выполнение задач", Objects.requireNonNullElse(workAggregate.average(), -1L),
                    "Согласование подразделения", Objects.requireNonNullElse(departmentReviewAggregate.average(), -1L),
                    "Финальное согласование главврача", Objects.requireNonNullElse(chiefReviewAggregate.average(), -1L)
            );

            return metrics.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .filter(entry -> entry.getValue() >= 0)
                    .map(Map.Entry::getKey)
                    .orElse("Данных пока недостаточно");
        }
    }
}
