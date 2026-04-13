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
import jakarta.persistence.Table;
import ru.vstu.medsim.player.domain.SessionParticipant;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_kanban_card_events")
public class TeamKanbanCardEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private TeamKanbanCard card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_participant_id")
    private SessionParticipant actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_participant_id")
    private SessionParticipant targetParticipant;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private KanbanCardEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 40)
    private KanbanCardStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 40)
    private KanbanCardStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private KanbanCardPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsible_department", length = 40)
    private KanbanResponsibleDepartment responsibleDepartment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TeamKanbanCardEvent() {
    }

    public TeamKanbanCardEvent(
            TeamKanbanCard card,
            SessionParticipant actor,
            SessionParticipant targetParticipant,
            KanbanCardEventType eventType,
            KanbanCardStatus fromStatus,
            KanbanCardStatus toStatus,
            KanbanCardPriority priority,
            KanbanResponsibleDepartment responsibleDepartment
    ) {
        this.card = card;
        this.actor = actor;
        this.targetParticipant = targetParticipant;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.priority = priority;
        this.responsibleDepartment = responsibleDepartment;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public TeamKanbanCard getCard() {
        return card;
    }

    public SessionParticipant getActor() {
        return actor;
    }

    public SessionParticipant getTargetParticipant() {
        return targetParticipant;
    }

    public KanbanCardEventType getEventType() {
        return eventType;
    }

    public KanbanCardStatus getFromStatus() {
        return fromStatus;
    }

    public KanbanCardStatus getToStatus() {
        return toStatus;
    }

    public KanbanCardPriority getPriority() {
        return priority;
    }

    public KanbanResponsibleDepartment getResponsibleDepartment() {
        return responsibleDepartment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
