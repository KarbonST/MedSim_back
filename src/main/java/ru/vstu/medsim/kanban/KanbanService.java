package ru.vstu.medsim.kanban;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.economy.SessionEconomyService;
import ru.vstu.medsim.economy.domain.FinalStageCrisisType;
import ru.vstu.medsim.economy.domain.ProblemEscalationType;
import ru.vstu.medsim.economy.domain.TeamResourceReservation;
import ru.vstu.medsim.economy.domain.SessionProblemStatus;
import ru.vstu.medsim.economy.domain.TeamProblemState;
import ru.vstu.medsim.economy.repository.TeamProblemStateRepository;
import ru.vstu.medsim.kanban.domain.KanbanCardEventType;
import ru.vstu.medsim.kanban.domain.KanbanCardPriority;
import ru.vstu.medsim.kanban.domain.KanbanCardStatus;
import ru.vstu.medsim.kanban.domain.KanbanResponsibleDepartment;
import ru.vstu.medsim.kanban.domain.KanbanSolutionOption;
import ru.vstu.medsim.kanban.domain.TeamKanbanCard;
import ru.vstu.medsim.kanban.domain.TeamKanbanCardEvent;
import ru.vstu.medsim.kanban.dto.GameSessionKanbanResponse;
import ru.vstu.medsim.kanban.dto.KanbanCardHistoryItem;
import ru.vstu.medsim.kanban.dto.KanbanSolutionOptionItem;
import ru.vstu.medsim.kanban.dto.PlayerKanbanNotificationItem;
import ru.vstu.medsim.kanban.dto.TeamKanbanBoardItem;
import ru.vstu.medsim.kanban.dto.TeamKanbanCardItem;
import ru.vstu.medsim.kanban.dto.TeamKanbanOverviewItem;
import ru.vstu.medsim.kanban.repository.KanbanSolutionOptionRepository;
import ru.vstu.medsim.kanban.repository.TeamKanbanCardEventRepository;
import ru.vstu.medsim.kanban.repository.TeamKanbanCardRepository;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.player.dto.PlayerKanbanCardStatusUpdateRequest;
import ru.vstu.medsim.player.dto.PlayerKanbanSolutionSelectionRequest;
import ru.vstu.medsim.player.repository.SessionParticipantRepository;
import ru.vstu.medsim.session.GameSessionQueryService;
import ru.vstu.medsim.session.domain.SessionStageSetting;
import ru.vstu.medsim.session.domain.SessionTeam;
import ru.vstu.medsim.session.repository.SessionStageSettingRepository;
import ru.vstu.medsim.session.repository.SessionTeamRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class KanbanService {

    private static final String CHIEF_DOCTOR_ROLE = "Главный врач";
    private static final String NURSING_LEAD_ROLE = "Главная медсестра";
    private static final String ENGINEERING_LEAD_ROLE = "Главный инженер";

    private static final Map<KanbanResponsibleDepartment, String> DEPARTMENT_LEAD_ROLES = Map.of(
            KanbanResponsibleDepartment.NURSING, NURSING_LEAD_ROLE,
            KanbanResponsibleDepartment.ENGINEERING, ENGINEERING_LEAD_ROLE
    );

    private static final Map<KanbanResponsibleDepartment, Set<String>> DEPARTMENT_EXECUTOR_ROLES = Map.of(
            KanbanResponsibleDepartment.NURSING, Set.of(
                    NURSING_LEAD_ROLE,
                    "Сестра поликлинического отделения",
                    "Сестра диагностического отделения"
            ),
            KanbanResponsibleDepartment.ENGINEERING, Set.of(
                    ENGINEERING_LEAD_ROLE,
                    "Заместитель главного инженера по медтехнике",
                    "Заместитель главного инженера по АХЧ"
            )
    );
    private static final Set<KanbanCardStatus> HOLDABLE_STATUSES = Set.of(
            KanbanCardStatus.REGISTERED,
            KanbanCardStatus.ASSIGNED,
            KanbanCardStatus.READY_FOR_WORK,
            KanbanCardStatus.REWORK
    );

    private final TeamProblemStateRepository teamProblemStateRepository;
    private final TeamKanbanCardRepository teamKanbanCardRepository;
    private final TeamKanbanCardEventRepository teamKanbanCardEventRepository;
    private final KanbanSolutionOptionRepository kanbanSolutionOptionRepository;
    private final SessionEconomyService sessionEconomyService;
    private final GameSessionQueryService gameSessionQueryService;
    private final SessionTeamRepository sessionTeamRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final SessionStageSettingRepository sessionStageSettingRepository;

    public KanbanService(
            TeamProblemStateRepository teamProblemStateRepository,
            TeamKanbanCardRepository teamKanbanCardRepository,
            TeamKanbanCardEventRepository teamKanbanCardEventRepository,
            KanbanSolutionOptionRepository kanbanSolutionOptionRepository,
            SessionEconomyService sessionEconomyService,
            GameSessionQueryService gameSessionQueryService,
            SessionTeamRepository sessionTeamRepository,
            SessionParticipantRepository sessionParticipantRepository,
            SessionStageSettingRepository sessionStageSettingRepository
    ) {
        this.teamProblemStateRepository = teamProblemStateRepository;
        this.teamKanbanCardRepository = teamKanbanCardRepository;
        this.teamKanbanCardEventRepository = teamKanbanCardEventRepository;
        this.kanbanSolutionOptionRepository = kanbanSolutionOptionRepository;
        this.sessionEconomyService = sessionEconomyService;
        this.gameSessionQueryService = gameSessionQueryService;
        this.sessionTeamRepository = sessionTeamRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
        this.sessionStageSettingRepository = sessionStageSettingRepository;
    }

    @Transactional
    public void initializeCardsForProblemStates(List<TeamProblemState> problemStates) {
        if (problemStates.isEmpty()) {
            return;
        }

        teamKanbanCardRepository.saveAll(problemStates.stream()
                .map(problemState -> new TeamKanbanCard(
                        problemState.getTeamRoomState().getTeam(),
                        problemState
                ))
                .toList());
    }

    @Transactional
    public TeamKanbanBoardItem getTeamBoard(SessionTeam team, Integer activeStageNumber) {
        ensureMissingCardsForTeam(team);
        List<TeamKanbanCard> cards = teamKanbanCardRepository.findAllCardsForTeam(team.getId())
                .stream()
                .filter(card -> isReleasedForStage(card, activeStageNumber))
                .toList();
        Map<Long, List<TeamKanbanCardEvent>> eventsByCardId = loadEventsByCardId(cards);
        FinalStageCrisisType crisisType = activeStageNumber != null && activeStageNumber >= 3
                ? team.getGameSession().getFinalStageCrisisType()
                : null;
        int activeEscalationCount = (int) cards.stream()
                .filter(card -> card.getProblemState().hasActiveEscalation())
                .count();

        return new TeamKanbanBoardItem(
                crisisType != null ? crisisType.name() : null,
                crisisType != null ? crisisType.getTitle() : null,
                crisisType != null ? crisisType.getDescription() : null,
                crisisType != null ? activeEscalationCount : null,
                cards.stream()
                        .map(card -> toCardItem(card, eventsByCardId.getOrDefault(card.getId(), List.of()), crisisType))
                        .toList()
        );
    }

    @Transactional
    public TeamKanbanBoardItem getTeamBoard(SessionTeam team) {
        return getTeamBoard(team, null);
    }

    @Transactional(readOnly = true)
    public List<PlayerKanbanNotificationItem> getNotificationsForParticipant(SessionParticipant participant) {
        if (participant.getTeam() == null) {
            return List.of();
        }

        return teamKanbanCardEventRepository
                .findRecentTargetedEvents(participant.getId(), PageRequest.of(0, 8))
                .stream()
                .map(this::toNotificationItem)
                .toList();
    }

    @Transactional
    public GameSessionKanbanResponse getSessionBoards(String sessionCode) {
        GameSession session = gameSessionQueryService.getSessionOrThrow(sessionCode);
        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());
        Integer releasedStageNumber = resolveReleasedStageNumber(session);

        return new GameSessionKanbanResponse(
                session.getId(),
                session.getCode(),
                session.getName(),
                session.getStatus().name(),
                teams.stream()
                        .map(team -> new TeamKanbanOverviewItem(
                                team.getId(),
                                team.getName(),
                                getTeamBoard(team, releasedStageNumber)
                        ))
                        .toList()
        );
    }

    @Transactional
    public void releaseHeldCardsForStage(GameSession session, Integer activeStageNumber) {
        if (activeStageNumber == null) {
            return;
        }

        teamKanbanCardRepository.findAllByGameSessionIdAndStatus(session.getId(), KanbanCardStatus.HOLD)
                .stream()
                .filter(card -> isReleasedForStage(card, activeStageNumber))
                .forEach(this::releaseHeldCardToStageBacklog);
    }

    @Transactional
    public void recordStageCrisisEscalations(GameSession session, List<Long> activatedProblemStateIds) {
        if (session == null || activatedProblemStateIds == null || activatedProblemStateIds.isEmpty()) {
            return;
        }

        Set<Long> problemStateIds = Set.copyOf(activatedProblemStateIds);
        List<SessionTeam> teams = sessionTeamRepository.findAllByGameSessionIdOrderBySortOrderAscIdAsc(session.getId());

        for (SessionTeam team : teams) {
            SessionParticipant chiefDoctor = findTeamMemberByRole(team, CHIEF_DOCTOR_ROLE).orElse(null);

            teamKanbanCardRepository.findAllCardsForTeam(team.getId())
                    .stream()
                    .filter(card -> problemStateIds.contains(card.getProblemState().getId()))
                    .forEach(card -> recordEvent(
                            card,
                            null,
                            chiefDoctor,
                            KanbanCardEventType.STAGE_CRISIS_ESCALATED,
                            card.getStatus(),
                            card.getStatus(),
                            card.getPriority(),
                            card.getResponsibleDepartment()
                    ));
        }
    }

    @Transactional
    public void updateCardStatus(
            GameSession session,
            SessionTeam team,
            SessionParticipant actor,
            Long cardId,
            PlayerKanbanCardStatusUpdateRequest request
    ) {
        TeamKanbanCard card = teamKanbanCardRepository
                .findCardForTeamSession(cardId, team.getId(), session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Карточка канбан-доски не найдена."));

        if (!isReleasedForStage(card, session.getActiveStageNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Эта карточка относится к будущему этапу и пока недоступна.");
        }

        applyWorkflowUpdate(session, team, actor, card, request);
        card.getProblemState().updateStatus(toProblemStatus(card.getStatus()));
    }

    @Transactional
    public void selectSolutionOption(
            GameSession session,
            SessionTeam team,
            SessionParticipant actor,
            Long cardId,
            PlayerKanbanSolutionSelectionRequest request
    ) {
        TeamKanbanCard card = teamKanbanCardRepository
                .findCardForTeamSession(cardId, team.getId(), session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Карточка канбан-доски не найдена."));

        if (!isReleasedForStage(card, session.getActiveStageNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Эта карточка относится к будущему этапу и пока недоступна.");
        }

        ensureCurrentStatus(card, KanbanCardStatus.IN_PROGRESS, "Способ решения можно выбрать только для задачи в работе.");

        if (card.getAssignee() == null || !card.getAssignee().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Способ решения может выбрать только назначенный исполнитель.");
        }

        KanbanSolutionOption solutionOption = kanbanSolutionOptionRepository
                .findByIdAndProblemTemplateIdAndActiveTrue(
                        request.solutionOptionId(),
                        card.getProblemState().getProblemTemplate().getId()
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Способ решения не найден для этой задачи."));

        sessionEconomyService.reserveResourcesForTask(actor, card, solutionOption);
        recordEvent(
                card,
                actor,
                null,
                KanbanCardEventType.SOLUTION_SELECTED,
                card.getStatus(),
                card.getStatus(),
                null,
                card.getResponsibleDepartment()
        );
        card.getProblemState().updateStatus(toProblemStatus(card.getStatus()));
    }

    private void applyWorkflowUpdate(
            GameSession session,
            SessionTeam team,
            SessionParticipant actor,
            TeamKanbanCard card,
            PlayerKanbanCardStatusUpdateRequest request
    ) {
        switch (request.status()) {
            case ASSIGNED -> triageCard(actor, card, request);
            case READY_FOR_WORK -> assignCardToExecutor(session, team, actor, card, request);
            case IN_PROGRESS -> startWork(actor, card);
            case DEPARTMENT_REVIEW -> sendToDepartmentReview(actor, card);
            case CHIEF_DOCTOR_REVIEW -> approveDepartmentReview(actor, card);
            case DONE -> approveChiefDoctorReview(actor, card);
            case HOLD -> holdCardForNextStage(actor, card);
            case REGISTERED -> returnCardToStageBacklog(actor, card);
            case REWORK -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Статус доработки пока не используется: верните карточку в задачи этапа."
            );
        }
    }

    private void triageCard(
            SessionParticipant actor,
            TeamKanbanCard card,
            PlayerKanbanCardStatusUpdateRequest request
    ) {
        ensureChiefDoctor(actor);

        if (card.getStatus() != KanbanCardStatus.REGISTERED && card.getStatus() != KanbanCardStatus.REWORK) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Назначить подразделение можно только из списка задач этапа.");
        }

        if (request.priority() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите приоритет задачи.");
        }

        if (request.responsibleDepartment() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите ответственное подразделение.");
        }

        KanbanCardStatus fromStatus = card.getStatus();
        KanbanResponsibleDepartment responsibleDepartment = request.responsibleDepartment();
        card.triage(request.priority(), request.responsibleDepartment());

        recordEvent(
                card,
                actor,
                null,
                KanbanCardEventType.PRIORITY_SET,
                fromStatus,
                card.getStatus(),
                request.priority(),
                null
        );
        recordEvent(
                card,
                actor,
                findTeamMemberByRole(card.getTeam(), DEPARTMENT_LEAD_ROLES.get(responsibleDepartment)).orElse(null),
                KanbanCardEventType.DEPARTMENT_ASSIGNED,
                fromStatus,
                card.getStatus(),
                null,
                responsibleDepartment
        );
    }

    private void assignCardToExecutor(
            GameSession session,
            SessionTeam team,
            SessionParticipant actor,
            TeamKanbanCard card,
            PlayerKanbanCardStatusUpdateRequest request
    ) {
        ensureCurrentStatus(card, KanbanCardStatus.ASSIGNED, "Назначить исполнителя можно только для задачи у руководителя подразделения.");
        KanbanResponsibleDepartment department = requireResponsibleDepartment(card);
        ensureDepartmentLead(actor, department);

        if (request.assigneeParticipantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите исполнителя задачи.");
        }

        SessionParticipant assignee = sessionParticipantRepository
                .findByIdAndGameSessionId(request.assigneeParticipantId(), session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Исполнитель не найден в этой сессии."));

        ensureAssigneeIsNotActor(actor, assignee);
        ensureParticipantInTeam(assignee, team);
        ensureExecutorMatchesDepartment(assignee, department);

        KanbanCardStatus fromStatus = card.getStatus();
        card.assignTo(assignee);
        recordEvent(
                card,
                actor,
                assignee,
                KanbanCardEventType.EXECUTOR_ASSIGNED,
                fromStatus,
                card.getStatus(),
                null,
                department
        );
    }

    private void startWork(SessionParticipant actor, TeamKanbanCard card) {
        ensureCurrentStatus(card, KanbanCardStatus.READY_FOR_WORK, "В работу можно взять только назначенную вам задачу.");

        if (card.getAssignee() == null || !card.getAssignee().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "В работу может взять только назначенный исполнитель.");
        }

        KanbanCardStatus fromStatus = card.getStatus();
        KanbanResponsibleDepartment responsibleDepartment = card.getResponsibleDepartment();
        card.startWork();
        recordEvent(
                card,
                actor,
                null,
                KanbanCardEventType.WORK_STARTED,
                fromStatus,
                card.getStatus(),
                null,
                responsibleDepartment
        );
    }

    private void sendToDepartmentReview(SessionParticipant actor, TeamKanbanCard card) {
        ensureCurrentStatus(card, KanbanCardStatus.IN_PROGRESS, "На проверку можно отправить только задачу в процессе.");

        if (card.getAssignee() == null || !card.getAssignee().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Отправить задачу на проверку может только назначенный исполнитель.");
        }

        if (card.getResourcesSpentAt() == null && sessionEconomyService.getActiveReservation(card).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Перед отправкой на согласование выберите способ решения задачи."
            );
        }

        KanbanCardStatus fromStatus = card.getStatus();
        KanbanResponsibleDepartment responsibleDepartment = requireResponsibleDepartment(card);
        card.updateStatus(KanbanCardStatus.DEPARTMENT_REVIEW);
        recordEvent(
                card,
                actor,
                findTeamMemberByRole(card.getTeam(), DEPARTMENT_LEAD_ROLES.get(responsibleDepartment)).orElse(null),
                KanbanCardEventType.SENT_TO_DEPARTMENT_REVIEW,
                fromStatus,
                card.getStatus(),
                null,
                responsibleDepartment
        );
    }

    private void approveDepartmentReview(SessionParticipant actor, TeamKanbanCard card) {
        ensureCurrentStatus(card, KanbanCardStatus.DEPARTMENT_REVIEW, "Передать главврачу можно только задачу на проверке подразделения.");
        KanbanResponsibleDepartment responsibleDepartment = requireResponsibleDepartment(card);
        ensureDepartmentLead(actor, responsibleDepartment);

        KanbanCardStatus fromStatus = card.getStatus();
        card.updateStatus(KanbanCardStatus.CHIEF_DOCTOR_REVIEW);
        recordEvent(
                card,
                actor,
                findTeamMemberByRole(card.getTeam(), CHIEF_DOCTOR_ROLE).orElse(null),
                KanbanCardEventType.DEPARTMENT_APPROVED,
                fromStatus,
                card.getStatus(),
                null,
                responsibleDepartment
        );
    }

    private void approveChiefDoctorReview(SessionParticipant actor, TeamKanbanCard card) {
        ensureChiefDoctor(actor);
        ensureCurrentStatus(card, KanbanCardStatus.CHIEF_DOCTOR_REVIEW, "Закрыть можно только задачу на финальном согласовании главврача.");

        KanbanCardStatus fromStatus = card.getStatus();
        KanbanResponsibleDepartment responsibleDepartment = card.getResponsibleDepartment();
        SessionParticipant assignee = card.getAssignee();
        boolean hadActiveEscalation = card.getProblemState().hasActiveEscalation();
        TeamResourceReservation reservation = sessionEconomyService.requireActiveReservationForCommit(card);
        sessionEconomyService.validateReservedResourcesCanBeCommitted(card, reservation);

        if (!isSolutionSuccessful(card, reservation.getSolutionOption())) {
            sessionEconomyService.releaseReservedResources(actor, card);
            card.returnToStageBacklog();
            recordEvent(
                    card,
                    actor,
                    assignee,
                    KanbanCardEventType.SOLUTION_FAILED,
                    fromStatus,
                    card.getStatus(),
                    null,
                    responsibleDepartment
            );
            return;
        }

        sessionEconomyService.commitReservedResources(actor, card);
        card.updateStatus(KanbanCardStatus.DONE);
        recordEvent(
                card,
                actor,
                assignee,
                KanbanCardEventType.CHIEF_DOCTOR_APPROVED,
                fromStatus,
                card.getStatus(),
                null,
                responsibleDepartment
        );

        if (hadActiveEscalation) {
            recordEvent(
                    card,
                    actor,
                    null,
                    KanbanCardEventType.STAGE_CRISIS_RESOLVED,
                    card.getStatus(),
                    card.getStatus(),
                    card.getPriority(),
                    responsibleDepartment
            );
        }
    }

    private void returnCardToStageBacklog(SessionParticipant actor, TeamKanbanCard card) {
        KanbanCardStatus fromStatus = card.getStatus();
        KanbanResponsibleDepartment responsibleDepartment = card.getResponsibleDepartment();
        SessionParticipant assignee = card.getAssignee();

        if (card.getStatus() == KanbanCardStatus.DEPARTMENT_REVIEW) {
            ensureDepartmentLead(actor, requireResponsibleDepartment(card));
        } else if (card.getStatus() == KanbanCardStatus.CHIEF_DOCTOR_REVIEW) {
            ensureChiefDoctor(actor);
        } else if (card.getStatus() == KanbanCardStatus.HOLD) {
            ensureCanManageHeldCard(actor, card);
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вернуть в задачи этапа можно только карточку на согласовании или в отложенных.");
        }

        sessionEconomyService.releaseReservedResources(actor, card);
        card.returnToStageBacklog();
        recordEvent(
                card,
                actor,
                assignee,
                KanbanCardEventType.RETURNED_TO_STAGE,
                fromStatus,
                card.getStatus(),
                null,
                responsibleDepartment
        );
    }

    private void holdCardForNextStage(SessionParticipant actor, TeamKanbanCard card) {
        if (!HOLDABLE_STATUSES.contains(card.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Отложить можно только задачу, которая ещё не находится в активной работе или на согласовании.");
        }

        ensureCanManageHeldCard(actor, card);

        KanbanCardStatus fromStatus = card.getStatus();
        KanbanResponsibleDepartment responsibleDepartment = card.getResponsibleDepartment();
        SessionParticipant targetParticipant = resolveHoldTargetParticipant(actor, card, responsibleDepartment);

        sessionEconomyService.releaseReservedResources(actor, card);
        card.holdForNextStage();
        recordEvent(
                card,
                actor,
                targetParticipant,
                KanbanCardEventType.TASK_HELD,
                fromStatus,
                card.getStatus(),
                null,
                responsibleDepartment
        );
    }

    private SessionParticipant resolveHoldTargetParticipant(
            SessionParticipant actor,
            TeamKanbanCard card,
            KanbanResponsibleDepartment responsibleDepartment
    ) {
        SessionParticipant targetParticipant = card.getAssignee();
        if (targetParticipant == null
                && CHIEF_DOCTOR_ROLE.equals(actor.getGameRole())
                && responsibleDepartment != null) {
            targetParticipant = findTeamMemberByRole(card.getTeam(), DEPARTMENT_LEAD_ROLES.get(responsibleDepartment))
                    .orElse(null);
        }

        return targetParticipant != null && targetParticipant.getId().equals(actor.getId())
                ? null
                : targetParticipant;
    }

    private void releaseHeldCardToStageBacklog(TeamKanbanCard card) {
        KanbanCardStatus fromStatus = card.getStatus();
        KanbanResponsibleDepartment responsibleDepartment = card.getResponsibleDepartment();

        card.returnToStageBacklog();
        card.getProblemState().updateStatus(toProblemStatus(card.getStatus()));
        recordEvent(
                card,
                null,
                null,
                KanbanCardEventType.HOLD_RELEASED,
                fromStatus,
                card.getStatus(),
                null,
                responsibleDepartment
        );
    }

    private void ensureMissingCardsForTeam(SessionTeam team) {
        Set<Long> existingProblemStateIds = Set.copyOf(teamKanbanCardRepository.findProblemStateIdsByTeamId(team.getId()));
        List<TeamProblemState> missingProblemStates = teamProblemStateRepository
                .findAllByTeamRoomStateTeamIdOrderByTeamRoomStateClinicRoomSortOrderAscProblemTemplateProblemNumberAscIdAsc(team.getId())
                .stream()
                .filter(problemState -> !existingProblemStateIds.contains(problemState.getId()))
                .toList();

        initializeCardsForProblemStates(missingProblemStates);
    }

    private Map<Long, List<TeamKanbanCardEvent>> loadEventsByCardId(List<TeamKanbanCard> cards) {
        List<Long> cardIds = cards.stream().map(TeamKanbanCard::getId).toList();

        if (cardIds.isEmpty()) {
            return Map.of();
        }

        return teamKanbanCardEventRepository.findAllForCardIds(cardIds)
                .stream()
                .collect(Collectors.groupingBy(
                        event -> event.getCard().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private TeamKanbanCardItem toCardItem(
            TeamKanbanCard card,
            List<TeamKanbanCardEvent> events,
            FinalStageCrisisType crisisType
    ) {
        SessionParticipant assignee = card.getAssignee();
        var problemTemplate = card.getProblemState().getProblemTemplate();
        List<KanbanSolutionOption> solutionOptions = kanbanSolutionOptionRepository
                .findAllByProblemTemplateIdAndActiveTrueOrderBySortOrderAscIdAsc(problemTemplate.getId());
        Optional<TeamResourceReservation> reservation = sessionEconomyService.getCurrentReservation(card);
        boolean escalated = card.getProblemState().hasActiveEscalation();
        ProblemEscalationType escalationType = card.getProblemState().getEscalationType();

        return new TeamKanbanCardItem(
                card.getId(),
                card.getProblemState().getId(),
                problemTemplate.getProblemNumber(),
                card.getProblemState().getStageNumber(),
                problemTemplate.getTitle(),
                card.getProblemState().getTeamRoomState().getClinicRoom().getCode(),
                card.getProblemState().getTeamRoomState().getClinicRoom().getName(),
                problemTemplate.getSeverity().name(),
                card.getPriority() != null ? card.getPriority().name() : null,
                problemTemplate.getBudgetCost(),
                problemTemplate.getTimeCost(),
                problemTemplate.getRequiredItemName(),
                problemTemplate.getRequiredItemQuantity(),
                solutionOptions.stream()
                        .map(option -> toSolutionOptionItem(card, option))
                        .toList(),
                reservation.map(value -> value.getSolutionOption().getId()).orElse(null),
                reservation.map(value -> value.getSolutionOption().getTitle()).orElse(null),
                reservation.map(value -> value.getStatus().name()).orElse(null),
                reservation.map(TeamResourceReservation::getBudgetAmount).orElse(BigDecimal.ZERO.setScale(2)),
                reservation.map(TeamResourceReservation::getTimeUnits).orElse(0),
                reservation.map(TeamResourceReservation::getItemName).orElse(null),
                reservation.map(TeamResourceReservation::getItemQuantity).orElse(0),
                card.getResourcesSpentAt() != null,
                escalated,
                escalated && escalationType != null ? escalationType.name() : null,
                escalated && escalationType != null ? escalationType.getTitle() : null,
                escalated && escalationType != null ? escalationType.getDescription() : null,
                escalated && escalationType != null ? escalationType.getPenaltyHint(crisisType) : null,
                card.getResponsibleDepartment() != null ? card.getResponsibleDepartment().name() : null,
                card.getStatus().name(),
                assignee != null ? assignee.getId() : null,
                assignee != null ? assignee.getPlayer().getDisplayName() : null,
                events.stream()
                        .map(this::toHistoryItem)
                        .toList()
        );
    }

    private KanbanSolutionOptionItem toSolutionOptionItem(TeamKanbanCard card, KanbanSolutionOption option) {
        Optional<String> unavailableReason = sessionEconomyService.getSolutionUnavailableReason(card.getTeam(), option);

        return new KanbanSolutionOptionItem(
                option.getId(),
                option.getTitle(),
                option.getDescription(),
                option.getBudgetCost(),
                option.getTimeCost(),
                option.getRequiredItemName(),
                option.getRequiredItemQuantity(),
                unavailableReason.isEmpty(),
                unavailableReason.orElse(null)
        );
    }

    private void recordEvent(
            TeamKanbanCard card,
            SessionParticipant actor,
            SessionParticipant targetParticipant,
            KanbanCardEventType eventType,
            KanbanCardStatus fromStatus,
            KanbanCardStatus toStatus,
            KanbanCardPriority priority,
            KanbanResponsibleDepartment responsibleDepartment
    ) {
        teamKanbanCardEventRepository.save(new TeamKanbanCardEvent(
                card,
                actor,
                targetParticipant,
                eventType,
                fromStatus,
                toStatus,
                priority,
                responsibleDepartment
        ));
    }

    private KanbanCardHistoryItem toHistoryItem(TeamKanbanCardEvent event) {
        SessionParticipant actor = event.getActor();
        SessionParticipant targetParticipant = event.getTargetParticipant();

        return new KanbanCardHistoryItem(
                event.getId(),
                event.getEventType().name(),
                toHistoryMessage(event),
                actor != null ? actor.getPlayer().getDisplayName() : null,
                actor != null ? actor.getGameRole() : null,
                targetParticipant != null ? targetParticipant.getPlayer().getDisplayName() : null,
                targetParticipant != null ? targetParticipant.getGameRole() : null,
                event.getPriority() != null ? event.getPriority().name() : null,
                event.getResponsibleDepartment() != null ? event.getResponsibleDepartment().name() : null,
                event.getCreatedAt()
        );
    }

    private PlayerKanbanNotificationItem toNotificationItem(TeamKanbanCardEvent event) {
        return new PlayerKanbanNotificationItem(
                event.getId(),
                event.getCard().getId(),
                event.getEventType().name(),
                toNotificationTitle(event),
                toNotificationMessage(event),
                event.getCreatedAt()
        );
    }

    private void ensureChiefDoctor(SessionParticipant actor) {
        if (!CHIEF_DOCTOR_ROLE.equals(actor.getGameRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это действие доступно только главному врачу.");
        }
    }

    private void ensureDepartmentLead(SessionParticipant actor, KanbanResponsibleDepartment department) {
        String expectedRole = DEPARTMENT_LEAD_ROLES.get(department);

        if (!expectedRole.equals(actor.getGameRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это действие доступно только руководителю выбранного подразделения.");
        }
    }

    private void ensureCanManageHeldCard(SessionParticipant actor, TeamKanbanCard card) {
        if (CHIEF_DOCTOR_ROLE.equals(actor.getGameRole())) {
            return;
        }

        KanbanResponsibleDepartment responsibleDepartment = card.getResponsibleDepartment();
        if (responsibleDepartment != null
                && DEPARTMENT_LEAD_ROLES.get(responsibleDepartment).equals(actor.getGameRole())) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Отложить или вернуть отложенную задачу могут только главврач и руководитель ответственного подразделения.");
    }

    private boolean isSolutionSuccessful(TeamKanbanCard card, KanbanSolutionOption option) {
        double successProbability = resolveSuccessProbability(card, option).doubleValue();

        if (successProbability <= 0) {
            return false;
        }
        if (successProbability >= 1) {
            return true;
        }

        return ThreadLocalRandom.current().nextDouble() < successProbability;
    }

    private BigDecimal resolveSuccessProbability(TeamKanbanCard card, KanbanSolutionOption option) {
        BigDecimal multiplier = BigDecimal.ONE;
        if (card.getResponsibleDepartment() == KanbanResponsibleDepartment.NURSING) {
            multiplier = option.getNursingSuccessMultiplier();
        } else if (card.getResponsibleDepartment() == KanbanResponsibleDepartment.ENGINEERING) {
            multiplier = option.getEngineeringSuccessMultiplier();
        }

        BigDecimal probability = option.getBaseSuccessProbability().multiply(multiplier);
        if (probability.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (probability.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return probability;
    }

    private void ensureCurrentStatus(TeamKanbanCard card, KanbanCardStatus expectedStatus, String message) {
        if (card.getStatus() != expectedStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void ensureParticipantInTeam(SessionParticipant participant, SessionTeam team) {
        if (participant.getTeam() == null || !participant.getTeam().getId().equals(team.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Исполнителя можно выбрать только из своей команды.");
        }
    }

    private void ensureAssigneeIsNotActor(SessionParticipant actor, SessionParticipant assignee) {
        if (actor.getId().equals(assignee.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Руководитель подразделения не может назначить задачу на себя.");
        }
    }

    private void ensureExecutorMatchesDepartment(SessionParticipant assignee, KanbanResponsibleDepartment department) {
        Set<String> allowedRoles = DEPARTMENT_EXECUTOR_ROLES.getOrDefault(department, Set.of());

        if (!allowedRoles.contains(assignee.getGameRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль исполнителя не относится к выбранному подразделению.");
        }
    }

    private KanbanResponsibleDepartment requireResponsibleDepartment(TeamKanbanCard card) {
        if (card.getResponsibleDepartment() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У карточки ещё не выбрано ответственное подразделение.");
        }

        return card.getResponsibleDepartment();
    }

    private Optional<SessionParticipant> findTeamMemberByRole(SessionTeam team, String role) {
        if (role == null) {
            return Optional.empty();
        }

        return sessionParticipantRepository
                .findAllByGameSessionIdAndTeamIdOrderByJoinedAtAscIdAsc(team.getGameSession().getId(), team.getId())
                .stream()
                .filter(participant -> role.equals(participant.getGameRole()))
                .findFirst();
    }

    private String toHistoryMessage(TeamKanbanCardEvent event) {
        String actorName = toParticipantName(event.getActor());

        return switch (event.getEventType()) {
            case PRIORITY_SET -> "%s поставил приоритет: %s".formatted(actorName, toPriorityLabel(event.getPriority()));
            case DEPARTMENT_ASSIGNED -> "%s назначил подразделение: %s".formatted(
                    actorName,
                    toDepartmentLabel(event.getResponsibleDepartment())
            );
            case EXECUTOR_ASSIGNED -> "%s назначил исполнителя: %s".formatted(
                    actorName,
                    toParticipantName(event.getTargetParticipant())
            );
            case WORK_STARTED -> "%s взял задачу в работу".formatted(actorName);
            case SOLUTION_SELECTED -> "%s выбрал способ решения и зарезервировал ресурсы".formatted(actorName);
            case SENT_TO_DEPARTMENT_REVIEW -> "%s отправил задачу на согласование".formatted(actorName);
            case DEPARTMENT_APPROVED -> "%s согласовал задачу и передал главврачу".formatted(actorName);
            case SOLUTION_FAILED -> "%s проверил результат: решение не сработало, задача вернулась в задачи этапа".formatted(actorName);
            case CHIEF_DOCTOR_APPROVED -> "%s финально согласовал и закрыл задачу".formatted(actorName);
            case TASK_HELD -> "%s отложил задачу на следующий этап".formatted(actorName);
            case HOLD_RELEASED -> "Задача вернулась из отложенных в задачи этапа";
            case RETURNED_TO_STAGE -> "%s вернул задачу в задачи этапа".formatted(actorName);
            case STAGE_CRISIS_ESCALATED -> "Система отметила задачу как кризис 3 этапа: %s".formatted(
                    toEscalationLabel(event.getCard())
            );
            case STAGE_CRISIS_RESOLVED -> "%s снял кризис 3 этапа, закрыв задачу".formatted(actorName);
        };
    }

    private String toNotificationTitle(TeamKanbanCardEvent event) {
        return switch (event.getEventType()) {
            case DEPARTMENT_ASSIGNED -> "Нужно назначить исполнителя";
            case EXECUTOR_ASSIGNED -> "Вам назначена задача";
            case SENT_TO_DEPARTMENT_REVIEW, DEPARTMENT_APPROVED -> "Задача ожидает согласования";
            case SOLUTION_FAILED, RETURNED_TO_STAGE -> "Задача возвращена";
            case CHIEF_DOCTOR_APPROVED -> "Задача закрыта";
            case TASK_HELD -> "Задача отложена";
            case HOLD_RELEASED -> "Задача вернулась в этап";
            case STAGE_CRISIS_ESCALATED -> "Критическая задача 3 этапа";
            case STAGE_CRISIS_RESOLVED -> "Кризис снят";
            case PRIORITY_SET, WORK_STARTED, SOLUTION_SELECTED -> "Обновление задачи";
        };
    }

    private String toNotificationMessage(TeamKanbanCardEvent event) {
        String problemTitle = event.getCard().getProblemState().getProblemTemplate().getTitle();
        String roomName = event.getCard().getProblemState().getTeamRoomState().getClinicRoom().getName();
        String actorName = toParticipantName(event.getActor());

        return switch (event.getEventType()) {
            case DEPARTMENT_ASSIGNED -> "%s передал задачу вашему подразделению: %s, %s".formatted(
                    actorName,
                    problemTitle,
                    roomName
            );
            case EXECUTOR_ASSIGNED -> "%s назначил вам задачу: %s, %s".formatted(actorName, problemTitle, roomName);
            case SENT_TO_DEPARTMENT_REVIEW -> "%s отправил задачу на согласование: %s, %s".formatted(
                    actorName,
                    problemTitle,
                    roomName
            );
            case DEPARTMENT_APPROVED -> "%s передал задачу на финальное согласование: %s, %s".formatted(
                    actorName,
                    problemTitle,
                    roomName
            );
            case SOLUTION_FAILED -> "%s проверил результат и вернул задачу в задачи этапа: %s, %s".formatted(actorName, problemTitle, roomName);
            case RETURNED_TO_STAGE -> "%s вернул задачу в задачи этапа: %s, %s".formatted(actorName, problemTitle, roomName);
            case CHIEF_DOCTOR_APPROVED -> "%s закрыл задачу: %s, %s".formatted(actorName, problemTitle, roomName);
            case TASK_HELD -> "%s отложил задачу на следующий этап: %s, %s".formatted(actorName, problemTitle, roomName);
            case HOLD_RELEASED -> "Задача вернулась из отложенных в задачи этапа: %s, %s".formatted(problemTitle, roomName);
            case STAGE_CRISIS_ESCALATED -> "Система выделила задачу как критическую для 3 этапа: %s, %s. %s".formatted(
                    problemTitle,
                    roomName,
                    toEscalationLabel(event.getCard())
            );
            case STAGE_CRISIS_RESOLVED -> "%s снял кризис 3 этапа: %s, %s".formatted(actorName, problemTitle, roomName);
            case PRIORITY_SET, WORK_STARTED, SOLUTION_SELECTED -> "%s: %s, %s".formatted(toHistoryMessage(event), problemTitle, roomName);
        };
    }

    private String toParticipantName(SessionParticipant participant) {
        return participant != null ? participant.getPlayer().getDisplayName() : "Система";
    }

    private String toPriorityLabel(KanbanCardPriority priority) {
        if (priority == null) {
            return "не указан";
        }

        return switch (priority) {
            case LOW -> "низкий";
            case MEDIUM -> "средний";
            case HIGH -> "высокий";
        };
    }

    private String toDepartmentLabel(KanbanResponsibleDepartment department) {
        if (department == null) {
            return "не указано";
        }

        return switch (department) {
            case NURSING -> "сестринское подразделение";
            case ENGINEERING -> "инженерное подразделение";
        };
    }

    private String toEscalationLabel(TeamKanbanCard card) {
        ProblemEscalationType escalationType = card.getProblemState().getEscalationType();
        return escalationType != null ? escalationType.getTitle() : "критическая задача";
    }

    private boolean isReleasedForStage(TeamKanbanCard card, Integer activeStageNumber) {
        if (activeStageNumber == null) {
            return true;
        }

        Integer cardStageNumber = card.getProblemState().getStageNumber();
        return cardStageNumber == null || cardStageNumber <= activeStageNumber;
    }

    private Integer resolveReleasedStageNumber(GameSession session) {
        if (session.getActiveStageNumber() == null) {
            return null;
        }

        boolean problemWorkflowAvailable = sessionStageSettingRepository
                .findAllByGameSessionIdOrderByStageNumberAsc(session.getId())
                .stream()
                .filter(stage -> stage.getStageNumber().equals(session.getActiveStageNumber()))
                .findFirst()
                .map(SessionStageSetting::getInteractionMode)
                .map(interactionMode -> interactionMode.hasProblemWorkflow())
                .orElse(false);

        return problemWorkflowAvailable ? session.getActiveStageNumber() : 0;
    }

    private SessionProblemStatus toProblemStatus(KanbanCardStatus status) {
        return switch (status) {
            case DONE -> SessionProblemStatus.RESOLVED;
            case REGISTERED, ASSIGNED, READY_FOR_WORK, REWORK, HOLD -> SessionProblemStatus.ACTIVE;
            case IN_PROGRESS, DEPARTMENT_REVIEW, CHIEF_DOCTOR_REVIEW -> SessionProblemStatus.IN_PROGRESS;
        };
    }
}
