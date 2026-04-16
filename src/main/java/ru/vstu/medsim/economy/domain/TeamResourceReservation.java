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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import ru.vstu.medsim.kanban.domain.KanbanSolutionOption;
import ru.vstu.medsim.kanban.domain.TeamKanbanCard;
import ru.vstu.medsim.player.domain.SessionParticipant;
import ru.vstu.medsim.session.domain.SessionTeam;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "team_resource_reservations")
public class TeamResourceReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private SessionTeam team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kanban_card_id", nullable = false)
    private TeamKanbanCard kanbanCard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solution_option_id", nullable = false)
    private KanbanSolutionOption solutionOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_participant_id")
    private SessionParticipant actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ResourceReservationStatus status;

    @Column(name = "budget_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "time_units", nullable = false)
    private Integer timeUnits;

    @Column(name = "item_name", length = 200)
    private String itemName;

    @Column(name = "item_quantity", nullable = false)
    private Integer itemQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    protected TeamResourceReservation() {
    }

    public TeamResourceReservation(
            SessionTeam team,
            TeamKanbanCard kanbanCard,
            KanbanSolutionOption solutionOption,
            SessionParticipant actor
    ) {
        this.team = team;
        this.kanbanCard = kanbanCard;
        this.solutionOption = solutionOption;
        this.actor = actor;
        this.status = ResourceReservationStatus.RESERVED;
        this.budgetAmount = solutionOption.getBudgetCost();
        this.timeUnits = solutionOption.getTimeCost();
        this.itemName = solutionOption.getRequiredItemName();
        this.itemQuantity = solutionOption.getRequiredItemQuantity() != null
                ? solutionOption.getRequiredItemQuantity()
                : 0;
    }

    public void commit() {
        this.status = ResourceReservationStatus.COMMITTED;
        this.committedAt = LocalDateTime.now();
    }

    public void release() {
        this.status = ResourceReservationStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
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
        if (itemQuantity == null) {
            itemQuantity = 0;
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

    public TeamKanbanCard getKanbanCard() {
        return kanbanCard;
    }

    public KanbanSolutionOption getSolutionOption() {
        return solutionOption;
    }

    public ResourceReservationStatus getStatus() {
        return status;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public Integer getTimeUnits() {
        return timeUnits;
    }

    public String getItemName() {
        return itemName;
    }

    public Integer getItemQuantity() {
        return itemQuantity;
    }
}
