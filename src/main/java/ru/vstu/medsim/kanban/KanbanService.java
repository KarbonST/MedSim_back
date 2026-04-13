package ru.vstu.medsim.kanban;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.vstu.medsim.economy.SessionEconomyService;
import ru.vstu.medsim.economy.domain.SessionProblemStatus;
import ru.vstu.medsim.economy.domain.TeamProblemState;
import ru.vstu.medsim.economy.repository.TeamProblemStateRepository;
import ru.vstu.medsim.kanban.domain.KanbanCardEventType;
import ru.vstu.medsim.kanban.domain.KanbanCardPriority;
import ru.vstu.medsim.kanban.domain.KanbanCardStatus;
import ru.vstu.medsim.kanban.domain.KanbanResponsibleDepartment;
import ru.vstu.medsim.kanban.domain.TeamKanbanCard;
import ru.vstu.medsim.kanban.domain.TeamKanbanCardEvent;
import ru.vstu.medsim.kanban.dto.GameSessionKanbanResponse;
import ru.vstu.medsim.kanban.dto.KanbanCardHistoryItem;
import ru.vstu.medsim.kanban.dto.PlayerKanbanNotificationItem;
import ru.vstu.medsim.kanban.dto.TeamKanbanBoardItem;
import ru.vstu.medsim.kanban.dto.TeamKanbanCardItem;
import ru.vstu.medsim.kanban.dto.TeamKanbanOverviewItem;
import ru.vstu.medsim.kanban.repository.TeamKanbanCardEventRepository;
import ru.vstu.medsim.kanban.repository.TeamKanbanCardRepository;
import ru.vstu.medsim.player.domain.GameSession;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.player.dto.PlayerKanbanCardStatusUpdateRequest;
import ru.vstu.medsim.player.repository.SessionParticipantRepository;
import ru.vstu.medsim.session.GameSessionQueryService;
import ru.vstu.medsim.session.domain.SessionTeam;
import ru.vstu.medsim.session.repository.SessionTeamRepository;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private final TeamProblemStateRepository teamProblemStateRepository;
    private final TeamKanbanCardRepository teamKanbanCardRepository;
    private final TeamKanbanCardEventRepository teamKanbanCardEventRepository;
    private final SessionEconomyService sessionEconomyService;
    private final GameSessionQueryService gameSessionQueryService;
    private final SessionTeamRepository sessionTeamRepository;
    private final SessionParticipantRepository sessionParticipantRepository;

    public KanbanService(
            TeamProblemStateRepository teamProblemStateRepository,
            TeamKanbanCardRepository teamKanbanCardRepository,
            TeamKanbanCardEventRepository teamKanbanCardEventRepository,
            SessionEconomyService sessionEconomyService,
            GameSessionQueryService gameSessionQueryService,
            SessionTeamRepository sessionTeamRepository,
            SessionParticipantRepository sessionParticipantRepository
    ) {
        this.teamProblemStateRepository = teamProblemStateRepository;
        this.teamKanbanCardRepository = teamKanbanCardRepository;
        this.teamKanbanCardEventRepository = teamKanbanCardEventRepository;
        this.sessionEconomyService = sessionEconomyService;
        this.gameSessionQueryService = gameSessionQueryService;
        this.sessionTeamRepository = sessionTeamRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
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

        return new TeamKanbanBoardItem(
                cards.stream()
                        .map(card -> toCardItem(card, eventsByCardId.getOrDefault(card.getId(), List.of())))
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

        return new GameSessionKanbanResponse(
                session.getId(),
                session.getCode(),
                session.getName(),
                session.getStatus().name(),
                teams.stream()
                        .map(team -> new TeamKanbanOverviewItem(
                                team.getId(),
                                team.getName(),
                                getTeamBoard(team, session.getActiveStageNumber())
                        ))
                        .toList()
        );
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
        sessionEconomyService.spendResourcesForTask(actor, card);
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
    }

    private void returnCardToStageBacklog(SessionParticipant actor, TeamKanbanCard card) {
        KanbanCardStatus fromStatus = card.getStatus();
        KanbanResponsibleDepartment responsibleDepartment = card.getResponsibleDepartment();
        SessionParticipant assignee = card.getAssignee();

        if (card.getStatus() == KanbanCardStatus.DEPARTMENT_REVIEW) {
            ensureDepartmentLead(actor, requireResponsibleDepartment(card));
        } else if (card.getStatus() == KanbanCardStatus.CHIEF_DOCTOR_REVIEW) {
            ensureChiefDoctor(actor);
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вернуть в задачи этапа можно только карточку на согласовании.");
        }

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

    private TeamKanbanCardItem toCardItem(TeamKanbanCard card, List<TeamKanbanCardEvent> events) {
        SessionParticipant assignee = card.getAssignee();
        var problemTemplate = card.getProblemState().getProblemTemplate();

        return new TeamKanbanCardItem(
                card.getId(),
                card.getProblemState().getId(),
                problemTemplate.getProblemNumber(),
                problemTemplate.getStageNumber(),
                problemTemplate.getTitle(),
                card.getProblemState().getTeamRoomState().getClinicRoom().getCode(),
                card.getProblemState().getTeamRoomState().getClinicRoom().getName(),
                problemTemplate.getSeverity().name(),
                card.getPriority() != null ? card.getPriority().name() : null,
                problemTemplate.getBudgetCost(),
                problemTemplate.getTimeCost(),
                problemTemplate.getRequiredItemName(),
                problemTemplate.getRequiredItemQuantity(),
                card.getResourcesSpentAt() != null,
                card.getResponsibleDepartment() != null ? card.getResponsibleDepartment().name() : null,
                card.getStatus().name(),
                assignee != null ? assignee.getId() : null,
                assignee != null ? assignee.getPlayer().getDisplayName() : null,
                events.stream()
                        .map(this::toHistoryItem)
                        .toList()
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
            case SENT_TO_DEPARTMENT_REVIEW -> "%s отправил задачу на согласование".formatted(actorName);
            case DEPARTMENT_APPROVED -> "%s согласовал задачу и передал главврачу".formatted(actorName);
            case CHIEF_DOCTOR_APPROVED -> "%s финально согласовал и закрыл задачу".formatted(actorName);
            case RETURNED_TO_STAGE -> "%s вернул задачу в задачи этапа".formatted(actorName);
        };
    }

    private String toNotificationTitle(TeamKanbanCardEvent event) {
        return switch (event.getEventType()) {
            case DEPARTMENT_ASSIGNED -> "Нужно назначить исполнителя";
            case EXECUTOR_ASSIGNED -> "Вам назначена задача";
            case SENT_TO_DEPARTMENT_REVIEW, DEPARTMENT_APPROVED -> "Задача ожидает согласования";
            case RETURNED_TO_STAGE -> "Задача возвращена";
            case CHIEF_DOCTOR_APPROVED -> "Задача закрыта";
            case PRIORITY_SET, WORK_STARTED -> "Обновление задачи";
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
            case RETURNED_TO_STAGE -> "%s вернул задачу в задачи этапа: %s, %s".formatted(actorName, problemTitle, roomName);
            case CHIEF_DOCTOR_APPROVED -> "%s закрыл задачу: %s, %s".formatted(actorName, problemTitle, roomName);
            case PRIORITY_SET, WORK_STARTED -> "%s: %s, %s".formatted(toHistoryMessage(event), problemTitle, roomName);
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

    private boolean isReleasedForStage(TeamKanbanCard card, Integer activeStageNumber) {
        if (activeStageNumber == null) {
            return true;
        }

        Integer cardStageNumber = card.getProblemState().getProblemTemplate().getStageNumber();
        return cardStageNumber == null || cardStageNumber <= activeStageNumber;
    }

    private SessionProblemStatus toProblemStatus(KanbanCardStatus status) {
        return switch (status) {
            case DONE -> SessionProblemStatus.RESOLVED;
            case REGISTERED, ASSIGNED, READY_FOR_WORK, REWORK -> SessionProblemStatus.ACTIVE;
            case IN_PROGRESS, DEPARTMENT_REVIEW, CHIEF_DOCTOR_REVIEW -> SessionProblemStatus.IN_PROGRESS;
        };
    }
}
