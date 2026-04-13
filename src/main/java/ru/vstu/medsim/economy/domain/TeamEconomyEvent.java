package ru.vstu.medsim.economy.domain;

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
import ru.vstu.medsim.kanban.domain.TeamKanbanCard;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.session.domain.SessionTeam;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "team_economy_events")
public class TeamEconomyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private SessionTeam team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_participant_id")
    private SessionParticipant actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kanban_card_id")
    private TeamKanbanCard kanbanCard;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private TeamEconomyEventType eventType;

    @Column(name = "stage_number")
    private Integer stageNumber;

    @Column(name = "amount_delta", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountDelta;

    @Column(name = "time_delta", nullable = false)
    private Integer timeDelta;

    @Column(name = "item_name", length = 200)
    private String itemName;

    @Column(name = "item_quantity_delta", nullable = false)
    private Integer itemQuantityDelta;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TeamEconomyEvent() {
    }

    public TeamEconomyEvent(
            SessionTeam team,
            SessionParticipant actor,
            TeamKanbanCard kanbanCard,
            TeamEconomyEventType eventType,
            Integer stageNumber,
            BigDecimal amountDelta,
            Integer timeDelta,
            String itemName,
            Integer itemQuantityDelta,
            String message
    ) {
        this.team = team;
        this.actor = actor;
        this.kanbanCard = kanbanCard;
        this.eventType = eventType;
        this.stageNumber = stageNumber;
        this.amountDelta = amountDelta;
        this.timeDelta = timeDelta;
        this.itemName = itemName;
        this.itemQuantityDelta = itemQuantityDelta;
        this.message = message;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (amountDelta == null) {
            amountDelta = BigDecimal.ZERO.setScale(2);
        }
        if (timeDelta == null) {
            timeDelta = 0;
        }
        if (itemQuantityDelta == null) {
            itemQuantityDelta = 0;
        }
    }

    public Long getId() {
        return id;
    }

    public SessionTeam getTeam() {
        return team;
    }

    public SessionParticipant getActor() {
        return actor;
    }

    public TeamKanbanCard getKanbanCard() {
        return kanbanCard;
    }

    public TeamEconomyEventType getEventType() {
        return eventType;
    }

    public Integer getStageNumber() {
        return stageNumber;
    }

    public BigDecimal getAmountDelta() {
        return amountDelta;
    }

    public Integer getTimeDelta() {
        return timeDelta;
    }

    public String getItemName() {
        return itemName;
    }

    public Integer getItemQuantityDelta() {
        return itemQuantityDelta;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
