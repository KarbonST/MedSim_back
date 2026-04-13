package ru.vstu.medsim.kanban.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import ru.vstu.medsim.economy.domain.TeamProblemState;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.session.domain.SessionTeam;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_kanban_cards")
public class TeamKanbanCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private SessionTeam team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_state_id", nullable = false)
    private TeamProblemState problemState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_participant_id")
    private SessionParticipant assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private KanbanCardStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private KanbanCardPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsible_department", length = 40)
    private KanbanResponsibleDepartment responsibleDepartment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "resources_spent_at")
    private LocalDateTime resourcesSpentAt;

    protected TeamKanbanCard() {
    }

    public TeamKanbanCard(SessionTeam team, TeamProblemState problemState) {
        this.team = team;
        this.problemState = problemState;
        this.status = KanbanCardStatus.REGISTERED;
    }

    public void updateStatus(KanbanCardStatus status) {
        this.status = status;
        this.completedAt = status == KanbanCardStatus.DONE
                ? LocalDateTime.now()
                : null;
    }

    public void triage(KanbanCardPriority priority, KanbanResponsibleDepartment responsibleDepartment) {
        this.priority = priority;
        this.responsibleDepartment = responsibleDepartment;
        this.assignee = null;
        updateStatus(KanbanCardStatus.ASSIGNED);
    }

    public void assignTo(SessionParticipant assignee) {
        this.assignee = assignee;
        updateStatus(KanbanCardStatus.READY_FOR_WORK);
    }

    public void startWork() {
        updateStatus(KanbanCardStatus.IN_PROGRESS);
    }

    public void markResourcesSpent() {
        if (resourcesSpentAt == null) {
            resourcesSpentAt = LocalDateTime.now();
        }
    }

    public void returnToStageBacklog() {
        this.priority = null;
        this.responsibleDepartment = null;
        this.assignee = null;
        updateStatus(KanbanCardStatus.REGISTERED);
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public SessionTeam getTeam() {
        return team;
    }

    public TeamProblemState getProblemState() {
        return problemState;
    }

    public SessionParticipant getAssignee() {
        return assignee;
    }

    public KanbanCardStatus getStatus() {
        return status;
    }

    public KanbanCardPriority getPriority() {
        return priority;
    }

    public KanbanResponsibleDepartment getResponsibleDepartment() {
        return responsibleDepartment;
    }

    public LocalDateTime getResourcesSpentAt() {
        return resourcesSpentAt;
    }
}
